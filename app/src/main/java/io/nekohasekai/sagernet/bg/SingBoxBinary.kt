package io.nekohasekai.sagernet.bg

import android.content.Context
import android.util.Log
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.database.DataStore
import java.io.File

/**
 * Provides the standalone sing-box binary (libsingbox.so) for root redir/tproxy.
 *
 * Packaged under app/executableSo (jniLibs) so each ABI-split APK only has one arch.
 * At runtime we **copy** it from nativeLibraryDir into filesDir/bin, because executing
 * directly from the app lib directory is often blocked by SELinux (su exits with 1).
 */
object SingBoxBinary {
    private const val TAG = "SingBoxBinary"
    private const val BIN_NAME = "libsingbox.so"

    fun isStandaloneMode(): Boolean {
        val mode = DataStore.serviceMode
        return mode == Key.MODE_REDIR || mode == Key.MODE_TPROXY
    }

    /**
     * Ensure an executable copy exists under filesDir/bin and return it.
     */
    fun ensureBinary(context: Context): File {
        val native = File(context.applicationInfo.nativeLibraryDir, BIN_NAME)
        if (!native.exists() || native.length() == 0L) {
            throw IllegalStateException(
                "Missing $BIN_NAME in nativeLibraryDir (${context.applicationInfo.nativeLibraryDir}). " +
                    "Rebuild with app.yml placing so under app/executableSo/{abi}/."
            )
        }

        val outDir = File(context.filesDir, "bin").apply { mkdirs() }
        val out = File(outDir, BIN_NAME)

        // Re-copy when missing or size changed (upgrade)
        if (!out.exists() || out.length() != native.length()) {
            native.inputStream().use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            Log.i(TAG, "Copied $BIN_NAME -> ${out.absolutePath} (${out.length()} bytes)")
        }

        // Make executable for owner and others (root via su needs x)
        out.setReadable(true, false)
        out.setExecutable(true, false)
        // Best-effort chmod 755 via Os if available
        try {
            android.system.Os.chmod(out.absolutePath, 493) // 0755
        } catch (_: Exception) {
        }

        if (!out.canExecute()) {
            Log.w(TAG, "Binary may not be executable: ${out.absolutePath}")
        }
        return out
    }
}
