package moe.matsuri.nb4a.proxy.neko

import androidx.preference.*
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.ktx.forEach
import io.nekohasekai.sagernet.ktx.getStr
import io.nekohasekai.sagernet.ui.profile.ProfileSettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.matsuri.nb4a.ui.SimpleMenuPreference
import moe.matsuri.nb4a.utils.getDrawableByName
import org.json.JSONArray
import org.json.JSONObject

object NekoPreferenceInflater {
    suspend fun inflate(pref: JSONArray, preferenceScreen: PreferenceScreen) =
        withContext(Dispatchers.Main) {
            val context = preferenceScreen.context
            pref.forEach { _, category ->
                category as JSONObject

                val preferenceCategory = PreferenceCategory(context)
                preferenceScreen.addPreference(preferenceCategory)

                category.getStr("key")?.apply { preferenceCategory.key = this }
                category.getStr("title")?.apply { preferenceCategory.title = this }

                category.optJSONArray("preferences")?.forEach { _, any ->
                    if (any is JSONObject) {
                        lateinit var p: Preference
                        // Create Preference
                        when (any.getStr("type")) {
                            "EditTextPreference" -> {
                                p = EditTextPreference(context).apply {
                                    when (any.getStr("summaryProvider")) {
                                        null -> summaryProvider =
                                            EditTextPreference.SimpleSummaryProvider.getInstance()
                                        "PasswordSummaryProvider" -> summaryProvider =
                                            ProfileSettingsActivity.PasswordSummaryProvider
                                    }
                                    when (any.getStr("EditTextPreferenceModifiers")) {
                                        "Monospace" -> setOnBindEditTextListener(
                                            EditTextPreferenceModifiers.Monospace
                                        )
                                        "Hosts" -> setOnBindEditTextListener(
                                            EditTextPreferenceModifiers.Hosts
                                        )
                                        "Port" -> setOnBindEditTextListener(
                                            EditTextPreferenceModifiers.Port
                                        )
                                        "Number" -> setOnBindEditTextListener(
                                            EditTextPreferenceModifiers.Number
                                        )
                                    }
                                }
                            }
                            "SwitchPreference" -> {
                                p = SwitchPreference(context)
                            }
                            "SimpleMenuPreference" -> {
                                p = SimpleMenuPreference(context).apply {
                                    val entries = any.optJSONObject("entries")
                                    if (entries != null) setMenu(this, entries)
                                }
                            }
                        }
                        // Set key & title
                        p.key = any.getString("key")
                        any.getStr("title")?.apply { p.title = this }
                        // Set icon
                        any.getStr("icon")?.apply {
                            p.icon = context.getDrawableByName(this)
                        }
                        // Set summary
                        any.getStr("summary")?.apply {
                            p.summary = this
                        }
                        // Add to category
                        preferenceCategory.addPreference(p)
                    }
                }
            }
        }

    fun setMenu(p: SimpleMenuPreference, entries: JSONObject) {
        val menuEntries = mutableListOf<String>()
        val menuEntryValues = mutableListOf<String>()
        entries.forEach { s, b ->
            menuEntryValues.add(s)
            menuEntries.add(b as String)
        }
        entries.apply {
            p.entries = menuEntries.toTypedArray()
            p.entryValues = menuEntryValues.toTypedArray()
        }
    }
}