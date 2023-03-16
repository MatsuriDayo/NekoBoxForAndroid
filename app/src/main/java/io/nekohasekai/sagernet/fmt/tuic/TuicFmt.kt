package io.nekohasekai.sagernet.fmt.tuic

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.LOCALHOST
import io.nekohasekai.sagernet.ktx.isIpAddress
import io.nekohasekai.sagernet.ktx.toStringPretty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import moe.matsuri.nb4a.plugin.Plugins
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.InetAddress

fun TuicBean.buildTuicConfig(port: Int, cacheFile: (() -> File)?): String {
    if (Plugins.isUsingMatsuriExe("tuic-plugin")) {
        if (!serverAddress.isIpAddress()) {
            runBlocking {
                finalAddress = withContext(Dispatchers.IO) {
                    InetAddress.getAllByName(serverAddress)
                }?.firstOrNull()?.hostAddress ?: "127.0.0.1"
                // TODO network on main thread, tuic don't support "sni"
            }
        }
    }
    return JSONObject().apply {
        put("relay", JSONObject().apply {
            if (sni.isNotBlank()) {
                put("server", sni)
                put("ip", finalAddress)
            } else if (serverAddress.isIpAddress()) {
                put("server", finalAddress)
            } else {
                put("server", serverAddress)
                put("ip", finalAddress)
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
                put("alpn", JSONArray(alpn.split("\n")))
            }
            put("congestion_controller", congestionController)
            put("disable_sni", disableSNI)
            put("reduce_rtt", reduceRTT)
            put("max_udp_relay_packet_size", mtu)
            if (fastConnect) put("fast_connect", true)
            if (allowInsecure) put("insecure", true)
        })
        put("local", JSONObject().apply {
            put("ip", LOCALHOST)
            put("port", port)
        })
        put("log_level", if (DataStore.logLevel > 0) "debug" else "info")
    }.toStringPretty()
}
