package moe.matsuri.nb4a

import io.nekohasekai.sagernet.database.DataStore

object DNS {
    fun SingBoxOptions.DNSServerOptions.applyDNSNetworkSettings(isDirect: Boolean) {
        if (isDirect) {
            if (DataStore.dnsNetwork.contains("NoDirectIPv4")) this.strategy = "ipv6_only"
            if (DataStore.dnsNetwork.contains("NoDirectIPv6")) this.strategy = "ipv4_only"
        } else {
            if (DataStore.dnsNetwork.contains("NoRemoteIPv4")) this.strategy = "ipv6_only"
            if (DataStore.dnsNetwork.contains("NoRemoteIPv6")) this.strategy = "ipv4_only"
        }
    }

    fun SingBoxOptions.DNSRule_DefaultOptions.makeSingBoxRule(list: List<String>) {
        geosite = mutableListOf<String>()
        domain = mutableListOf<String>()
        domain_suffix = mutableListOf<String>()
        domain_regex = mutableListOf<String>()
        domain_keyword = mutableListOf<String>()
        list.forEach {
            if (it.startsWith("geosite:")) {
                geosite.plusAssign(it.removePrefix("geosite:"))
            } else if (it.startsWith("full:")) {
                domain.plusAssign(it.removePrefix("full:"))
            } else if (it.startsWith("domain:")) {
                domain_suffix.plusAssign(it.removePrefix("domain:"))
            } else if (it.startsWith("regexp:")) {
                domain_regex.plusAssign(it.removePrefix("regexp:"))
            } else if (it.startsWith("keyword:")) {
                domain_keyword.plusAssign(it.removePrefix("keyword:"))
            } else {
                domain.plusAssign(it)
            }
        }
        if (geosite?.isEmpty() == true) geosite = null
        if (domain?.isEmpty() == true) domain = null
        if (domain_suffix?.isEmpty() == true) domain_suffix = null
        if (domain_regex?.isEmpty() == true) domain_regex = null
        if (domain_keyword?.isEmpty() == true) domain_keyword = null
    }

    fun SingBoxOptions.Rule_DefaultOptions.makeSingBoxRule(list: List<String>, isIP: Boolean) {
        if (isIP) {
            ip_cidr = mutableListOf<String>()
            geoip = mutableListOf<String>()
        } else {
            geosite = mutableListOf<String>()
            domain = mutableListOf<String>()
            domain_suffix = mutableListOf<String>()
            domain_regex = mutableListOf<String>()
            domain_keyword = mutableListOf<String>()
        }
        list.forEach {
            if (isIP) {
                if (it.startsWith("geoip:")) {
                    geoip.plusAssign(it.removePrefix("geoip:"))
                } else {
                    ip_cidr.plusAssign(it)
                }
                return@forEach
            }
            if (it.startsWith("geosite:")) {
                geosite.plusAssign(it.removePrefix("geosite:"))
            } else if (it.startsWith("full:")) {
                domain.plusAssign(it.removePrefix("full:"))
            } else if (it.startsWith("domain:")) {
                domain_suffix.plusAssign(it.removePrefix("domain:"))
            } else if (it.startsWith("regexp:")) {
                domain_regex.plusAssign(it.removePrefix("regexp:"))
            } else if (it.startsWith("keyword:")) {
                domain_keyword.plusAssign(it.removePrefix("keyword:"))
            } else {
                domain.plusAssign(it)
            }
        }
        if (ip_cidr?.isEmpty() == true) ip_cidr = null
        if (geoip?.isEmpty() == true) geoip = null
        if (geosite?.isEmpty() == true) geosite = null
        if (domain?.isEmpty() == true) domain = null
        if (domain_suffix?.isEmpty() == true) domain_suffix = null
        if (domain_regex?.isEmpty() == true) domain_regex = null
        if (domain_keyword?.isEmpty() == true) domain_keyword = null
    }
}
