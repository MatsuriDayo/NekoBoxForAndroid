package io.nekohasekai.sagernet.database

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.system.Os
import androidx.work.*
import androidx.work.multiprocess.RemoteWorkManager
import io.nekohasekai.sagernet.SagerNet
import libcore.Libcore
import moe.matsuri.nb4a.SingBoxOptions.RuleSet
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

object RemoteRuleSetManager {
    const val DOWNLOAD_CLIENT = "ruleset-fetch-direct"
    private val root get() = File(SagerNet.application.filesDir, "remote-rule-sets")
    private val dao get() = SagerDatabase.remoteRuleSetsDao
    private fun cache(e: RemoteRuleSetEntity) = File(root, "${e.id}.${e.format}")
    private val errors = ConcurrentHashMap<Long, String>()
    private val downloadLock = Any()
    fun updated(e: RemoteRuleSetEntity): Long = cache(e).lastModified()
    fun error(id: Long): String {
        errors[id]?.let { return it }
        val info = try {
            RemoteWorkManager.getInstance(SagerNet.application)
                .getWorkInfos(WorkQuery.fromUniqueWorkNames(listOf("rule-set-$id"))).get(10, TimeUnit.SECONDS)
                .maxByOrNull { it.outputData.getLong("completed", 0) }
        } catch (_: Exception) { return "Background update status unavailable" } ?: return ""
        val e = dao.get(id) ?: return ""
        return if (info.outputData.getLong("completed", 0) > updated(e)) info.outputData.getString("error") ?: "" else ""
    }

    // Main UI and VPN processes share the same files. Serialize updates, edits and deletion.
    @Synchronized
    private fun <T> locked(block: () -> T): T {
        check(root.isDirectory || root.mkdirs())
        return RandomAccessFile(File(root, ".lock"), "rw").use { file ->
            file.channel.lock().use { block() }
        }
    }

    fun save(value: RemoteRuleSetEntity): Long = locked {
        RemoteRuleSetStore.validate(value)
        val old = if (value.id == 0L) null else dao.get(value.id) ?: error("Rule-set no longer exists")
        require(dao.all().none { it.id != value.id && it.tag == value.tag }) { "Duplicate rule-set tag" }
        if (old != null && old.tag != value.tag) ensureUnreferenced(old.tag)
        SagerDatabase.instance.runInTransaction {
            if (old == null) value.id = dao.insert(value) else dao.update(value)
        }
        errors.remove(value.id)
        schedule(value, ExistingWorkPolicy.REPLACE, 0)
        value.id
    }

    private fun ensureUnreferenced(tag: String) {
        require(SagerDatabase.rulesDao.allRules().none { tag in RemoteRuleSetStore.tags(it.remoteRuleSetTags) }) {
            "Rule-set is referenced; remove route references first"
        }
    }

    fun delete(id: Long) = locked {
        val entity = dao.get(id) ?: return@locked
        ensureUnreferenced(entity.tag)
        dao.delete(id)
        RemoteWorkManager.getInstance(SagerNet.application).cancelUniqueWork("rule-set-$id")
        listOf("source", "binary").forEach { format ->
            val file = File(root, "$id.$format")
            check(!file.exists() || file.delete()) { "Cannot remove rule-set cache" }
        }
        errors.remove(id)
    }

    private fun schedule(e: RemoteRuleSetEntity, policy: ExistingWorkPolicy, delay: Long) {
        val manager = RemoteWorkManager.getInstance(SagerNet.application)
        if (!e.enabled) {
            manager.cancelUniqueWork("rule-set-${e.id}")
            return
        }
        val request = OneTimeWorkRequestBuilder<RemoteRuleSetWorker>()
            .setInputData(workDataOf("id" to e.id))
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        manager.enqueueUniqueWork("rule-set-${e.id}", policy, request).get(10, TimeUnit.SECONDS)
    }

