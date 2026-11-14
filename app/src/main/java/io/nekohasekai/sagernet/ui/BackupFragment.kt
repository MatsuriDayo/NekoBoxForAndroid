package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.provider.OpenableColumns
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jakewharton.processphoenix.ProcessPhoenix
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.bg.Executable
import io.nekohasekai.sagernet.database.*
import io.nekohasekai.sagernet.database.preference.KeyValuePair
import io.nekohasekai.sagernet.database.preference.PublicDatabase
import io.nekohasekai.sagernet.databinding.LayoutBackupBinding
import io.nekohasekai.sagernet.databinding.LayoutImportBinding
import io.nekohasekai.sagernet.databinding.LayoutProgressBinding
import io.nekohasekai.sagernet.ktx.*
import kotlinx.coroutines.delay
import moe.matsuri.nb4a.utils.Util
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.*

class BackupFragment : NamedFragment(R.layout.layout_backup) {

    override fun name0() = app.getString(R.string.backup)

    var content = ""
    private val exportSettings =
        registerForActivityResult(ActivityResultContracts.CreateDocument()) { data ->
            if (data != null) {
                runOnDefaultDispatcher {
                    try {
                        requireActivity().contentResolver.openOutputStream(
                            data
                        )!!.bufferedWriter().use {
                            it.write(content)
                        }
                        onMainDispatcher {
                            snackbar(getString(R.string.action_export_msg)).show()
                        }
                    } catch (e: Exception) {
                        Logs.w(e)
                        onMainDispatcher {
                            snackbar(e.readableMessage).show()
                        }
                    }
                }
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = LayoutBackupBinding.bind(view)

        binding.resetSettings.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.confirm)
                .setMessage(R.string.reset_settings_message)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes) { _, _ ->
                    DataStore.configurationStore.reset()
                    triggerFullRestart(requireContext())
                }
                .show()
        }

        binding.actionExport.setOnClickListener {
            runOnDefaultDispatcher {
                content = doBackup(
                    binding.backupConfigurations.isChecked,
                    binding.backupRules.isChecked,
                    binding.backupSettings.isChecked
                )
                onMainDispatcher {
                    startFilesForResult(
                        exportSettings, "nekobox_backup_${Date().toLocaleString()}.json"
                    )
                }
            }
        }

        binding.actionShare.setOnClickListener {
            runOnDefaultDispatcher {
                content = doBackup(
                    binding.backupConfigurations.isChecked,
                    binding.backupRules.isChecked,
                    binding.backupSettings.isChecked
                )
                app.cacheDir.mkdirs()
                val cacheFile = File(
                    app.cacheDir, "nekobox_backup_${Date().toLocaleString()}.json"
                )
                cacheFile.writeText(content)
                onMainDispatcher {
                    startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("application/json")
                                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                .putExtra(
                                    Intent.EXTRA_STREAM, FileProvider.getUriForFile(
                                        app, BuildConfig.APPLICATION_ID + ".cache", cacheFile
                                    )
                                ), app.getString(R.string.abc_shareactionprovider_share_with)
                        )
                    )
                }

            }
        }

        binding.actionImportFile.setOnClickListener {
            startFilesForResult(importFile, "*/*")
        }
    }

    fun Parcelable.toBase64Str(): String {
        val parcel = Parcel.obtain()
        writeToParcel(parcel, 0)
        try {
            return Util.b64EncodeUrlSafe(parcel.marshall())
        } finally {
            parcel.recycle()
        }
    }

    fun doBackup(profile: Boolean, rule: Boolean, setting: Boolean): String {
        val out = JSONObject().apply {
            put("version", 1)
            if (profile) {
                put("profiles", JSONArray().apply {
                    SagerDatabase.proxyDao.getAll().forEach {
                        put(it.toBase64Str())
                    }
                })

                put("groups", JSONArray().apply {
                    SagerDatabase.groupDao.allGroups().forEach {
                        put(it.toBase64Str())
                    }
                })
            }
            if (rule) {
                put("remoteRuleSets", RemoteRuleSetStore.export(SagerDatabase.remoteRuleSetsDao.all()))
                put("remoteRuleSetReferences", JSONObject().apply {
                    SagerDatabase.rulesDao.allRules().forEach {
                        if (it.remoteRuleSetTags.isNotBlank()) put(it.id.toString(), JSONArray(RemoteRuleSetStore.tags(it.remoteRuleSetTags)))
                    }
                })
                put("rules", JSONArray().apply {
                    SagerDatabase.rulesDao.allRules().forEach {
                        put(it.toBase64Str())
                    }
                })
            }
            if (setting) {
                put("settings", JSONArray().apply {
                    PublicDatabase.kvPairDao.all().forEach {
                        put(it.toBase64Str())
                    }
                })
            }
        }
        return out.toStringPretty()
    }

    val importFile = registerForActivityResult(ActivityResultContracts.GetContent()) { file ->
        if (file != null) {
            runOnDefaultDispatcher {
                startImport(file)
            }
        }
    }

    suspend fun startImport(file: Uri) {
        val fileName = requireContext().contentResolver.query(file, null, null, null, null)
            ?.use { cursor ->
                cursor.moveToFirst()
                cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME).let(cursor::getString)
            }
            ?.takeIf { it.isNotBlank() } ?: file.pathSegments.last()
            .substringAfterLast('/')
            .substringAfter(':')

        if (!fileName.endsWith(".json")) {
            onMainDispatcher {
                snackbar(getString(R.string.backup_not_file, fileName)).show()
            }
            return
        }

        suspend fun invalid() = onMainDispatcher {
            onMainDispatcher {
                snackbar(getString(R.string.invalid_backup_file)).show()
            }
        }

        val content = try {
            JSONObject((requireContext().contentResolver.openInputStream(file) ?: return).use {
                it.bufferedReader().readText()
            })
        } catch (e: Exception) {
            // JSON parser errors can embed the backup, including subscription URL tokens.
            Logs.w("Invalid backup JSON")
            invalid()
            return
        }
        val version = content.optInt("version", 0)
        if (version < 1 || version > 1) {
            invalid()
            return
        }

        onMainDispatcher {
            val import = LayoutImportBinding.inflate(layoutInflater)
            if (!content.has("profiles")) {
                import.backupConfigurations.isVisible = false
            }
            if (!content.has("rules")) {
                import.backupRules.isVisible = false
            }
            if (!content.has("settings")) {
                import.backupSettings.isVisible = false
            }
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.backup_import)
                .setView(import.root)
                .setPositiveButton(R.string.backup_import) { _, _ ->
                    SagerNet.stopService()

                    val binding = LayoutProgressBinding.inflate(layoutInflater)
                    binding.content.text = getString(R.string.backup_importing)
                    val dialog = AlertDialog.Builder(requireContext())
                        .setView(binding.root)
                        .setCancelable(false)
                        .show()
                    runOnDefaultDispatcher {
                        runCatching {
                            finishImport(
                                content,
                                import.backupConfigurations.isChecked,
                                import.backupRules.isChecked,
                                import.backupSettings.isChecked
                            )
                            triggerFullRestart(requireContext())
                        }.onFailure {
                            Logs.w(it)
                            onMainDispatcher {
                                alert(it.readableMessage).tryToShow()
                            }
                        }

                        onMainDispatcher {
                            dialog.dismiss()
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    fun finishImport(
        content: JSONObject, profile: Boolean, rule: Boolean, setting: Boolean
    ) {
        val (remoteSets, parsedReferences) = try {
            val sets = if (rule && content.has("rules")) {
                RemoteRuleSetStore.parse(if (content.has("remoteRuleSets")) content.getJSONArray("remoteRuleSets") else null)
            } else emptyList()
            val references = if (rule && content.has("remoteRuleSetReferences")) content.getJSONObject("remoteRuleSetReferences") else JSONObject()
            val parsed = references.keys().asSequence().associateWith { key ->
                require(key.toLongOrNull() != null)
                val array = references.getJSONArray(key)
                (0 until array.length()).map { array.getString(it) }.also { tags ->
                    require(tags.all { tag -> sets.any { it.tag == tag } })
                }.joinToString("\n")
            }
            sets to parsed
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid remote rule-set backup data")
        }
        fun <T> parse(name: String, create: (Parcel) -> T): List<T> {
            val values = content.getJSONArray(name)
            return (0 until values.length()).map { index ->
                val data = Util.b64Decode(values.getString(index))
                val parcel = Parcel.obtain()
                try {
                    parcel.unmarshall(data, 0, data.size)
                    parcel.setDataPosition(0)
                    create(parcel)
                } finally {
                    parcel.recycle()
                }
            }
        }

        // Parse every selected section before changing either database or any cache.
        val profiles = if (profile && content.has("profiles")) parse("profiles", ProxyEntity.CREATOR::createFromParcel) else null
        val groups = if (profiles != null) parse("groups", ProxyGroup.CREATOR::createFromParcel) else null
        val rules = if (rule && content.has("rules")) parse("rules") { parcel ->
            ParcelizeBridge.createRule(parcel).apply { remoteRuleSetTags = parsedReferences[id.toString()] ?: "" }
        } else null
        val settings = if (setting && content.has("settings")) parse("settings", KeyValuePair.CREATOR::createFromParcel) else null
        if (rules != null) {
            require(parsedReferences.keys.all { id -> rules.any { it.id.toString() == id } }) { "Missing referenced route" }
            RemoteRuleSetStore.validateReferences(remoteSets, rules)
        }

        val database = SagerDatabase.instance
        val sqlite = database.openHelper.writableDatabase
        // Both Room databases use rollback journals. ATTACH makes their replacement one
        // SQLite transaction, including a rollback if inserting settings fails.
        if (settings != null) {
            val settingsPath = PublicDatabase.instance.openHelper.writableDatabase.path
            sqlite.execSQL("ATTACH DATABASE ? AS imported_settings", arrayOf(settingsPath))
        }
        try {
            val replace: () -> Unit = {
                if (profiles != null) {
                    SagerDatabase.proxyDao.reset()
                    SagerDatabase.proxyDao.insert(profiles)
                    SagerDatabase.groupDao.reset()
                    SagerDatabase.groupDao.insert(groups!!)
                }
                if (rules != null) {
                    SagerDatabase.rulesDao.reset()
                    SagerDatabase.rulesDao.insert(rules)
                }
                if (settings != null) {
                    sqlite.execSQL("DELETE FROM imported_settings.KeyValuePair")
                    settings.forEach { value ->
                        sqlite.execSQL(
                            "INSERT INTO imported_settings.KeyValuePair (`key`, valueType, value) VALUES (?, ?, ?)",
                            arrayOf(value.key, value.valueType, value.value)
                        )
                    }
                }
            }
            if (rules != null) RemoteRuleSetManager.restore(remoteSets, replace)
            else database.runInTransaction { replace() }
        } finally {
            if (settings != null) sqlite.execSQL("DETACH DATABASE imported_settings")
        }
    }

}
