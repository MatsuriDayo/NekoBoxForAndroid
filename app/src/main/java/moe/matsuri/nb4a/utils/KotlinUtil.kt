package moe.matsuri.nb4a.utils

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.ktx.Logs
import java.io.File

// SagerNet Class

fun SagerNet.cleanWebview() {
    var pathToClean = "app_webview"
    if (isBgProcess) pathToClean += "_$process"
    try {
        val dataDir = filesDir.parentFile!!
        File(dataDir, "$pathToClean/BrowserMetrics").recreate(true)
        File(dataDir, "$pathToClean/BrowserMetrics-spare.pma").recreate(false)
    } catch (e: Exception) {
        Logs.e(e)
    }
}

fun File.recreate(dir: Boolean) {
    if (parentFile?.isDirectory != true) return
    if (dir && !isFile) {
        if (exists()) deleteRecursively()
        createNewFile()
    } else if (!dir && !isDirectory) {
        if (exists()) delete()
        mkdir()
    }
}

// Context utils

fun Context.getDrawableByName(name: String?): Drawable? {
    val resourceId: Int = resources.getIdentifier(name, "drawable", packageName)
    return AppCompatResources.getDrawable(this, resourceId)
}

// Traffic display

fun Long.toBytesString(): String {
    return when {
        this > 1024 * 1024 * 1024 -> String.format(
            "%.2f GiB", (this.toDouble() / 1024 / 1024 / 1024)
        )
        this > 1024 * 1024 -> String.format("%.2f MiB", (this.toDouble() / 1024 / 1024))
        this > 1024 -> String.format("%.2f KiB", (this.toDouble() / 1024))
        else -> "$this Bytes"
    }
}
