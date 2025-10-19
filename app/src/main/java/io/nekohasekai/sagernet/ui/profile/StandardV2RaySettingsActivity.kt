package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import moe.matsuri.nb4a.proxy.PreferenceBinding
import moe.matsuri.nb4a.proxy.PreferenceBindingManager
import moe.matsuri.nb4a.proxy.Type
import moe.matsuri.nb4a.ui.SimpleMenuPreference

abstract class StandardV2RaySettingsActivity : ProfileSettingsActivity<StandardV2RayBean>() {

    var tmpBean: StandardV2RayBean? = null

    private val pbm = PreferenceBindingManager()
    private val name = pbm.add(PreferenceBinding(Type.Text, "name"))
    private val serverAddress = pbm.add(PreferenceBinding(Type.Text, "serverAddress"))
    private val serverPort = pbm.add(PreferenceBinding(Type.TextToInt, "serverPort"))
    private val uuid = pbm.add(PreferenceBinding(Type.Text, "uuid"))
    private val username = pbm.add(PreferenceBinding(Type.Text, "username"))
    private val password = pbm.add(PreferenceBinding(Type.Text, "password"))
    private val alterId = pbm.add(PreferenceBinding(Type.TextToInt, "alterId"))
    private val encryption = pbm.add(PreferenceBinding(Type.Text, "encryption"))
    private val type = pbm.add(PreferenceBinding(Type.Text, "type"))
    private val host = pbm.add(PreferenceBinding(Type.Text, "host"))
    private val path = pbm.add(PreferenceBinding(Type.Text, "path"))
    private val packetEncoding = pbm.add(PreferenceBinding(Type.TextToInt, "packetEncoding"))
    private val wsMaxEarlyData = pbm.add(PreferenceBinding(Type.TextToInt, "wsMaxEarlyData"))
    private val earlyDataHeaderName = pbm.add(PreferenceBinding(Type.Text, "earlyDataHeaderName"))
    private val security = pbm.add(PreferenceBinding(Type.Text, "security"))
    private val sni = pbm.add(PreferenceBinding(Type.Text, "sni"))
    private val alpn = pbm.add(PreferenceBinding(Type.Text, "alpn"))
    private val certificates = pbm.add(PreferenceBinding(Type.Text, "certificates"))
    private val allowInsecure = pbm.add(PreferenceBinding(Type.Bool, "allowInsecure"))
    private val utlsFingerprint = pbm.add(PreferenceBinding(Type.Text, "utlsFingerprint"))
    private val realityPubKey = pbm.add(PreferenceBinding(Type.Text, "realityPubKey"))
    private val realityShortId = pbm.add(PreferenceBinding(Type.Text, "realityShortId"))

    private val enableECH = pbm.add(PreferenceBinding(Type.Bool, "enableECH"))
    private val echConfig = pbm.add(PreferenceBinding(Type.Text, "echConfig"))

    private val enableMux = pbm.add(PreferenceBinding(Type.Bool, "enableMux"))
    private val muxPadding = pbm.add(PreferenceBinding(Type.Bool, "muxPadding"))
    private val muxType = pbm.add(PreferenceBinding(Type.TextToInt, "muxType"))
    private val muxConcurrency = pbm.add(PreferenceBinding(Type.TextToInt, "muxConcurrency"))

    private val xhttpMode = pbm.add(PreferenceBinding(Type.Text, "xhttpMode"))
    private val xhttpExtra = pbm.add(PreferenceBinding(Type.Text, "xhttpExtra"))
    private val vlessEncryption = pbm.add(PreferenceBinding(Type.Text, "vlessEncryption"))

    override fun StandardV2RayBean.init() {
        if (this is TrojanBean) {
            this@StandardV2RaySettingsActivity.uuid.fieldName = "password"
            this@StandardV2RaySettingsActivity.password.disable = true
        }

        tmpBean = this // copy bean
        pbm.writeToCacheAll(this)
    }

    override fun StandardV2RayBean.serialize() {
        pbm.fromCacheAll(this)
    }

