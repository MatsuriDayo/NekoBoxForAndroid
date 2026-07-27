package io.nekohasekai.sagernet.fmt

import io.nekohasekai.sagernet.database.DataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object ProxyDecorator {

    private val flagCache = ConcurrentHashMap<String, String>()
    private val geminiCache = ConcurrentHashMap<String, Boolean>()

    private fun getProxyHttpClient(localProxyPort: Int): OkHttpClient {
        return OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", localProxyPort)))
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    /**
     * دکوریشن روی AbstractBean (برای استفاده در RawUpdater)
     */
    suspend fun decorateBeanWithFlag(
        bean: AbstractBean,
        localProxyPort: Int = DataStore.mixedPort
    ) = withContext(Dispatchers.IO) {
        try {
            val serverKey = bean.serverAddress.trim()
            if (serverKey.isEmpty()) return@withContext

            val flagEmoji = flagCache[serverKey] ?: run {
                val client = getProxyHttpClient(localProxyPort)
                val request = Request.Builder()
                    .url("http://ip-api.com/json/?fields=status,countryCode")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@run null
                    val bodyString = response.body?.string() ?: return@run null
                    val json = JSONObject(bodyString)
                    if (json.optString("status") != "success") return@run null
                    val countryCode = json.optString("countryCode")
                    val resolvedFlag = countryCodeToEmojiFlag(countryCode)
                    if (resolvedFlag.isNotEmpty()) {
                        flagCache[serverKey] = resolvedFlag
                        resolvedFlag
                    } else null
                }
            }

            if (!flagEmoji.isNullOrEmpty()) {
                val currentName = bean.displayName()
                if (!currentName.contains(flagEmoji)) {
                    bean.name = "$flagEmoji $currentName"
                }
            }
        } catch (_: Exception) {
            // Fail silently
        }
    }

    suspend fun decorateBeanWithGemini(
        bean: AbstractBean,
        localProxyPort: Int = DataStore.mixedPort
    ) = withContext(Dispatchers.IO) {
        try {
            val serverKey = bean.serverAddress.trim()
            if (serverKey.isEmpty()) return@withContext

            val isGeminiAvailable = geminiCache[serverKey] ?: run {
                val client = getProxyHttpClient(localProxyPort)
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/\$discovery/rest?version=v1beta")
                    .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                    .build()

                client.newCall(request).execute().use { response ->
                    val isSuccess = response.isSuccessful || response.code == 200
                    geminiCache[serverKey] = isSuccess
                    isSuccess
                }
            }

            if (isGeminiAvailable) {
                val marker = "[Gemini]"
                val currentName = bean.displayName()
                if (!currentName.contains(marker)) {
                    bean.name = "$currentName $marker"
                }
            }
        } catch (_: Exception) {
            // Fail silently
        }
    }

    private fun countryCodeToEmojiFlag(countryCode: String): String {
        if (countryCode.length != 2) return ""
        val uppercase = countryCode.uppercase()
        val firstChar = Character.toChars(0x1F1E6 + uppercase[0].code - 'A'.code)
        val secondChar = Character.toChars(0x1F1E6 + uppercase[1].code - 'A'.code)
        return String(firstChar) + String(secondChar)
    }
}
