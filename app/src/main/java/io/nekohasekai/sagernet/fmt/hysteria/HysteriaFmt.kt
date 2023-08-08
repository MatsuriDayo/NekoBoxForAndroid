package io.nekohasekai.sagernet.fmt.hysteria

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.LOCALHOST
import io.nekohasekai.sagernet.ktx.*
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.listByLineOrComma
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.File


// hysteria://host:port?auth=123456&peer=sni.domain&insecure=1|0&upmbps=100&downmbps=100&alpn=hysteria&obfs=xplus&obfsParam=123456#remarks

fun parseHysteria(url: String): HysteriaBean {
    val link = url.replace("hysteria://", "https://").toHttpUrlOrNull() ?: error(
        "invalid hysteria link $url"
    )
    return HysteriaBean().apply {
        serverAddress = link.host
        serverPorts = link.port.toString()
        name = link.fragment

        link.queryParameter("mport")?.also {
            serverPorts = it
        }
        link.queryParameter("peer")?.also {
            sni = it
        }
        link.queryParameter("auth")?.takeIf { it.isNotBlank() }?.also {
            authPayloadType = HysteriaBean.TYPE_STRING
            authPayload = it
        }
        link.queryParameter("insecure")?.also {
            allowInsecure = it == "1"
        }
        link.queryParameter("upmbps")?.also {
            uploadMbps = it.toIntOrNull() ?: uploadMbps
        }
        link.queryParameter("downmbps")?.also {
            downloadMbps = it.toIntOrNull() ?: downloadMbps
        }
        link.queryParameter("alpn")?.also {
            alpn = it
        }
        link.queryParameter("obfsParam")?.also {
            obfuscation = it
        }
        link.queryParameter("protocol")?.also {
            when (it) {
                "faketcp" -> {
                    protocol = HysteriaBean.PROTOCOL_FAKETCP
                }

                "wechat-video" -> {
                    protocol = HysteriaBean.PROTOCOL_WECHAT_VIDEO
                }
            }
        }
    }
}

fun HysteriaBean.toUri(): String {
    val builder = linkBuilder()
        .host(serverAddress)
        .port(getFirstPort(serverPorts))
    if (isMultiPort(displayAddress())) {
        builder.addQueryParameter("mport", serverPorts)
    }
    if (allowInsecure) {
        builder.addQueryParameter("insecure", "1")
    }
    if (sni.isNotBlank()) {
        builder.addQueryParameter("peer", sni)
    }
    if (authPayload.isNotBlank()) {
        builder.addQueryParameter("auth", authPayload)
    }
    builder.addQueryParameter("upmbps", "$uploadMbps")
    builder.addQueryParameter("downmbps", "$downloadMbps")
    if (alpn.isNotBlank()) {
        builder.addQueryParameter("alpn", alpn)
    }
    if (obfuscation.isNotBlank()) {
        builder.addQueryParameter("obfs", "xplus")
        builder.addQueryParameter("obfsParam", obfuscation)
    }
    when (protocol) {
        HysteriaBean.PROTOCOL_FAKETCP -> {
            builder.addQueryParameter("protocol", "faketcp")
        }

        HysteriaBean.PROTOCOL_WECHAT_VIDEO -> {
            builder.addQueryParameter("protocol", "wechat-video")
        }
    }
    if (protocol == HysteriaBean.PROTOCOL_FAKETCP) {
        builder.addQueryParameter("protocol", "faketcp")
    }
    if (name.isNotBlank()) {
        builder.encodedFragment(name.urlSafe())
    }
    return builder.toLink("hysteria")
}

fun JSONObject.parseHysteria(): HysteriaBean {
    return HysteriaBean().apply {
        serverAddress = optString("server").substringBeforeLast(":")
        serverPorts = optString("server").substringAfterLast(":")
        uploadMbps = getIntNya("up_mbps")
        downloadMbps = getIntNya("down_mbps")
        obfuscation = getStr("obfs")
        getStr("auth")?.also {
            authPayloadType = HysteriaBean.TYPE_BASE64
            authPayload = it
        }
        getStr("auth_str")?.also {
            authPayloadType = HysteriaBean.TYPE_STRING
            authPayload = it
        }
        getStr("protocol")?.also {
            when (it) {
                "faketcp" -> {
                    protocol = HysteriaBean.PROTOCOL_FAKETCP
                }

                "wechat-video" -> {
                    protocol = HysteriaBean.PROTOCOL_WECHAT_VIDEO
                }
            }
        }
        sni = getStr("server_name")
        alpn = getStr("alpn")
        allowInsecure = getBool("insecure")

        streamReceiveWindow = getIntNya("recv_window_conn")
        connectionReceiveWindow = getIntNya("recv_window")
        disableMtuDiscovery = getBool("disable_mtu_discovery")
    }
}

