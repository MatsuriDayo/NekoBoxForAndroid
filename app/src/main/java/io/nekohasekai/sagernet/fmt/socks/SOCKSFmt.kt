package io.nekohasekai.sagernet.fmt.socks

import moe.matsuri.nb4a.SingBoxOptions
import io.nekohasekai.sagernet.ktx.*
import moe.matsuri.nb4a.utils.NGUtil
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun parseSOCKS(link: String): SOCKSBean {
    if (!link.substringAfter("://").contains(":")) {
        // v2rayN shit format
        var url = link.substringAfter("://")
        if (url.contains("#")) {
            url = url.substringBeforeLast("#")
        }
        url = url.decodeBase64UrlSafe()
        val httpUrl = "http://$url".toHttpUrlOrNull() ?: error("Invalid v2rayN link content: $url")
        return SOCKSBean().apply {
            serverAddress = httpUrl.host
            serverPort = httpUrl.port
            username = httpUrl.username.takeIf { it != "null" } ?: ""
            password = httpUrl.password.takeIf { it != "null" } ?: ""
            if (link.contains("#")) {
                name = link.substringAfter("#").unUrlSafe()
            }
        }
    } else {
        val url = ("http://" + link.substringAfter("://")).toHttpUrlOrNull()
            ?: error("Not supported: $link")

        return SOCKSBean().apply {
            protocol = when {
                link.startsWith("socks4://") -> SOCKSBean.PROTOCOL_SOCKS4
                link.startsWith("socks4a://") -> SOCKSBean.PROTOCOL_SOCKS4A
                else -> SOCKSBean.PROTOCOL_SOCKS5
            }
            serverAddress = url.host
            serverPort = url.port
            username = url.username
            password = url.password
            name = url.fragment
        }
    }
}

fun SOCKSBean.toUri(): String {

    val builder = HttpUrl.Builder().scheme("http").host(serverAddress).port(serverPort)
    if (!username.isNullOrBlank()) builder.username(username)
    if (!password.isNullOrBlank()) builder.password(password)
    if (!name.isNullOrBlank()) builder.encodedFragment(name.urlSafe())
    return builder.toLink("socks${protocolVersion()}")

}

fun SOCKSBean.toV2rayN(): String {

    var link = ""
    if (username.isNotBlank()) {
        link += username.urlSafe() + ":" + password.urlSafe() + "@"
    }
    link += "$serverAddress:$serverPort"
    link = "socks://" + NGUtil.encode(link)
    if (name.isNotBlank()) {
        link += "#" + name.urlSafe()
    }

    return link

}

fun buildSingBoxOutboundSocksBean(bean: SOCKSBean): SingBoxOptions.Outbound_SocksOptions {
    return SingBoxOptions.Outbound_SocksOptions().apply {
        type = "socks"
        server = bean.serverAddress
        server_port = bean.serverPort
        username = bean.username
        password = bean.password
        version = bean.protocolVersionName()
    }
}
