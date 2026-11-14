package io.nekohasekai.sagernet.database

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

/** Subscription data and cache operations, independent of Android networking. */
object RemoteRuleSetStore {
    fun tags(value: String): List<String> = value.lineSequence().filter { it.isNotBlank() }.distinct().toList()

    fun validate(entity: RemoteRuleSetEntity) {
        require(entity.id >= 0 && entity.name.isNotBlank()) { "Invalid rule-set name or ID" }
        require(entity.tag.matches(Regex("[A-Za-z0-9_.-]+"))) { "Invalid rule-set tag" }
        require(entity.format == "source" || entity.format == "binary") { "Invalid rule-set format" }
        require(entity.updateIntervalMinutes in 1..(Long.MAX_VALUE / 60000)) { "Invalid update interval" }
        val uri = try { URI(entity.url) } catch (_: Exception) { null }
        require(uri != null && uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank()
            && uri.userInfo == null && uri.fragment == null) { "Invalid rule-set URL" }
    }

    fun validateReferences(entities: List<RemoteRuleSetEntity>, rules: List<RuleEntity>) {
        entities.forEach(::validate)
        require(entities.map { it.tag }.distinct().size == entities.size) { "Duplicate rule-set tag" }
        val byTag = entities.associateBy { it.tag }
        rules.forEach { rule -> tags(rule.remoteRuleSetTags).forEach { tag ->
            val entity = byTag[tag] ?: error("Missing rule-set: $tag")
            require(!rule.enabled || entity.enabled) { "Enabled rule references disabled rule-set: $tag" }
        } }
    }

    /** Only subscriptions used by enabled routes are required to start the proxy. */
    fun required(entities: List<RemoteRuleSetEntity>, rules: List<RuleEntity>): List<RemoteRuleSetEntity> {
        val active = rules.filter { it.enabled }
        val referenced = active.flatMap { tags(it.remoteRuleSetTags) }.toSet()
        val required = entities.filter { it.tag in referenced }
        validateReferences(required, active)
        return required
    }

    fun export(entities: List<RemoteRuleSetEntity>): JSONArray = JSONArray().apply {
        entities.forEach { e -> put(JSONObject().apply {
            put("id", e.id); put("name", e.name); put("tag", e.tag); put("url", e.url)
            put("format", e.format); put("updateIntervalMinutes", e.updateIntervalMinutes); put("enabled", e.enabled)
        }) }
    }

    fun parse(array: JSONArray?): List<RemoteRuleSetEntity> {
        val result = (0 until (array?.length() ?: 0)).map { index ->
            val o = array!!.getJSONObject(index)
            RemoteRuleSetEntity(o.getLong("id"), o.getString("name"), o.getString("tag"),
                o.getString("url"), o.getString("format"), o.getLong("updateIntervalMinutes"), o.getBoolean("enabled"))
                .also { validate(it); require(it.id > 0) { "Invalid rule-set ID" } }
        }
        require(result.map { it.id }.distinct().size == result.size) { "Duplicate rule-set ID" }
        validateReferences(result, emptyList())
        return result
    }

    /** The old valid file stays in place until validation and a same-directory rename succeed. */
    fun replace(cache: File, input: InputStream, validate: (File) -> Unit, rename: (File, File) -> Unit) {
        check(cache.parentFile!!.isDirectory || cache.parentFile!!.mkdirs()) { "Cannot create rule-set cache" }
        val candidate = File.createTempFile("candidate-", ".tmp", cache.parentFile)
        try {
            FileOutputStream(candidate).use { out ->
                val buffer = ByteArray(32768)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > 64L * 1024 * 1024) throw IOException("Rule-set exceeds 64 MiB")
                    out.write(buffer, 0, count)
                }
                out.fd.sync()
            }
            validate(candidate)
            rename(candidate, cache)
        } finally {
            candidate.delete()
        }
    }
}
