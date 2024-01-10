package io.nekohasekai.sagernet.fmt

import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet

enum class PluginEntry(
    val pluginId: String,
    val displayName: String,
    val packageName: String, // for play and f-droid page
    val downloadSource: DownloadSource = DownloadSource()
) {
    TrojanGo(
        "trojan-go-plugin",
        SagerNet.application.getString(R.string.action_trojan_go),
        "io.nekohasekai.sagernet.plugin.trojan_go"
    ),
    MieruProxy(
        "mieru-plugin",
        SagerNet.application.getString(R.string.action_mieru),
        "moe.matsuri.exe.mieru",
        DownloadSource(
            playStore = false,
            fdroid = false,
            downloadLink = "https://github.com/MatsuriDayo/plugins/releases?q=mieru"
        )
    ),
    NaiveProxy(
        "naive-plugin",
        SagerNet.application.getString(R.string.action_naive),
        "moe.matsuri.exe.naive",
        DownloadSource(
            playStore = false,
            fdroid = false,
            downloadLink = "https://github.com/MatsuriDayo/plugins/releases?q=naive"
        )
    ),
    Hysteria(
        "hysteria-plugin",
        SagerNet.application.getString(R.string.action_hysteria),
        "moe.matsuri.exe.hysteria",
        DownloadSource(
            playStore = false,
            fdroid = false,
            downloadLink = "https://github.com/MatsuriDayo/plugins/releases?q=Hysteria"
        )
    ),
    ;

    data class DownloadSource(
        val playStore: Boolean = true,
        val fdroid: Boolean = true,
        val downloadLink: String = "https://matsuridayo.github.io/"
    )

    companion object {

        fun find(name: String): PluginEntry? {
            for (pluginEntry in enumValues<PluginEntry>()) {
                if (name == pluginEntry.pluginId) {
                    return pluginEntry
                }
            }
            return null
        }

    }

}