    private lateinit var securityCategory: PreferenceCategory
    private lateinit var tlsCamouflageCategory: PreferenceCategory
    private lateinit var wsCategory: PreferenceCategory
    private lateinit var xhttpCategory: PreferenceCategory
    private lateinit var echCategory: PreferenceCategory

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.standard_v2ray_preferences)
        pbm.setPreferenceFragment(this)
        securityCategory = findPreference(Key.SERVER_SECURITY_CATEGORY)!!
        tlsCamouflageCategory = findPreference(Key.SERVER_TLS_CAMOUFLAGE_CATEGORY)!!
        echCategory = findPreference(Key.SERVER_ECH_CATEORY)!!
        wsCategory = findPreference(Key.SERVER_WS_CATEGORY)!!
        xhttpCategory = findPreference("serverXhttpCategory")!!


        // vmess/vless/http/trojan
        val isHttp = tmpBean is HttpBean
        val isVmess = tmpBean is VMessBean && tmpBean?.isVLESS == false
        val isVless = tmpBean?.isVLESS == true

        serverPort.preference.apply {
            this as EditTextPreference
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }

        alterId.preference.apply {
            this as EditTextPreference
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }

        uuid.preference.summaryProvider = PasswordSummaryProvider

        type.preference.isVisible = !isHttp
        uuid.preference.isVisible = !isHttp
        packetEncoding.preference.isVisible = isVmess || isVless
        alterId.preference.isVisible = isVmess
        encryption.preference.isVisible = isVmess || isVless
        vlessEncryption.preference.isVisible = isVless
        username.preference.isVisible = isHttp
        password.preference.isVisible = isHttp

        if (tmpBean is TrojanBean) {
            uuid.preference.title = resources.getString(R.string.password)
        }

        encryption.preference.apply {
            this as SimpleMenuPreference
            if (isVless) {
                title = resources.getString(R.string.xtls_flow)
                setIcon(R.drawable.ic_baseline_stream_24)
                setEntries(R.array.xtls_flow_value)
                setEntryValues(R.array.xtls_flow_value)
            } else {
                setEntries(R.array.vmess_encryption_value)
                setEntryValues(R.array.vmess_encryption_value)
            }
        }

        // menu with listener

        type.preference.apply {
            updateView(type.readStringFromCache())
            this as SimpleMenuPreference
            setOnPreferenceChangeListener { _, newValue ->
                updateView(newValue as String)
                true
            }
        }

        security.preference.apply {
            updateTls(security.readStringFromCache())
            this as SimpleMenuPreference
            setOnPreferenceChangeListener { _, newValue ->
                updateTls(newValue as String)
                true
            }
        }
    }

    private fun updateView(network: String) {
        host.preference.isVisible = false
        path.preference.isVisible = false
        wsCategory.isVisible = false
        xhttpCategory.isVisible = false

        when (network) {
            "tcp" -> {
                host.preference.setTitle(R.string.http_host)
                path.preference.setTitle(R.string.http_path)
            }

            "http" -> {
                host.preference.setTitle(R.string.http_host)
                path.preference.setTitle(R.string.http_path)
                host.preference.isVisible = true
                path.preference.isVisible = true
            }

            "ws" -> {
                host.preference.setTitle(R.string.ws_host)
                path.preference.setTitle(R.string.ws_path)
                host.preference.isVisible = true
                path.preference.isVisible = true
                wsCategory.isVisible = true
            }

            "grpc" -> {
                path.preference.setTitle(R.string.grpc_service_name)
                path.preference.isVisible = true
            }

            "httpupgrade" -> {
                host.preference.setTitle(R.string.http_upgrade_host)
                path.preference.setTitle(R.string.http_upgrade_path)
                host.preference.isVisible = true
                path.preference.isVisible = true
            }

            "xhttp" -> {
                host.preference.setTitle(R.string.xhttp_host)
                path.preference.setTitle(R.string.xhttp_path)
                host.preference.isVisible = true
                path.preference.isVisible = true
                xhttpCategory.isVisible = true
            }
        }
    }

    private fun updateTls(tls: String) {
        val isTLS = "tls" in tls
        securityCategory.isVisible = isTLS
        tlsCamouflageCategory.isVisible = isTLS
        echCategory.isVisible = isTLS
    }

}
