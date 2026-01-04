package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import moe.matsuri.nb4a.proxy.PreferenceBinding
import moe.matsuri.nb4a.proxy.PreferenceBindingManager
import moe.matsuri.nb4a.proxy.Type

import moe.matsuri.nb4a.ui.SimpleMenuPreference

class ShadowsocksSettingsActivity : ProfileSettingsActivity<ShadowsocksBean>() {

    override fun createEntity() = ShadowsocksBean()

    private val pbm = PreferenceBindingManager()
    private val name = pbm.add(PreferenceBinding(Type.Text, "name"))
    private val serverAddress = pbm.add(PreferenceBinding(Type.Text, "serverAddress"))
    private val serverPort = pbm.add(PreferenceBinding(Type.TextToInt, "serverPort"))
    private val password = pbm.add(PreferenceBinding(Type.Text, "password"))
    private val method = pbm.add(PreferenceBinding(Type.Text, "method"))
    private val pluginName =
        pbm.add(PreferenceBinding(Type.Text, "pluginName").apply { disable = true })
    private val pluginConfig =
        pbm.add(PreferenceBinding(Type.Text, "pluginConfig").apply { disable = true })
    private val sUoT = pbm.add(PreferenceBinding(Type.Bool, "sUoT"))
    private val enableMux = pbm.add(PreferenceBinding(Type.Bool, "enableMux"))
    private val muxType = pbm.add(PreferenceBinding(Type.TextToInt, "muxType"))
    private val muxConcurrency = pbm.add(PreferenceBinding(Type.TextToInt, "muxConcurrency"))
    private val muxMode = pbm.add(PreferenceBinding(Type.TextToInt, "muxMode"))
    private val muxMaxConnections = pbm.add(PreferenceBinding(Type.TextToInt, "muxMaxConnections"))
    private val muxMinStreams = pbm.add(PreferenceBinding(Type.TextToInt, "muxMinStreams"))
    private val muxPadding = pbm.add(PreferenceBinding(Type.Bool, "muxPadding"))

    override fun ShadowsocksBean.init() {
        pbm.writeToCacheAll(this)

        DataStore.profileCacheStore.putString("pluginName", plugin.substringBefore(";"))
        DataStore.profileCacheStore.putString("pluginConfig", plugin.substringAfter(";"))
    }

    override fun ShadowsocksBean.serialize() {
        pbm.fromCacheAll(this)

        val pn = pluginName.readStringFromCache()
        val pc = pluginConfig.readStringFromCache()
        plugin = if (pn.isNotBlank()) "$pn;$pc" else ""
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.shadowsocks_preferences)
        pbm.setPreferenceFragment(this)

        serverPort.preference.apply {
            this as EditTextPreference
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        password.preference.apply {
            this as EditTextPreference
            summaryProvider = PasswordSummaryProvider
        }

        // Mux mode visibility control
        muxMode.preference.apply {
            updateMuxMode(muxMode.readIntFromCache())
            this as SimpleMenuPreference
            setOnPreferenceChangeListener { _, newValue ->
                updateMuxMode((newValue as String).toInt())
                true
            }
        }
    }

    private fun updateMuxMode(mode: Int) {
        // mode 0: max_streams mode - show muxConcurrency, hide muxMaxConnections/muxMinStreams
        // mode 1: connections mode - hide muxConcurrency, show muxMaxConnections/muxMinStreams
        val isMaxStreamsMode = mode == 0
        muxConcurrency.preference.isVisible = isMaxStreamsMode
        muxMaxConnections.preference.isVisible = !isMaxStreamsMode
        muxMinStreams.preference.isVisible = !isMaxStreamsMode
    }

}
