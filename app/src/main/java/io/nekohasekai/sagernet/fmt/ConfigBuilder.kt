package io.nekohasekai.sagernet.fmt

import io.nekohasekai.sagernet.IPv6Mode
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.bg.VpnService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_CONFIG
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.ConfigBuildResult.IndexEntity
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.fmt.hysteria.buildSingBoxOutboundHysteriaBean
import io.nekohasekai.sagernet.fmt.hysteria.isMultiPort
import io.nekohasekai.sagernet.fmt.internal.ChainBean
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocks.buildSingBoxOutboundShadowsocksBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.socks.buildSingBoxOutboundSocksBean
import io.nekohasekai.sagernet.fmt.ssh.SSHBean
import io.nekohasekai.sagernet.fmt.ssh.buildSingBoxOutboundSSHBean
import io.nekohasekai.sagernet.fmt.tuic.TuicBean
import moe.matsuri.nb4a.SingBoxOptions.*
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean
import io.nekohasekai.sagernet.fmt.v2ray.buildSingBoxOutboundStandardV2RayBean
import io.nekohasekai.sagernet.fmt.wireguard.WireGuardBean
import io.nekohasekai.sagernet.fmt.wireguard.buildSingBoxOutboundWireguardBean
import io.nekohasekai.sagernet.ktx.isIpAddress
import io.nekohasekai.sagernet.ktx.mkPort
import io.nekohasekai.sagernet.utils.PackageCache
import moe.matsuri.nb4a.DNS.applyDNSNetworkSettings
import moe.matsuri.nb4a.DNS.makeSingBoxRule
import moe.matsuri.nb4a.proxy.config.ConfigBean
import moe.matsuri.nb4a.plugin.Plugins
import moe.matsuri.nb4a.utils.JavaUtil.gson
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

const val TAG_MIXED = "mixed-in"
const val TAG_TRANS = "trans-in"

const val TAG_PROXY = "proxy"
const val TAG_DIRECT = "direct"
const val TAG_BYPASS = "bypass"
const val TAG_BLOCK = "block"

const val TAG_DNS_IN = "dns-in"
const val TAG_DNS_OUT = "dns-out"

const val LOCALHOST = "127.0.0.1"
const val LOCAL_DNS_SERVER = "underlying://0.0.0.0"

class ConfigBuildResult(
    var config: String,
    var externalIndex: List<IndexEntity>,
    var outboundTags: List<String>,
    var outboundTagMain: String,
    var trafficMap: Map<String, ProxyEntity>,
    val alerts: List<Pair<Int, String>>,
) {
    data class IndexEntity(var chain: LinkedHashMap<Int, ProxyEntity>)
}

fun mergeJSON(j: String, to: MutableMap<String, Any>) {
    if (j.isNullOrBlank()) return
    val m = gson.fromJson(j, to.javaClass)
    m.forEach { (k, v) ->
        if (v is Map<*, *> && to[k] is Map<*, *>) {
            val currentMap = (to[k] as Map<*, *>).toMutableMap()
            currentMap += v
            to[k] = currentMap
        } else {
            to[k] = v
        }
    }
}

