package io.nekohasekai.sagernet.group

import android.annotation.SuppressLint
import androidx.core.net.toUri
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.*
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.ProxyDecorator
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.fmt.hysteria.parseHysteria1Json
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocks.parseShadowsocks
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.trojan_go.parseTrojanGo
import io.nekohasekai.sagernet.fmt.tuic.TuicBean
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.fmt.v2ray.isTLS
import io.nekohasekai.sagernet.fmt.v2ray.setTLS
import io.nekohasekai.sagernet.fmt.wireguard.WireGuardBean
import io.nekohasekai.sagernet.ktx.*
import kotlinx.coroutines.delay
import libcore.Libcore
import moe.matsuri.nb4a.Protocols
import moe.matsuri.nb4a.proxy.anytls.AnyTLSBean
import moe.matsuri.nb4a.proxy.config.ConfigBean
import moe.matsuri.nb4a.utils.Util
import org.ini4j.Ini
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.yaml.snakeyaml.TypeDescription
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.YAMLException
import java.io.StringReader

@Suppress("EXPERIMENTAL_API_USAGE")
object RawUpdater : GroupUpdater() {

    // ==================== پارامترهای بهینه‌سازی مرحله ۶ ====================
    private const val DECORATION_THROTTLE_DELAY_MS = 500L
    private const val GEMINI_LATENCY_THRESHOLD_MS = 1000
    // ======================================================================

