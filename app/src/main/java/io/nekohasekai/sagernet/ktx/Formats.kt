package io.nekohasekai.sagernet.ktx

import com.google.gson.JsonParser
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.Serializable
import io.nekohasekai.sagernet.fmt.http.parseHttp
import io.nekohasekai.sagernet.fmt.hysteria.parseHysteria1
import io.nekohasekai.sagernet.fmt.hysteria.parseHysteria2
import io.nekohasekai.sagernet.fmt.naive.parseNaive
import io.nekohasekai.sagernet.fmt.parseUniversal
import io.nekohasekai.sagernet.fmt.shadowsocks.parseShadowsocks
import io.nekohasekai.sagernet.fmt.shadowsocksr.parseShadowsocksR
import io.nekohasekai.sagernet.fmt.socks.parseSOCKS
import io.nekohasekai.sagernet.fmt.trojan.parseTrojan
import io.nekohasekai.sagernet.fmt.tuic.parseTuic
import io.nekohasekai.sagernet.fmt.juicity.parseJuicity
import io.nekohasekai.sagernet.fmt.trojan_go.parseTrojanGo
import io.nekohasekai.sagernet.fmt.v2ray.parseV2Ray
import moe.matsuri.nb4a.proxy.anytls.parseAnytls
import moe.matsuri.nb4a.utils.JavaUtil.gson
import moe.matsuri.nb4a.utils.Util
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// JSON & Base64

fun JSONObject.toStringPretty(): String {
    return gson.toJson(JsonParser.parseString(this.toString()))
}

inline fun <reified T : Any> JSONArray.filterIsInstance(): List<T> {
    val list = mutableListOf<T>()
    for (i in 0 until this.length()) {
        if (this[i] is T) list.add(this[i] as T)
    }
    return list
}

inline fun JSONArray.forEach(action: (Int, Any) -> Unit) {
    for (i in 0 until this.length()) {
        action(i, this[i])
    }
}

inline fun JSONObject.forEach(action: (String, Any) -> Unit) {
    for (k in this.keys()) {
        action(k, this.get(k))
    }
}

fun isJsonObjectValid(j: Any): Boolean {
    if (j is JSONObject) return true
    if (j is JSONArray) return true
    try {
        JSONObject(j as String)
    } catch (ex: JSONException) {
        try {
            JSONArray(j)
        } catch (ex1: JSONException) {
            return false
        }
    }
    return true
}

// wtf hutool
fun JSONObject.getStr(name: String): String? {
    val obj = this.opt(name) ?: return null
    if (obj is String) {
        if (obj.isBlank()) {
            return null
        }
        return obj
    } else {
        return null
    }
}

fun JSONObject.getBool(name: String): Boolean? {
    return try {
        getBoolean(name)
    } catch (ignored: Exception) {
        null
    }
}


// 重名了喵
fun JSONObject.getIntNya(name: String): Int? {
    return try {
        getInt(name)
    } catch (ignored: Exception) {
        null
    }
}


fun String.decodeBase64UrlSafe(): String {
    return String(Util.b64Decode(this))
}

// Sub

class SubscriptionFoundException(val link: String) : RuntimeException()

suspend fun parseProxies(text: String): List<AbstractBean> {
    val links = text.split('\n').flatMap { it.trim().split(' ') }
    val linksByLine = text.split('\n').map { it.trim() }

    val entities = ArrayList<AbstractBean>()
    val entitiesByLine = ArrayList<AbstractBean>()

    fun String.parseLink(entities: ArrayList<AbstractBean>) {
        if (startsWith("clash://install-config?") || startsWith("sn://subscription?")) {
            throw SubscriptionFoundException(this)
        }

        if (startsWith("sn://")) {
            Logs.d("Try parse universal link: $this")
            runCatching {
                entities.add(parseUniversal(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("socks://") || startsWith("socks4://") || startsWith("socks4a://") || startsWith(
                "socks5://"
            )
        ) {
            Logs.d("Try parse socks link: $this")
            runCatching {
                entities.add(parseSOCKS(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (matches("(http|https)://.*".toRegex())) {
            Logs.d("Try parse http link: $this")
            runCatching {
                entities.add(parseHttp(this))
            }.onFailure {
                Logs.w(it)
                val clashUrl = HttpUrl.Builder()
                    .scheme("https")
                    .host("install-config")
                    .addQueryParameter("url", this)
                    .build()
                    .toString()
                    .replaceFirst("https://", "clash://")
                throw (SubscriptionFoundException(clashUrl))
            }
        } else if (startsWith("vmess://")) {
            Logs.d("Try parse v2ray link: $this")
            runCatching {
                entities.add(parseV2Ray(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("vless://")) {
            Logs.d("Try parse vless link: $this")
            runCatching {
                entities.add(parseV2Ray(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("trojan://")) {
            Logs.d("Try parse trojan link: $this")
            runCatching {
                entities.add(parseTrojan(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("trojan-go://")) {
            Logs.d("Try parse trojan-go link: $this")
            runCatching {
                entities.add(parseTrojanGo(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("ss://")) {
            Logs.d("Try parse shadowsocks link: $this")
            runCatching {
                entities.add(parseShadowsocks(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("ssr://")) {
            Logs.d("Try parse shadowsocksr link: $this")
            runCatching {
                entities.add(parseShadowsocksR(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("naive+")) {
            Logs.d("Try parse naive link: $this")
            runCatching {
                entities.add(parseNaive(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("hysteria://")) {
            Logs.d("Try parse hysteria1 link: $this")
            runCatching {
                entities.add(parseHysteria1(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("hysteria2://") || startsWith("hy2://")) {
            Logs.d("Try parse hysteria2 link: $this")
            runCatching {
                entities.add(parseHysteria2(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("tuic://")) {
            Logs.d("Try parse TUIC link: $this")
            runCatching {
                entities.add(parseTuic(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("juicity://")) {
            Logs.d("Try parse Juicity link: $this")
            runCatching {
                entities.add(parseJuicity(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("anytls://")) {
            Logs.d("Try parse anytls link: $this")
            runCatching {
                entities.add(parseAnytls(this))
            }.onFailure {
                Logs.w(it)
            }
        }
    }

    for (link in links) {
        link.parseLink(entities)
    }
    for (link in linksByLine) {
        link.parseLink(entitiesByLine)
    }
//    var isBadLink = false
    if (entities.onEach { it.initializeDefaultValues() }.size == entitiesByLine.onEach { it.initializeDefaultValues() }.size) run test@{
        entities.forEachIndexed { index, bean ->
            val lineBean = entitiesByLine[index]
            if (bean == lineBean && bean.displayName() != lineBean.displayName()) {
//                isBadLink = true
                return@test
            }
        }
    }
    return if (entities.size > entitiesByLine.size) entities else entitiesByLine
}

fun <T : Serializable> T.applyDefaultValues(): T {
    initializeDefaultValues()
    return this
}