fun buildConfig(
    proxy: ProxyEntity, forTest: Boolean = false
): ConfigBuildResult {

    if (proxy.type == TYPE_CONFIG) {
        val bean = proxy.requireBean() as ConfigBean
        if (bean.type == 0) {
            return ConfigBuildResult(
                bean.config,
                listOf(),
                listOf(TAG_PROXY), //
                TAG_PROXY, //
                mapOf(
                    TAG_PROXY to proxy
                ),
                listOf()
            )
        }
    }

    val outboundTags = ArrayList<String>()
    var outboundTagMain = TAG_BYPASS
    val trafficMap = HashMap<String, ProxyEntity>()
    val globalOutbounds = ArrayList<Long>()

    fun ProxyEntity.resolveChain(): MutableList<ProxyEntity> {
        val bean = requireBean()
        if (bean is ChainBean) {
            val beans = SagerDatabase.proxyDao.getEntities(bean.proxies)
            val beansMap = beans.associateBy { it.id }
            val beanList = ArrayList<ProxyEntity>()
            for (proxyId in bean.proxies) {
                val item = beansMap[proxyId] ?: continue
                beanList.addAll(item.resolveChain())
            }
            return beanList.asReversed()
        }
        return mutableListOf(this)
    }

    val proxies = proxy.resolveChain()
    val extraRules = if (forTest) listOf() else SagerDatabase.rulesDao.enabledRules()
    val extraProxies =
        if (forTest) mapOf() else SagerDatabase.proxyDao.getEntities(extraRules.mapNotNull { rule ->
            rule.outbound.takeIf { it > 0 && it != proxy.id }
        }.toHashSet().toList()).associate { it.id to it.resolveChain() }

    val uidListDNSRemote = mutableListOf<Int>()
    val uidListDNSDirect = mutableListOf<Int>()
    val domainListDNSRemote = mutableListOf<String>()
    val domainListDNSDirect = mutableListOf<String>()
    val domainListDNSBlock = mutableListOf<String>()
    val bypassDNSBeans = hashSetOf<AbstractBean>()
    val isVPN = DataStore.serviceMode == Key.MODE_VPN
    val bind = if (!forTest && DataStore.allowAccess) "0.0.0.0" else LOCALHOST
    val remoteDns = DataStore.remoteDns.split("\n")
        .mapNotNull { dns -> dns.trim().takeIf { it.isNotBlank() && !it.startsWith("#") } }
    var directDNS = DataStore.directDns.split("\n")
        .mapNotNull { dns -> dns.trim().takeIf { it.isNotBlank() && !it.startsWith("#") } }
    val enableDnsRouting = DataStore.enableDnsRouting
    val useFakeDns = DataStore.enableFakeDns && !forTest && DataStore.ipv6Mode != IPv6Mode.ONLY
    val needSniff = DataStore.trafficSniffing
    val externalIndexMap = ArrayList<IndexEntity>()
    val requireTransproxy = if (forTest) false else DataStore.requireTransproxy
    val ipv6Mode = if (forTest) IPv6Mode.ENABLE else DataStore.ipv6Mode
    val resolveDestination = DataStore.resolveDestination
    val alerts = mutableListOf<Pair<Int, String>>()

    var optionsToMerge: String = ""

    return MyOptions().apply {
        if (!forTest && DataStore.enableClashAPI) experimental = ExperimentalOptions().apply {
            clash_api = ClashAPIOptions().apply {
                external_controller = "127.0.0.1:9090"
                external_ui = "../files/yacd"
                cache_file = "../cache/clash.db"
            }
        }

        dns = DNSOptions().apply {
            // TODO nb4a hosts?
//            hosts = DataStore.hosts.split("\n")
//                .filter { it.isNotBlank() }
//                .associate { it.substringBefore(" ") to it.substringAfter(" ") }
//                .toMutableMap()

            servers = mutableListOf()
            rules = mutableListOf()

            when (ipv6Mode) {
                IPv6Mode.DISABLE -> {
                    strategy = "ipv4_only"
                }
                IPv6Mode.ONLY -> {
                    strategy = "ipv6_only"
                }
            }
        }

        inbounds = mutableListOf()

        if (!forTest) {
            if (isVPN) inbounds.add(Inbound_TunOptions().apply {
                type = "tun"
                tag = "tun-in"
                stack = if (DataStore.tunImplementation == 1) "system" else "gvisor"
                sniff = needSniff
                endpoint_independent_nat = true
                when (ipv6Mode) {
                    IPv6Mode.DISABLE -> {
                        inet4_address = listOf(VpnService.PRIVATE_VLAN4_CLIENT + "/28")
                    }
                    IPv6Mode.ONLY -> {
                        inet6_address = listOf(VpnService.PRIVATE_VLAN6_CLIENT + "/126")
                    }
                    else -> {
                        inet4_address = listOf(VpnService.PRIVATE_VLAN4_CLIENT + "/28")
                        inet6_address = listOf(VpnService.PRIVATE_VLAN6_CLIENT + "/126")
                    }
                }
            })
            inbounds.add(Inbound_MixedOptions().apply {
                type = "mixed"
                tag = TAG_MIXED
                listen = bind
                listen_port = DataStore.mixedPort
                if (needSniff) {
                    sniff = true
//                destOverride = when {
//                    useFakeDns && !trafficSniffing -> listOf("fakedns")
//                    useFakeDns -> listOf("fakedns", "http", "tls", "quic")
//                    else -> listOf("http", "tls", "quic")
//                }
//                metadataOnly = useFakeDns && !trafficSniffing
//                routeOnly = true
                }
            })
        }

        if (requireTransproxy) {
            if (DataStore.transproxyMode == 1) {
                inbounds.add(Inbound_TProxyOptions().apply {
                    type = "tproxy"
                    tag = TAG_TRANS
                    listen = bind
                    listen_port = DataStore.transproxyPort
                    sniff = needSniff
                })
            } else {
                inbounds.add(Inbound_RedirectOptions().apply {
                    type = "redirect"
                    tag = TAG_TRANS
                    listen = bind
                    listen_port = DataStore.transproxyPort
                    sniff = needSniff
                })
            }
        }

        outbounds = mutableListOf()

        // init routing object
        route = RouteOptions().apply {
            auto_detect_interface = true
            rules = mutableListOf()
        }

        // returns outbound tag
        fun buildChain(
            chainId: Long, profileList: List<ProxyEntity>
        ): String {
            var currentOutbound = mutableMapOf<String, Any>()
            lateinit var pastOutbound: MutableMap<String, Any>
            lateinit var pastInboundTag: String
            var pastEntity: ProxyEntity? = null
            val externalChainMap = LinkedHashMap<Int, ProxyEntity>()
            externalIndexMap.add(IndexEntity(externalChainMap))
            val chainOutbounds = ArrayList<MutableMap<String, Any>>()

            // chainTagOut: v2ray outbound tag for this chain
            var chainTagOut = ""
            var chainTag = "c-$chainId"
            var muxApplied = false

            fun genDomainStrategy(noAsIs: Boolean): String {
                return when {
                    !resolveDestination && !noAsIs -> ""
                    ipv6Mode == IPv6Mode.DISABLE -> "ipv4_only"
                    ipv6Mode == IPv6Mode.PREFER -> "prefer_ipv6"
                    ipv6Mode == IPv6Mode.ONLY -> "ipv6_only"
                    else -> "prefer_ipv4"
                }
            }

            var currentDomainStrategy = genDomainStrategy(false)

            profileList.forEachIndexed { index, proxyEntity ->
                val bean = proxyEntity.requireBean()

                // tagOut: v2ray outbound tag for a profile
                // profile2 (in) (global)   tag g-(id)
                // profile1                 tag (chainTag)-(id)
                // profile0 (out)           tag (chainTag)-(id) / single: "proxy"
                var tagOut = "$chainTag-${proxyEntity.id}"

                // needGlobal: can only contain one?
                var needGlobal = false

                // first profile set as global
                if (index == profileList.lastIndex) {
                    needGlobal = true
                    tagOut = "g-" + proxyEntity.id
                    bypassDNSBeans += proxyEntity.requireBean()
                }

                // last profile set as "proxy"
                if (chainId == 0L && index == 0) {
                    tagOut = TAG_PROXY
                }

                // chain rules
                if (index > 0) {
                    // chain route/proxy rules
                    if (pastEntity!!.needExternal()) {
                        route.rules.add(Rule_DefaultOptions().apply {
                            inbound = listOf(pastInboundTag)
                            outbound = tagOut
                        })
                    } else {
                        pastOutbound["detour"] = tagOut
                    }
                } else {
                    // index == 0 means last profile in chain / not chain
                    chainTagOut = tagOut
                    outboundTags.add(tagOut)
                    if (chainId == 0L) outboundTagMain = tagOut
                }

                if (needGlobal) {
                    if (globalOutbounds.contains(proxyEntity.id)) {
                        return@forEachIndexed
                    }
                    globalOutbounds.add(proxyEntity.id)
                }

                // include g-xx
                trafficMap[tagOut] = proxyEntity

                // Chain outbound
                if (proxyEntity.needExternal()) {
                    val localPort = mkPort()
                    externalChainMap[localPort] = proxyEntity
                    currentOutbound = Outbound_SocksOptions().apply {
                        type = "socks"
                        server = LOCALHOST
                        server_port = localPort
                    }.asMap()
                } else {
                    // internal outbound

                    currentOutbound = when (bean) {
                        is ConfigBean ->
                            gson.fromJson(bean.config, currentOutbound.javaClass)
                        is StandardV2RayBean ->
                            buildSingBoxOutboundStandardV2RayBean(bean).asMap()
                        is HysteriaBean ->
                            buildSingBoxOutboundHysteriaBean(bean).asMap()
                        is SOCKSBean ->
                            buildSingBoxOutboundSocksBean(bean).asMap()
                        is ShadowsocksBean ->
                            buildSingBoxOutboundShadowsocksBean(bean).asMap()
                        is WireGuardBean ->
                            buildSingBoxOutboundWireguardBean(bean).asMap()
                        is SSHBean ->
                            buildSingBoxOutboundSSHBean(bean).asMap()
                        else -> throw IllegalStateException("can't reach")
                    }

                    currentOutbound.apply {
                        // TODO nb4a keepAliveInterval?
//                        val keepAliveInterval = DataStore.tcpKeepAliveInterval
//                        val needKeepAliveInterval = keepAliveInterval !in intArrayOf(0, 15)

                        if (!muxApplied && proxyEntity.needCoreMux()) {
                            muxApplied = true
                            currentOutbound["multiplex"] = MultiplexOptions().apply {
                                enabled = true
                                max_streams = DataStore.muxConcurrency
                            }
                        }
                    }

                    // custom JSON merge
                    if (bean.customOutboundJson.isNotBlank()) {
                        mergeJSON(bean.customOutboundJson, currentOutbound)
                    }
                    if (index == 0 && bean.customConfigJson.isNotBlank()) {
                        optionsToMerge = bean.customConfigJson
                    }

                }

                pastEntity?.requireBean()?.apply {
                    // don't loopback
                    if (currentDomainStrategy != "" && !serverAddress.isIpAddress()) {
                        domainListDNSDirect.add("full:$serverAddress")
                    }
                }
                if (forTest) {
                    currentDomainStrategy = ""
                }

                currentOutbound["tag"] = tagOut
                currentOutbound["domain_strategy"] = currentDomainStrategy

                // External proxy need a dokodemo-door inbound to forward the traffic
                // For external proxy software, their traffic must goes to v2ray-core to use protected fd.
                if (bean.canMapping() && proxyEntity.needExternal()) {
                    // With ss protect, don't use mapping
                    var needExternal = true
                    if (index == profileList.lastIndex) {
                        val pluginId = when (bean) {
                            is HysteriaBean -> "hysteria-plugin"
                            is TuicBean -> "tuic-plugin"
                            else -> ""
                        }
                        if (Plugins.isUsingMatsuriExe(pluginId)) {
                            needExternal = false
                        } else if (bean is HysteriaBean) {
                            throw Exception("not supported hysteria-plugin (SagerNet)")
                        }
                    }
                    if (needExternal) {
                        val mappingPort = mkPort()
                        bean.finalAddress = LOCALHOST
                        bean.finalPort = mappingPort

                        inbounds.add(Inbound_DirectOptions().apply {
                            type = "direct"
                            listen = LOCALHOST
                            listen_port = mappingPort
                            tag = "$chainTag-mapping-${proxyEntity.id}"

                            override_address = bean.serverAddress
                            override_port = bean.serverPort

                            pastInboundTag = tag

                            // no chain rule and not outbound, so need to set to direct
                            if (index == profileList.lastIndex) {
                                route.rules.add(Rule_DefaultOptions().apply {
                                    inbound = listOf(tag)
                                    outbound = TAG_DIRECT
                                })
                            }
                        })
                    }
                }

                outbounds.add(currentOutbound)
                chainOutbounds.add(currentOutbound)
                pastOutbound = currentOutbound
                pastEntity = proxyEntity
            }

            return chainTagOut
        }

        val tagProxy = buildChain(0, proxies)
        val tagMap = mutableMapOf<Long, String>()
        extraProxies.forEach { (key, entities) ->
            tagMap[key] = buildChain(key, entities)
        }

        // apply user rules
        for (rule in extraRules) {
            val _uidList = rule.packages.map {
                PackageCache[it]?.takeIf { uid -> uid >= 1000 }
            }.toHashSet().filterNotNull()

            if (rule.packages.isNotEmpty()) {
                if (!isVPN) {
                    alerts.add(0 to rule.displayName())
                    continue
                }
            }
            route.rules.add(Rule_DefaultOptions().apply {
                if (rule.packages.isNotEmpty()) {
                    PackageCache.awaitLoadSync()
                    user_id = _uidList
                }

                var _domainList: List<String>? = null
                if (rule.domains.isNotBlank()) {
                    _domainList = rule.domains.split("\n")
                    makeSingBoxRule(_domainList, false)
                }
                if (rule.ip.isNotBlank()) {
                    makeSingBoxRule(rule.ip.split("\n"), true)
                }
                if (rule.port.isNotBlank()) {
                    port = rule.port.split("\n").map { it.toIntOrNull() ?: 0 }
                }
                if (rule.sourcePort.isNotBlank()) {
                    source_port = rule.sourcePort.split("\n").map { it.toIntOrNull() ?: 0 }
                }
                if (rule.network.isNotBlank()) {
                    network = rule.network
                }
                if (rule.source.isNotBlank()) {
                    source_ip_cidr = rule.source.split("\n")
                }
                if (rule.protocol.isNotBlank()) {
                    protocol = rule.protocol.split("\n")
                }

                // also bypass lookup
                // cannot use other outbound profile to lookup...
                if (rule.outbound == -1L) {
                    uidListDNSDirect += _uidList
                    if (_domainList != null) domainListDNSDirect += _domainList
                } else if (rule.outbound == 0L) {
                    uidListDNSRemote += _uidList
                    if (_domainList != null) domainListDNSRemote += _domainList
                } else if (rule.outbound == -2L) {
                    if (_domainList != null) domainListDNSBlock += _domainList
                }

                outbound = when (val outId = rule.outbound) {
                    0L -> tagProxy
                    -1L -> TAG_BYPASS
                    -2L -> TAG_BLOCK
                    else -> if (outId == proxy.id) tagProxy else tagMap[outId]
                        ?: throw Exception("invalid rule")
                }
            })
        }

        for (freedom in arrayOf(TAG_DIRECT, TAG_BYPASS)) outbounds.add(Outbound().apply {
            tag = freedom
            type = "direct"
        }.asMap())

        outbounds.add(Outbound().apply {
            tag = TAG_BLOCK
            type = "block"
        }.asMap())

        if (!forTest) {
            inbounds.add(0, Inbound_DirectOptions().apply {
                type = "direct"
                tag = TAG_DNS_IN
                listen = bind
                listen_port = DataStore.localDNSPort
                override_address = if (!remoteDns.first().isIpAddress()) {
                    "8.8.8.8"
                } else {
                    remoteDns.first()
                }
                override_port = 53
            })

            outbounds.add(Outbound().apply {
                type = "dns"
                tag = TAG_DNS_OUT
            }.asMap())
        }

        if (DataStore.directDnsUseSystem) {
            // finally able to use "localDns" now...
            directDNS = listOf(LOCAL_DNS_SERVER)
        }

        // routing for DNS server
        for (dns in remoteDns) {
            if (!dns.isIpAddress()) continue
            route.rules.add(Rule_DefaultOptions().apply {
                outbound = tagProxy
                ip_cidr = listOf(dns)
            })
        }

        for (dns in directDNS) {
            if (!dns.isIpAddress()) continue
            route.rules.add(Rule_DefaultOptions().apply {
                outbound = TAG_DIRECT
                ip_cidr = listOf(dns)
            })
        }

        // Bypass Lookup for the first profile
        bypassDNSBeans.forEach {
            var serverAddr = it.serverAddress
            if (it is HysteriaBean && it.isMultiPort()) {
                serverAddr = it.serverAddress.substringBeforeLast(":")
            }

            if (!serverAddr.isIpAddress()) {
                domainListDNSDirect.add("full:${serverAddr}")
            }
        }

        remoteDns.forEach {
            var address = it
            if (address.contains("://")) {
                address = address.substringAfter("://")
            }
            "https://$address".toHttpUrlOrNull()?.apply {
                if (!host.isIpAddress()) {
                    domainListDNSDirect.add("full:$host")
                }
            }
        }

        // remote dns obj
        remoteDns.firstOrNull()?.apply {
            val d = this
            dns.servers.add(DNSServerOptions().apply {
                address = d
                tag = "dns-remote"
                address_resolver = "dns-direct"
                applyDNSNetworkSettings(false)
            })
        }

        // add directDNS objects here
        directDNS.firstOrNull()?.apply {
            val d = this
            dns.servers.add(DNSServerOptions().apply {
                address = d
                tag = "dns-direct"
                detour = "direct"
                address_resolver = "dns-local"
                applyDNSNetworkSettings(true)
            })
        }
        dns.servers.add(DNSServerOptions().apply {
            address = LOCAL_DNS_SERVER
            tag = "dns-local"
            detour = "direct"
        })
        dns.servers.add(DNSServerOptions().apply {
            address = "rcode://success"
            tag = "dns-block"
        })

        // dns object user rules
        if (enableDnsRouting) {
            if (domainListDNSRemote.isNotEmpty() || uidListDNSRemote.isNotEmpty()) {
                dns.rules.add(
                    DNSRule_DefaultOptions().apply {
                        makeSingBoxRule(domainListDNSRemote.toHashSet().toList())
                        user_id = uidListDNSRemote.toHashSet().toList()
                        server = "dns-remote"
                    }
                )
            }
            if (domainListDNSDirect.isNotEmpty() || uidListDNSDirect.isNotEmpty()) {
                dns.rules.add(
                    DNSRule_DefaultOptions().apply {
                        makeSingBoxRule(domainListDNSDirect.toHashSet().toList())
                        user_id = uidListDNSDirect.toHashSet().toList()
                        server = "dns-direct"
                    }
                )
            }
        }
        if (domainListDNSBlock.isNotEmpty()) {
            dns.rules.add(
                DNSRule_DefaultOptions().apply {
                    makeSingBoxRule(domainListDNSBlock.toHashSet().toList())
                    server = "dns-block"
                }
            )
        }

        // Disable DNS for test
        if (forTest) {
            dns.servers.clear()
            dns.rules.clear()
        }

        if (!forTest) {
            route.rules.add(Rule_DefaultOptions().apply {
                inbound = listOf(TAG_DNS_IN)
                outbound = TAG_DNS_OUT
            })
            route.rules.add(Rule_DefaultOptions().apply {
                port = listOf(53)
                outbound = TAG_DNS_OUT
            }) // TODO new mode use system dns?
            if (DataStore.bypassLan && DataStore.bypassLanInCoreOnly) {
                route.rules.add(Rule_DefaultOptions().apply {
                    outbound = TAG_BYPASS
                    geoip = listOf("private")
                })
            }
            // block mcast
            route.rules.add(Rule_DefaultOptions().apply {
                ip_cidr = listOf("224.0.0.0/3", "ff00::/8")
                source_ip_cidr = listOf("224.0.0.0/3", "ff00::/8")
                outbound = TAG_BLOCK
            })
            dns.rules.add(DNSRule_DefaultOptions().apply {
                domain_suffix = listOf(".arpa.", ".arpa")
                server = "dns-block"
            })
        }

        // fakedns obj
        if (useFakeDns) {
            dns.servers.add(DNSServerOptions().apply {
                address = "fakedns://" + VpnService.FAKEDNS_VLAN4_CLIENT + "/15"
                tag = "dns-fake"
                strategy = "ipv4_only"
            })
            dns.rules.add(DNSRule_DefaultOptions().apply {
                inbound = listOf("tun-in")
                server = "dns-fake"
            })
        }
    }.let {
        ConfigBuildResult(
            gson.toJson(it.asMap().apply {
                mergeJSON(optionsToMerge, this)
            }),
            externalIndexMap,
            outboundTags,
            outboundTagMain,
            trafficMap,
            alerts
        )
    }

}