fun HysteriaBean.buildHysteriaConfig(port: Int, cacheFile: (() -> File)?): String {
    return JSONObject().apply {
        put("server", displayAddress())
        when (protocol) {
            HysteriaBean.PROTOCOL_FAKETCP -> {
                put("protocol", "faketcp")
            }

            HysteriaBean.PROTOCOL_WECHAT_VIDEO -> {
                put("protocol", "wechat-video")
            }
        }
        put("up_mbps", uploadMbps)
        put("down_mbps", downloadMbps)
        put(
            "socks5", JSONObject(
                mapOf(
                    "listen" to "$LOCALHOST:$port",
                )
            )
        )
        put("retry", 5)
        put("fast_open", true)
        put("lazy_start", true)
        put("obfs", obfuscation)
        when (authPayloadType) {
            HysteriaBean.TYPE_BASE64 -> put("auth", authPayload)
            HysteriaBean.TYPE_STRING -> put("auth_str", authPayload)
        }
        if (sni.isBlank() && finalAddress == LOCALHOST && !serverAddress.isIpAddress()) {
            sni = serverAddress
        }
        if (sni.isNotBlank()) {
            put("server_name", sni)
        }
        if (alpn.isNotBlank()) put("alpn", alpn)
        if (caText.isNotBlank() && cacheFile != null) {
            val caFile = cacheFile()
            caFile.writeText(caText)
            put("ca", caFile.absolutePath)
        }

        if (allowInsecure) put("insecure", true)
        if (streamReceiveWindow > 0) put("recv_window_conn", streamReceiveWindow)
        if (connectionReceiveWindow > 0) put("recv_window", connectionReceiveWindow)
        if (disableMtuDiscovery) put("disable_mtu_discovery", true)

        // hy 1.2.0 （不兼容）
        put("resolver", "udp://127.0.0.1:" + DataStore.localDNSPort)

        put("hop_interval", hopInterval)
    }.toStringPretty()
}

fun isMultiPort(hyAddr: String): Boolean {
    if (!hyAddr.contains(":")) return false
    val p = hyAddr.substringAfterLast(":")
    if (p.contains("-") || p.contains(",")) return true
    return false
}

fun getFirstPort(portStr: String): Int {
    return portStr.substringBefore(":").substringBefore(",").toIntOrNull() ?: 443
}

fun HysteriaBean.canUseSingBox(): Boolean {
    if (protocol != HysteriaBean.PROTOCOL_UDP) return false
    return true
}

fun buildSingBoxOutboundHysteriaBean(bean: HysteriaBean): SingBoxOptions.Outbound_HysteriaOptions {
    return SingBoxOptions.Outbound_HysteriaOptions().apply {
        type = "hysteria"
        server = bean.serverAddress
        val port = bean.serverPorts.toIntOrNull()
        if (port != null) {
            server_port = port
        } else {
            hop_ports = bean.serverPorts
        }
        hop_interval = bean.hopInterval
        up_mbps = bean.uploadMbps
        down_mbps = bean.downloadMbps
        obfs = bean.obfuscation
        disable_mtu_discovery = bean.disableMtuDiscovery
        when (bean.authPayloadType) {
            HysteriaBean.TYPE_BASE64 -> auth = bean.authPayload
            HysteriaBean.TYPE_STRING -> auth_str = bean.authPayload
        }
        if (bean.streamReceiveWindow > 0) {
            recv_window_conn = bean.streamReceiveWindow.toLong()
        }
        if (bean.connectionReceiveWindow > 0) {
            recv_window_conn = bean.connectionReceiveWindow.toLong()
        }
        tls = SingBoxOptions.OutboundTLSOptions().apply {
            if (bean.sni.isNotBlank()) {
                server_name = bean.sni
            }
            if (bean.alpn.isNotBlank()) {
                alpn = bean.alpn.listByLineOrComma()
            }
            if (bean.caText.isNotBlank()) {
                certificate = bean.caText
            }
            insecure = bean.allowInsecure
            enabled = true
        }
    }
}
