package io.nekohasekai.sagernet.database

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

internal object RemoteRuleSetDownload {
    fun fetch(client: OkHttpClient, url: String, cache: File, validate: (File) -> Unit, rename: (File, File) -> Unit) {
        // A subscription response must not redirect a direct-network request to another target.
        val direct = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
        direct.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw RemoteRuleSetManager.DownloadFailure("HTTP ${response.code}")
            val body = response.body ?: throw RemoteRuleSetManager.DownloadFailure("Empty HTTP response")
            body.byteStream().use { RemoteRuleSetStore.replace(cache, it, validate, rename) }
        }
    }
}