    fun update(id: Long) = synchronized(downloadLock) {
        check(root.isDirectory || root.mkdirs())
        RandomAccessFile(File(root, ".download.lock"), "rw").use { file ->
            file.channel.lock().use {
                root.listFiles { entry -> entry.name.startsWith("candidate-") && entry.name.endsWith(".tmp") }
                    ?.forEach { it.delete() }
                performUpdate(id)
            }
        }
    }

    private fun performUpdate(id: Long) {
        val e = locked { dao.get(id) ?: error("Rule-set no longer exists") }
        try {
            download(e)
            errors.remove(id)
        } catch (exception: Exception) {
            // Only controlled messages are stored/displayed; HTTP URLs and Core parse errors may contain secrets.
            val message = if (exception is DownloadFailure) exception.message!! else "Rule-set update failed"
            errors[id] = message
            throw DownloadFailure(message)
        }
    }

    fun backgroundUpdate(id: Long, active: () -> Boolean): String {
        if (!active() || dao.get(id)?.enabled != true) return ""
        try { update(id) } catch (_: Exception) { /* The manager keeps a visible, sanitized error. */ }
        locked { if (active()) dao.get(id)?.let { schedule(it, ExistingWorkPolicy.APPEND_OR_REPLACE, it.updateIntervalMinutes) } }
        return errors[id] ?: ""
    }

    private fun download(e: RemoteRuleSetEntity) {
        val cm = SagerNet.application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = (listOfNotNull(SagerNet.underlyingNetwork) + cm.allNetworks).distinct().firstOrNull {
            cm.getNetworkCapabilities(it)?.let { caps ->
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            } == true
        } ?: throw DownloadFailure("No direct network available")
        val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY)
            .socketFactory(network.socketFactory).dns { network.getAllByName(it).toList() }
            .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS).followSslRedirects(false).build()
        try {
            RemoteRuleSetDownload.fetch(client, e.url, cache(e), { candidate -> validateContent(e, candidate) }) { from, to ->
                locked {
                    val current = dao.get(e.id)
                    require(current != null && current.url == e.url && current.format == e.format) { "Subscription changed during update" }
                    Os.rename(from.absolutePath, to.absolutePath)
                }
            }
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    private fun validateContent(e: RemoteRuleSetEntity, candidate: File) {
        val config = JSONObject().put("log", JSONObject().put("disabled", true))
            .put("route", JSONObject().put("rule_set", JSONArray().put(JSONObject()
                .put("type", "local").put("tag", e.tag).put("format", e.format).put("path", candidate.absolutePath))))
        try {
            Libcore.newSingBoxInstance(config.toString(), null).close()
        } catch (_: Exception) {
            throw DownloadFailure("Invalid rule-set content")
        }
    }

    fun config(rules: List<RuleEntity>): List<RuleSet> = locked {
        val entities = dao.all()
        RemoteRuleSetStore.validateReferences(entities, rules)
        entities.filter { it.enabled }.map { e ->
            require(cache(e).isFile) { "Rule-set ${e.tag} has no valid cache; update it first" }
            RuleSet().apply { type = "local"; tag = e.tag; format = e.format; path = cache(e).absolutePath }
        }
    }

    fun restore(entities: List<RemoteRuleSetEntity>) = locked {
        val previous = dao.all()
        SagerDatabase.instance.runInTransaction { dao.reset(); dao.insertAll(entities) }
        previous.forEach { e ->
            RemoteWorkManager.getInstance(SagerNet.application).cancelUniqueWork("rule-set-${e.id}")
            listOf("source", "binary").forEach { File(root, "${e.id}.$it").delete() }
            errors.remove(e.id)
        }
        entities.forEach { schedule(it, ExistingWorkPolicy.REPLACE, 0) }
    }

    class DownloadFailure(message: String) : Exception(message)
}

class RemoteRuleSetWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val error = RemoteRuleSetManager.backgroundUpdate(inputData.getLong("id", 0)) { !isStopped }
        return Result.success(workDataOf("error" to error, "completed" to System.currentTimeMillis()))
    }
}
