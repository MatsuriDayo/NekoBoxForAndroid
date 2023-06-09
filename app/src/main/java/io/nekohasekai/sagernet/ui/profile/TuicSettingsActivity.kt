package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.tuic.TuicBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import moe.matsuri.nb4a.ui.EditConfigPreference
import moe.matsuri.nb4a.ui.SimpleMenuPreference

class TuicSettingsActivity : ProfileSettingsActivity<TuicBean>() {

    override fun createEntity() = TuicBean().applyDefaultValues()

    override fun TuicBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverPassword = token
        DataStore.serverALPN = alpn
        DataStore.serverCertificates = caText
        DataStore.serverUDPRelayMode = udpRelayMode
        DataStore.serverCongestionController = congestionController
        DataStore.serverDisableSNI = disableSNI
        DataStore.serverSNI = sni
        DataStore.serverReduceRTT = reduceRTT
        DataStore.serverMTU = mtu
        //
        DataStore.serverFastConnect = fastConnect
        DataStore.serverAllowInsecure = allowInsecure
        //
        DataStore.serverConfig = customJSON
        DataStore.serverProtocolVersion = protocolVersion
        DataStore.serverUsername = uuid
    }

    override fun TuicBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        token = DataStore.serverPassword
        alpn = DataStore.serverALPN
        caText = DataStore.serverCertificates
        udpRelayMode = DataStore.serverUDPRelayMode
        congestionController = DataStore.serverCongestionController
        disableSNI = DataStore.serverDisableSNI
        sni = DataStore.serverSNI
        reduceRTT = DataStore.serverReduceRTT
        mtu = DataStore.serverMTU
        //
        fastConnect = DataStore.serverFastConnect
        allowInsecure = DataStore.serverAllowInsecure
        //
        customJSON = DataStore.serverConfig
        protocolVersion = DataStore.serverProtocolVersion
        uuid = DataStore.serverUsername
    }

    private lateinit var editConfigPreference: EditConfigPreference

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.tuic_preferences)

        editConfigPreference = findPreference(Key.SERVER_CONFIG)!!

        val uuid = findPreference<EditTextPreference>(Key.SERVER_USERNAME)!!
        val mtu = findPreference<EditTextPreference>(Key.SERVER_MTU)!!
        val fastConnect = findPreference<SwitchPreference>(Key.SERVER_FAST_CONNECT)!!
        fun updateVersion(v: Int) {
            if (v == 5) {
                uuid.isVisible = true
                mtu.isVisible = false
                fastConnect.isVisible = false
            } else {
                uuid.isVisible = false
                mtu.isVisible = true
                fastConnect.isVisible = true
            }
        }
        findPreference<SimpleMenuPreference>(Key.SERVER_PROTOCOL)!!.setOnPreferenceChangeListener { _, newValue ->
            updateVersion(newValue.toString().toIntOrNull() ?: 4)
            true
        }
        updateVersion(DataStore.serverProtocolVersion)

        val disableSNI = findPreference<SwitchPreference>(Key.SERVER_DISABLE_SNI)!!
        val sni = findPreference<EditTextPreference>(Key.SERVER_SNI)!!
        sni.isEnabled = !disableSNI.isChecked
        disableSNI.setOnPreferenceChangeListener { _, newValue ->
            sni.isEnabled = !(newValue as Boolean)
            true
        }

        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
    }

    override fun onResume() {
        super.onResume()

        if (::editConfigPreference.isInitialized) {
            editConfigPreference.notifyChanged()
        }
    }

}