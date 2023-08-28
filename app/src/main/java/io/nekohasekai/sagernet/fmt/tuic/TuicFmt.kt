package io.nekohasekai.sagernet.fmt.tuic

import io.nekohasekai.sagernet.fmt.LOCALHOST
import io.nekohasekai.sagernet.ktx.*
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.JavaUtil
import moe.matsuri.nb4a.utils.Util
import moe.matsuri.nb4a.utils.listByLineOrComma
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun parseTuic(url: String): TuicBean {
    // https://github.com/daeuniverse/dae/discussions/182
    var link = url.replace("tuic://", "https://").toHttpUrlOrNull() ?: error(
        "invalid tuic link $url"
    )
    return TuicBean().apply {
        protocolVersion = 5

        name = link.fragment
        uuid = link.username
        token = link.password
        serverAddress = link.host
        serverPort = link.port

        link.queryParameter("sni")?.let {
            sni = it
        }
        link.queryParameter("congestion_control")?.let {
            congestionController = it
        }
        link.queryParameter("udp_relay_mode")?.let {
            udpRelayMode = it
        }
        link.queryParameter("alpn")?.let {
            alpn = it
        }
        link.queryParameter("allow_insecure")?.let {
            if (it == "1") allowInsecure = true
        }
        link.queryParameter("disable_sni")?.let {
            if (it == "1") disableSNI = true
        }
    }
}

fun TuicBean.toUri(): String {
    val builder = linkBuilder().username(uuid).password(token).host(serverAddress).port(serverPort)

    builder.addQueryParameter("congestion_control", congestionController)
    builder.addQueryParameter("udp_relay_mode", udpRelayMode)

    if (sni.isNotBlank()) builder.addQueryParameter("sni", sni)
    if (alpn.isNotBlank()) builder.addQueryParameter("alpn", alpn)
    if (allowInsecure) builder.addQueryParameter("allow_insecure", "1")
    if (disableSNI) builder.addQueryParameter("disable_sni", "1")
    if (name.isNotBlank()) builder.encodedFragment(name.urlSafe())

    return builder.toLink("tuic")
}

fun buildSingBoxOutboundTuicBean(bean: TuicBean): SingBoxOptions.Outbound_TUICOptions {
    return SingBoxOptions.Outbound_TUICOptions().apply {
        type = "tuic"
        server = bean.serverAddress
        server_port = bean.serverPort
        uuid = bean.uuid
        password = bean.token
        congestion_control = bean.congestionController
        when (bean.udpRelayMode) {
            "quic" -> udp_relay_mode = "quic"
        }
        zero_rtt_handshake = bean.reduceRTT
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
            disable_sni = bean.disableSNI
            insecure = bean.allowInsecure
            enabled = true
        }
    }
}

fun TuicBean.pluginId(): String {
    return when (protocolVersion) {
        5 -> "tuic-v5-plugin"
        else -> "tuic-plugin"
    }
}

fun TuicBean.buildTuicConfig(port: Int, cacheFile: (() -> File)?): String {
    val config = when (protocolVersion) {
        5 -> buildTuicConfigV5(port, cacheFile)
        else -> buildTuicConfigV4(port, cacheFile)
    }.toString()
    var gsonMap = mutableMapOf<String, Any>()
    gsonMap = JavaUtil.gson.fromJson(config, gsonMap.javaClass)
    Util.mergeJSON(customJSON, gsonMap)
    return JavaUtil.gson.toJson(gsonMap)
}

fun TuicBean.buildTuicConfigV5(port: Int, cacheFile: (() -> File)?): JSONObject {
    return JSONObject().apply {
        put("relay", JSONObject().apply {
            var disableSNI2 = disableSNI

            if (sni.isNotBlank()) { // domain + SNI
                put("server", "$sni:$finalPort")
                if (finalAddress.isIpAddress()) {
                    put("ip", finalAddress)
                } else {
                    throw Exception("TUIC must use IP address when you need spoof SNI.")
                }
            } else if (!serverAddress.isIpAddress()) { // domain
                put("server", "$serverAddress:$finalPort")
                if (finalAddress.isIpAddress()) {
                    put("ip", finalAddress)
                }
            } else { // prue IP server
                put("server", "example.com:$finalPort")
                put("ip", finalAddress)
                disableSNI2 = true
            }

            put("uuid", uuid)
            put("password", token)

            if (caText.isNotBlank() && cacheFile != null) {
                val caFile = cacheFile()
                caFile.writeText(caText)
                put("certificates", JSONArray(listOf(caFile.absolutePath)))
            }

            put("udp_relay_mode", udpRelayMode)
            if (alpn.isNotBlank()) {
                put("alpn", JSONArray(alpn.listByLineOrComma()))
            }
            put("congestion_control", congestionController)
            put("disable_sni", disableSNI2)
            put("zero_rtt_handshake", reduceRTT)
            if (allowInsecure) put("allow_insecure", true)
        })
        put("local", JSONObject().apply {
            put("server", "127.0.0.1:$port")
        })
        put("log_level", "debug")
    }
}

fun TuicBean.buildTuicConfigV4(port: Int, cacheFile: (() -> File)?): JSONObject {
    return JSONObject().apply {
        put("relay", JSONObject().apply {
            var disableSNI2 = disableSNI

            if (sni.isNotBlank()) { // domain + SNI
                put("server", sni)
                if (finalAddress.isIpAddress()) {
                    put("ip", finalAddress)
                } else {
                    throw Exception("TUIC must use IP address when you need spoof SNI.")
                }
            } else if (!serverAddress.isIpAddress()) { // domain
                put("server", serverAddress)
                if (finalAddress.isIpAddress()) {
                    put("ip", finalAddress)
                }
            } else { // prue IP server
                put("server", "example.com")
                put("ip", finalAddress)
                disableSNI2 = true
            }

            put("port", finalPort)
            put("token", token)

            if (caText.isNotBlank() && cacheFile != null) {
                val caFile = cacheFile()
                caFile.writeText(caText)
                put("certificates", JSONArray(listOf(caFile.absolutePath)))
            }

            put("udp_relay_mode", udpRelayMode)
            if (alpn.isNotBlank()) {
                put("alpn", JSONArray(alpn.listByLineOrComma()))
            }
            put("congestion_controller", congestionController)
            put("disable_sni", disableSNI2)
            put("reduce_rtt", reduceRTT)
            put("max_udp_relay_packet_size", mtu)
            if (fastConnect) put("fast_connect", true)
            if (allowInsecure) put("insecure", true)
        })
        put("local", JSONObject().apply {
            put("ip", LOCALHOST)
            put("port", port)
        })
        put("log_level", "debug")
    }
}
