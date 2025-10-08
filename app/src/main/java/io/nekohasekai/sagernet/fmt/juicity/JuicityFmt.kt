package io.nekohasekai.sagernet.fmt.juicity

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.linkBuilder
import io.nekohasekai.sagernet.ktx.toLink
import io.nekohasekai.sagernet.ktx.urlSafe
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.SingBoxOptions.Outbound_JuicityOptions
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun parseJuicity(url: String): JuicityBean {
    val link = url.replace("juicity://", "https://").toHttpUrlOrNull() ?: error(
        "invalid juicity link $url"
    )
    return JuicityBean().apply {
        name = link.fragment
        uuid = link.username
        password = link.password
        serverAddress = link.host
        serverPort = link.port

        link.queryParameter("sni")?.let {
            sni = it
        }
        link.queryParameter("pinned_certchain_sha256")?.let {
            pinnedCertchainSha256 = it
        }
        link.queryParameter("allow_insecure")?.let {
            if (it == "1" || it == "true") allowInsecure = true
        }
    }
}

fun JuicityBean.toUri(): String {
    val builder = linkBuilder().username(uuid).password(password).host(serverAddress).port(serverPort)

    if (sni.isNotBlank()) {
        builder.addQueryParameter("sni", sni)
    }
    if (pinnedCertchainSha256.isNotBlank()) {
        builder.addQueryParameter("pinned_certchain_sha256", pinnedCertchainSha256)
    }
    if (allowInsecure) {
        builder.addQueryParameter("allow_insecure", "1")
    }
    if (name.isNotBlank()) {
        builder.encodedFragment(name.urlSafe())
    }

    return builder.toLink("juicity")
}

fun buildSingBoxOutboundJuicityBean(bean: JuicityBean): Outbound_JuicityOptions {
    return Outbound_JuicityOptions().apply {
        type = "juicity"
        server = bean.serverAddress
        server_port = bean.serverPort
        uuid = bean.uuid
        password = bean.password

        // Create TLS options object
        tls = SingBoxOptions.OutboundTLSOptions().apply {
            enabled = true
            if (bean.sni.isNotBlank()) {
                server_name = bean.sni
            }
            insecure = bean.allowInsecure || DataStore.globalAllowInsecure || bean.pinnedCertchainSha256.isNotBlank()
        }

        if (bean.pinnedCertchainSha256.isNotBlank()) {
            pin_cert_sha256 = bean.pinnedCertchainSha256
        }
    }
}