    @SuppressLint("Recycle")
    override suspend fun doUpdate(
        proxyGroup: ProxyGroup,
        subscription: SubscriptionBean,
        userInterface: GroupManager.Interface?,
        byUser: Boolean
    ) {
        val link = subscription.link
        var proxies: List<AbstractBean>

        if (link.startsWith("content://")) {
            val contentText = app.contentResolver.openInputStream(link.toUri())
                ?.bufferedReader()
                ?.readText()
            proxies = contentText?.let { parseRaw(contentText) }
                ?: error(app.getString(R.string.no_proxies_found_in_subscription))
        } else {
            val response = Libcore.newHttpClient().apply {
                trySocks5(DataStore.mixedPort)
                tryH3Direct()
                when (DataStore.appTLSVersion) {
                    "1.3" -> restrictedTLS()
                }
            }.newRequest().apply {
                if (DataStore.allowInsecureOnRequest) {
                    allowInsecure()
                }
                setURL(subscription.link)
                setUserAgent(subscription.customUserAgent.takeIf { it.isNotBlank() } ?: USER_AGENT)
            }.execute()

            proxies = parseRaw(Util.getStringBox(response.contentString))
                ?: error(app.getString(R.string.no_proxies_found))
            subscription.subscriptionUserinfo = Util.getStringBox(response.getHeader("Subscription-Userinfo"))

            // 修改默认名字
            if (proxyGroup.name?.startsWith("Subscription #") == true) {
                var remoteName = Util.getStringBox(response.getHeader("content-disposition"))
                if (remoteName.isNotBlank()) {
                    remoteName = Util.decodeFilename(remoteName)
                    if (remoteName.isNotBlank()) {
                        proxyGroup.name = remoteName
                        SagerDatabase.groupDao.updateGroup(proxyGroup)
                    }
                }
            }
        }

        // ==================== اعمال دکوریشن روی پروکسی‌ها با بهینه‌سازی ====================
        val enableFlag = DataStore.enableFlagEmoji
        val enableGemini = DataStore.enableGeminiMarker

        if (enableFlag || enableGemini) {
            val localProxyPort = DataStore.mixedPort
            
            // پردازش ترتیبی با تاخیر برای جلوگیری از مسدود شدن IP
            proxies.forEachIndexed { index, bean ->
                var executedNetworkCall = false

                // 1. افزودن ایموجی پرچم
                if (enableFlag) {
                    ProxyDecorator.decorateBeanWithFlag(bean, localProxyPort)
                    executedNetworkCall = true
                }

                // 2. افزودن نشانگر Gemini (فقط در صورت وجود ping معتبر)
                // محافظ تاخیر سخت: فقط اگر ping اندازه‌گیری شده و کمتر از 1000ms باشد
                if (enableGemini) {
                    // توجه: ping در این مرحله ممکن است هنوز اندازه‌گیری نشده باشد
                    // در صورت وجود، از آن برای تصمیم‌گیری استفاده می‌کنیم
                    val ping = bean.ping ?: 0
                    if (ping in 1 until GEMINI_LATENCY_THRESHOLD_MS) {
                        ProxyDecorator.decorateBeanWithGemini(bean, localProxyPort)
                        executedNetworkCall = true
                    }
                }

                // Throttling: تاخیر 500ms بین درخواست‌های شبکه
                // رد کردن تاخیر برای آخرین عنصر برای جلوگیری از انتظار اضافی
                if (executedNetworkCall && index < proxies.lastIndex) {
                    delay(DECORATION_THROTTLE_DELAY_MS)
                }
            }
        }
        // ====================================================================

        // 去重
        val nameMap = LinkedHashMap<String, AbstractBean>()
        val duplicate = mutableListOf<String>()

        for (bean in proxies) {
            val name = bean.displayName()
            if (nameMap.containsKey(name)) {
                duplicate.add(name)
            } else {
                nameMap[name] = bean
            }
        }

        val toReplace = LinkedHashMap<String, ProxyEntity>()
        val toDelete = ArrayList<ProxyEntity>()

        for (profile in SagerDatabase.proxyDao.getByGroup(proxyGroup.id)) {
            val name = profile.displayName()
            if (nameMap.containsKey(name)) {
                toReplace[name] = profile
            } else {
                toDelete.add(profile)
            }
        }

        Logs.d("To delete: ${toDelete.size}, to replace: ${toReplace.size}")

        val toUpdate = ArrayList<ProxyEntity>()
        val added = mutableListOf<String>()
        val updated = mutableMapOf<String, String>()
        val deleted = toDelete.map { it.displayName() }

        var userOrder = 1L
        var changed = toDelete.size

        for ((name, bean) in nameMap.entries) {
            if (toReplace.contains(name)) {
                val entity = toReplace[name]!!
                val existsBean = entity.requireBean()

                // 更新订阅，保留自定义覆写设置
                bean.customOutboundJson = existsBean.customOutboundJson
                bean.customConfigJson = existsBean.customConfigJson

                when {
                    existsBean != bean -> {
                        changed++
                        entity.putBean(bean)
                        toUpdate.add(entity)
                        updated[entity.displayName()] = name
                        Logs.d("Updated profile: $name")
                    }
                    entity.userOrder != userOrder -> {
                        entity.putBean(bean)
                        toUpdate.add(entity)
                        entity.userOrder = userOrder
                        Logs.d("Reordered profile: $name")
                    }
                    else -> {
                        Logs.d("Ignored profile: $name")
                    }
                }
            } else {
                changed++
                SagerDatabase.proxyDao.addProxy(
                    ProxyEntity(
                        groupId = proxyGroup.id,
                        userOrder = userOrder
                    ).apply {
                        putBean(bean)
                    })
                added.add(name)
                Logs.d("Inserted profile: $name")
            }
            userOrder++
        }

        SagerDatabase.proxyDao.updateProxy(toUpdate).also { Logs.d("Updated profiles: $it") }
        SagerDatabase.proxyDao.deleteProxy(toDelete).also { Logs.d("Deleted profiles: $it") }

        val existCount = SagerDatabase.proxyDao.countByGroup(proxyGroup.id).toInt()
        if (existCount != proxies.size) {
            Logs.e("Exist profiles: $existCount, new profiles: ${proxies.size}")
        }

        subscription.lastUpdated = (System.currentTimeMillis() / 1000).toInt()
        SagerDatabase.groupDao.updateGroup(proxyGroup)
        finishUpdate(proxyGroup)

        userInterface?.onUpdateSuccess(
            proxyGroup,
            changed,
            added,
            updated,
            deleted,
            duplicate,
            byUser
        )
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun parseRaw(text: String, fileName: String = ""): List<AbstractBean>? {
        val proxies = mutableListOf<AbstractBean>()

        // 1. Clash / Meta YAML
        if (text.contains("proxies:")) {
            try {
                val yaml = Yaml().apply {
                    addTypeDescription(TypeDescription(String::class.java, "str"))
                }.loadAs(text, Map::class.java)

                val globalClientFingerprint = yaml["global-client-fingerprint"]?.toString() ?: ""

                for (proxy in (yaml["proxies"] as? List<Map<String, Any>> ?: error(
                    app.getString(R.string.no_proxies_found_in_file)
                ))) {
                    when (proxy["type"] as String) {
                        "socks5" -> {
                            proxies.add(SOCKSBean().apply {
                                serverAddress = proxy["server"] as String
                                serverPort = proxy["port"].toString().toInt()
                                username = proxy["username"]?.toString()
                                password = proxy["password"]?.toString()
                                name = proxy["name"]?.toString()
                            })
                        }
                        "http" -> {
                            proxies.add(HttpBean().apply {
                                serverAddress = proxy["server"] as String
                                serverPort = proxy["port"].toString().toInt()
                                username = proxy["username"]?.toString()
                                password = proxy["password"]?.toString()
                                setTLS(proxy["tls"]?.toString() == "true")
                                sni = proxy["sni"]?.toString()
                                name = proxy["name"]?.toString()
                                allowInsecure = proxy["skip-cert-verify"]?.toString() == "true"
                            })
                        }
                        "ss" -> {
                            val ssPlugin = mutableListOf<String>()
                            if (proxy.containsKey("plugin")) {
                                val opts = proxy["plugin-opts"] as? Map<String, Any>
                                when (proxy["plugin"] as? String) {
                                    "obfs" -> {
                                        ssPlugin.apply {
                                            add("obfs-local")
                                            add("obfs=" + (opts?.get("mode")?.toString() ?: ""))
                                            add("obfs-host=" + (opts?.get("host")?.toString() ?: ""))
                                        }
                                    }
                                    "v2ray-plugin" -> {
                                        ssPlugin.apply {
                                            add("v2ray-plugin")
                                            if (opts?.get("mode")?.toString() == "websocket") {
                                                add("ws")
                                            }
                                            if (opts?.get("host")?.toString().isNotBlank()) {
                                                add("host=" + opts["host"])
                                            }
                                            if (opts?.get("path")?.toString().isNotBlank()) {
                                                add("path=" + opts["path"])
                                            }
                                            if (opts?.get("tls")?.toString() == "true") {
                                                add("tls")
                                            }
                                        }
                                    }
                                }
                            }
                            proxies.add(ShadowsocksBean().apply {
                                serverAddress = proxy["server"] as String
                                serverPort = proxy["port"].toString().toInt()
                                method = proxy["cipher"] as String
                                password = proxy["password"] as String
                                plugin = ssPlugin.joinToString(";")
                                name = proxy["name"]?.toString()
                            })
                        }
                        "vmess" -> {
                            proxies.add(VMessBean().apply {
                                serverAddress = proxy["server"] as String
                                serverPort = proxy["port"].toString().toInt()
                                uuid = proxy["uuid"] as String
                                security = proxy["cipher"]?.toString() ?: "auto"
                                alterId = (proxy["alterId"] as? Number)?.toInt() ?: 0
                                val network = proxy["network"]?.toString() ?: "tcp"
                                when (network) {
                                    "ws" -> {
                                        val wsOpts = proxy["ws-opts"] as? Map<String, Any>
                                        this.network = "ws"
                                        path = wsOpts?.get("path")?.toString() ?: ""
                                        val headers = wsOpts?.get("headers") as? Map<String, String>
                                        host = headers?.get("Host") ?: ""
                                    }
                                    "grpc" -> {
                                        val grpcOpts = proxy["grpc-opts"] as? Map<String, Any>
                                        this.network = "grpc"
                                        host = grpcOpts?.get("grpc-service-name")?.toString() ?: ""
                                    }
                                    else -> {
                                        this.network = network
                                    }
                                }
                                setTLS(proxy["tls"]?.toString() == "true")
                                sni = proxy["sni"]?.toString()
                                fingerprint = proxy["client-fingerprint"]?.toString()
                                allowInsecure = proxy["skip-cert-verify"]?.toString() == "true"
                                name = proxy["name"]?.toString()
                            })
                        }
                        "trojan" -> {
                            proxies.add(TrojanBean().apply {
                                serverAddress = proxy["server"] as String
                                serverPort = proxy["port"].toString().toInt()
                                password = proxy["password"] as String
                                setTLS(proxy["tls"]?.toString() == "true")
                                sni = proxy["sni"]?.toString()
                                fingerprint = proxy["client-fingerprint"]?.toString()
                                allowInsecure = proxy["skip-cert-verify"]?.toString() == "true"
                                val network = proxy["network"]?.toString() ?: "tcp"
                                when (network) {
                                    "ws" -> {
                                        val wsOpts = proxy["ws-opts"] as? Map<String, Any>
                                        this.network = "ws"
                                        path = wsOpts?.get("path")?.toString() ?: ""
                                        val headers = wsOpts?.get("headers") as? Map<String, String>
                                        host = headers?.get("Host") ?: ""
                                    }
                                    "grpc" -> {
                                        val grpcOpts = proxy["grpc-opts"] as? Map<String, Any>
                                        this.network = "grpc"
                                        host = grpcOpts?.get("grpc-service-name")?.toString() ?: ""
                                    }
                                    else -> {
                                        this.network = network
                                    }
                                }
                                name = proxy["name"]?.toString()
                            })
                        }
                        "hysteria", "hysteria2" -> {
                            val hysteria = HysteriaBean()
                            hysteria.serverAddress = proxy["server"] as String
                            hysteria.serverPort = proxy["port"].toString().toInt()
                            hysteria.up = (proxy["up"] as? Number)?.toString() ?: "20"
                            hysteria.down = (proxy["down"] as? Number)?.toString() ?: "100"
                            hysteria.authString = proxy["auth_str"]?.toString()
                            hysteria.auth = proxy["auth"]?.toString()
                            hysteria.obfs = proxy["obfs"]?.toString()
                            hysteria.obfsPassword = proxy["obfs-password"]?.toString()
                            hysteria.sni = proxy["sni"]?.toString()
                            hysteria.fingerprint = proxy["client-fingerprint"]?.toString()
                            hysteria.allowInsecure = proxy["skip-cert-verify"]?.toString() == "true"
                            if (proxy["type"] == "hysteria2") {
                                hysteria.version = 2
                            }
                            proxies.add(hysteria)
                        }
                        "tuic" -> {
                            proxies.add(TuicBean().apply {
                                serverAddress = proxy["server"] as String
                                serverPort = proxy["port"].toString().toInt()
                                token = proxy["token"] as String
                                uuid = proxy["uuid"]?.toString()
                                password = proxy["password"]?.toString()
                                setTLS(proxy["tls"]?.toString() == "true")
                                sni = proxy["sni"]?.toString()
                                fingerprint = proxy["client-fingerprint"]?.toString()
                                allowInsecure = proxy["skip-cert-verify"]?.toString() == "true"
                                name = proxy["name"]?.toString()
                                congestion_control = proxy["congestion_control"]?.toString() ?: "bbr"
                                udp_relay_mode = proxy["udp_relay_mode"]?.toString() ?: "native"
                                zero_rtt_handshake = proxy["zero_rtt_handshake"]?.toString() == "true"
                                heartBeat = proxy["heartbeat"]?.toString() ?: "10s"
                            })
                        }
                        "wireguard" -> {
                            proxies.add(WireGuardBean().apply {
                                serverAddress = proxy["server"] as String
                                serverPort = proxy["port"].toString().toInt()
                                privateKey = proxy["private-key"] as String
                                val peer = (proxy["peer"] as? Map<String, Any>)
                                publicKey = peer?.get("public-key") as? String ?: ""
                                allowedIps = (peer?.get("allowed-ips") as? String) ?: "0.0.0.0/0"
                                presharedKey = peer?.get("preshared-key")?.toString()
                                name = proxy["name"]?.toString()
                            })
                        }
                        "ssh" -> {
                            // SSH not fully implemented in Clash
                            // skip
                        }
                        "anytls" -> {
                            proxies.add(AnyTLSBean().apply {
                                serverAddress = proxy["server"] as String
                                serverPort = proxy["port"].toString().toInt()
                                password = proxy["password"] as String
                                setTLS(proxy["tls"]?.toString() == "true")
                                sni = proxy["sni"]?.toString()
                                name = proxy["name"]?.toString()
                            })
                        }
                    }
                }
            } catch (e: YAMLException) {
                // fall through
            } catch (e: Exception) {
                // fall through
            }
        }

        // 2. Sing-box / NekoBox JSON
        if (proxies.isEmpty() && text.trim().startsWith("{")) {
            try {
                val json = JSONObject(text)
                if (json.has("outbounds")) {
                    val outbounds = json.getJSONArray("outbounds")
                    for (i in 0 until outbounds.length()) {
                        val out = outbounds.getJSONObject(i)
                        val type = out.getString("type")
                        when (type) {
                            "socks" -> {
                                val socks = SOCKSBean()
                                socks.serverAddress = out.getString("server")
                                socks.serverPort = out.getInt("server_port")
                                socks.username = out.optString("username")
                                socks.password = out.optString("password")
                                socks.name = out.optString("tag")
                                proxies.add(socks)
                            }
                            "http" -> {
                                val http = HttpBean()
                                http.serverAddress = out.getString("server")
                                http.serverPort = out.getInt("server_port")
                                http.username = out.optString("username")
                                http.password = out.optString("password")
                                http.setTLS(out.optBoolean("tls"))
                                http.sni = out.optString("tls").let { if (it.isNotBlank()) out.optString("server_name") else "" }
                                http.name = out.optString("tag")
                                proxies.add(http)
                            }
                            "shadowsocks" -> {
                                val ss = ShadowsocksBean()
                                ss.serverAddress = out.getString("server")
                                ss.serverPort = out.getInt("server_port")
                                ss.method = out.getString("method")
                                ss.password = out.getString("password")
                                ss.plugin = out.optString("plugin")
                                ss.name = out.optString("tag")
                                proxies.add(ss)
                            }
                            "vmess" -> {
                                val vmess = VMessBean()
                                vmess.serverAddress = out.getString("server")
                                vmess.serverPort = out.getInt("server_port")
                                vmess.uuid = out.getString("uuid")
                                vmess.security = out.optString("security", "auto")
                                vmess.alterId = out.optInt("alter_id", 0)
                                vmess.network = out.optString("transport", "tcp")
                                vmess.path = out.optString("path")
                                vmess.host = out.optString("host")
                                vmess.setTLS(out.optBoolean("tls"))
                                vmess.sni = out.optString("server_name")
                                vmess.fingerprint = out.optString("fingerprint")
                                vmess.allowInsecure = out.optBoolean("allow_insecure")
                                vmess.name = out.optString("tag")
                                proxies.add(vmess)
                            }
                            "trojan" -> {
                                val trojan = TrojanBean()
                                trojan.serverAddress = out.getString("server")
                                trojan.serverPort = out.getInt("server_port")
                                trojan.password = out.getString("password")
                                trojan.setTLS(out.optBoolean("tls"))
                                trojan.sni = out.optString("server_name")
                                trojan.fingerprint = out.optString("fingerprint")
                                trojan.allowInsecure = out.optBoolean("allow_insecure")
                                trojan.network = out.optString("transport", "tcp")
                                trojan.path = out.optString("path")
                                trojan.host = out.optString("host")
                                trojan.name = out.optString("tag")
                                proxies.add(trojan)
                            }
                            "hysteria" -> {
                                val hysteria = HysteriaBean()
                                hysteria.serverAddress = out.getString("server")
                                hysteria.serverPort = out.getInt("server_port")
                                hysteria.up = out.optString("up", "20")
                                hysteria.down = out.optString("down", "100")
                                hysteria.authString = out.optString("auth_str")
                                hysteria.auth = out.optString("auth")
                                hysteria.obfs = out.optString("obfs")
                                hysteria.obfsPassword = out.optString("obfs-password")
                                hysteria.sni = out.optString("server_name")
                                hysteria.fingerprint = out.optString("fingerprint")
                                hysteria.allowInsecure = out.optBoolean("allow_insecure")
                                hysteria.version = if (type == "hysteria2") 2 else 1
                                hysteria.name = out.optString("tag")
                                proxies.add(hysteria)
                            }
                            "tuic" -> {
                                val tuic = TuicBean()
                                tuic.serverAddress = out.getString("server")
                                tuic.serverPort = out.getInt("server_port")
                                tuic.token = out.optString("token")
                                tuic.uuid = out.optString("uuid")
                                tuic.password = out.optString("password")
                                tuic.setTLS(out.optBoolean("tls"))
                                tuic.sni = out.optString("server_name")
                                tuic.fingerprint = out.optString("fingerprint")
                                tuic.allowInsecure = out.optBoolean("allow_insecure")
                                tuic.congestion_control = out.optString("congestion_control", "bbr")
                                tuic.udp_relay_mode = out.optString("udp_relay_mode", "native")
                                tuic.zero_rtt_handshake = out.optBoolean("zero_rtt_handshake")
                                tuic.heartBeat = out.optString("heartbeat", "10s")
                                tuic.name = out.optString("tag")
                                proxies.add(tuic)
                            }
                            "wireguard" -> {
                                val wg = WireGuardBean()
                                wg.serverAddress = out.getString("server")
                                wg.serverPort = out.getInt("server_port")
                                wg.privateKey = out.getString("private_key")
                                wg.publicKey = out.optString("public_key")
                                wg.allowedIps = out.optString("allowed_ips", "0.0.0.0/0")
                                wg.presharedKey = out.optString("preshared_key")
                                wg.name = out.optString("tag")
                                proxies.add(wg)
                            }
                            "anytls" -> {
                                val anytls = AnyTLSBean()
                                anytls.serverAddress = out.getString("server")
                                anytls.serverPort = out.getInt("server_port")
                                anytls.password = out.getString("password")
                                anytls.setTLS(out.optBoolean("tls"))
                                anytls.sni = out.optString("server_name")
                                anytls.name = out.optString("tag")
                                proxies.add(anytls)
                            }
                            "config" -> {
                                val config = ConfigBean()
                                config.name = out.optString("tag")
                                proxies.add(config)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // fall through
            }
        }

        // 3. URI schemes (vmess://, ss://, trojan://, etc.)
        if (proxies.isEmpty()) {
            try {
                val lines = text.lines().filter { it.isNotBlank() }
                for (line in lines) {
                    val uri = line.trim()
                    try {
                        val bean = Protocols.parseUri(uri)
                        if (bean != null) {
                            proxies.add(bean)
                        }
                    } catch (e: Exception) {
                        // skip invalid line
                    }
                }
            } catch (e: Exception) {
                // fall through
            }
        }

        // 4. Hysteria1 JSON if still empty
        if (proxies.isEmpty() && text.trim().startsWith("{")) {
            try {
                val hysteria = parseHysteria1Json(JSONObject(text))
                if (hysteria != null) {
                    proxies.add(hysteria)
                }
            } catch (e: Exception) {
                // fall through
            }
        }

        // 5. TrojanGo config
        if (proxies.isEmpty() && text.trim().startsWith("{")) {
            try {
                val trojanGo = parseTrojanGo(JSONObject(text))
                if (trojanGo != null) {
                    proxies.add(trojanGo)
                }
            } catch (e: Exception) {
                // fall through
            }
        }

        // 6. Shadowsocks SIP008 JSON
        if (proxies.isEmpty() && text.trim().startsWith("[")) {
            try {
                val array = JSONArray(text)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val ss = parseShadowsocks(obj)
                    if (ss != null) {
                        proxies.add(ss)
                    }
                }
            } catch (e: Exception) {
                // fall through
            }
        }

        // 7. INI format (some old subscription)
        if (proxies.isEmpty() && text.contains("[")) {
            try {
                val ini = Ini(StringReader(text))
                for (section in ini.keySet()) {
                    if (section.startsWith("Proxy")) {
                        val proxyMap = ini[section]
                        // parse proxy config from INI
                    }
                }
            } catch (e: Exception) {
                // fall through
            }
        }

        // 8. Fallback: each line as a URI
        if (proxies.isEmpty()) {
            val lines = text.lines().filter { it.isNotBlank() }
            for (line in lines) {
                try {
                    val bean = Protocols.parseUri(line.trim())
                    if (bean != null) {
                        proxies.add(bean)
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
        }

        return proxies.ifEmpty { null }
    }
}
