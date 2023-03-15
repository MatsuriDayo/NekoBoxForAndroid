package io.nekohasekai.sagernet.plugin

import android.content.pm.ComponentInfo
import android.content.pm.ProviderInfo
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.ktx.Logs
import moe.matsuri.nb4a.plugin.Plugins
import java.io.File
import java.io.FileNotFoundException

object PluginManager {

    class PluginNotFoundException(val plugin: String) : FileNotFoundException(plugin),
        BaseService.ExpectedException {
        override fun getLocalizedMessage() =
            SagerNet.application.getString(R.string.plugin_unknown, plugin)
    }

    data class InitResult(
        val path: String,
        val info: ProviderInfo,
    )

    @Throws(Throwable::class)
    fun init(pluginId: String): InitResult? {
        if (pluginId.isEmpty()) return null
        var throwable: Throwable? = null

        try {
            val result = initNative(pluginId)
            if (result != null) return result
        } catch (t: Throwable) {
            if (throwable == null) throwable = t else Logs.w(t)
        }

        throw throwable ?: PluginNotFoundException(pluginId)
    }

    private fun initNative(pluginId: String): InitResult? {
        val info = Plugins.getPlugin(pluginId) ?: return null

        try {
            initNativeFaster(info)?.also { return InitResult(it, info) }
        } catch (t: Throwable) {
            Logs.w("Initializing native plugin faster mode failed", t)
        }

        Logs.w("Init native returns empty result")
        return null
    }

    private fun initNativeFaster(provider: ProviderInfo): String? {
        return provider.loadString(Plugins.METADATA_KEY_EXECUTABLE_PATH)
            ?.let { relativePath ->
                File(provider.applicationInfo.nativeLibraryDir).resolve(relativePath).apply {
                    check(canExecute())
                }.absolutePath
            }
    }

    fun ComponentInfo.loadString(key: String) = when (val value = metaData.get(key)) {
        is String -> value
        is Int -> SagerNet.application.packageManager.getResourcesForApplication(applicationInfo)
            .getString(value)
        null -> null
        else -> error("meta-data $key has invalid type ${value.javaClass}")
    }
}
