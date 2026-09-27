package io.nekohasekai.sagernet.bg

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import io.nekohasekai.sagernet.ktx.Logs
import java.io.File
import java.io.IOException
import androidx.core.text.isDigitsOnly

object Executable {
    private val EXECUTABLES = setOf(
        "libtrojan.so", "libtrojan-go.so", "libnaive.so", "libtuic.so", "libhysteria.so",
        "libsingbox.so"
    )

    fun killAll(alsoKillBg: Boolean = false) {
        val rootPidsKilled = mutableListOf<Int>()

        for (process in File("/proc").listFiles { _, name -> name.isDigitsOnly() } ?: return) {
            val exe = File(
                try {
                    File(process, "cmdline").inputStream().bufferedReader().use { it.readText() }
                } catch (_: IOException) {
                    continue
                }.split(Character.MIN_VALUE, limit = 2).first()
            )
            if (!EXECUTABLES.contains(exe.name) && !(alsoKillBg && exe.name.endsWith(":bg"))) continue

            val pid = process.name.toInt()
            try {
                Os.kill(pid, OsConstants.SIGKILL)
                Logs.w("SIGKILL ${exe.name} ($pid) succeed")
            } catch (e: ErrnoException) {
                when (e.errno) {
                    OsConstants.EPERM -> {
                        // root-owned process (standalone redir/tproxy mode) — use `su -c kill`
                        try {
                            Runtime.getRuntime()
                                .exec(arrayOf("su", "-c", "kill -KILL $pid"))
                                .waitFor()
                            Logs.w("su SIGKILL ${exe.name} ($pid) sent")
                            rootPidsKilled += pid
                        } catch (ex: Exception) {
                            Logs.w("su SIGKILL ${exe.name} ($pid) failed: ${ex.message}")
                        }
                    }
                    OsConstants.ESRCH -> { /* already gone */ }
                    else -> {
                        Logs.w("SIGKILL ${exe.absolutePath} ($pid) failed")
                        Logs.w(e)
                    }
                }
            }
        }

        // Wait for root-owned processes to actually disappear before returning,
        // so the new sing-box process won't collide on the same port.
        if (rootPidsKilled.isNotEmpty()) {
            val deadline = System.currentTimeMillis() + 2_000L
            val remaining = rootPidsKilled.toMutableList()
            while (remaining.isNotEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(100)
                remaining.removeAll { pid ->
                    val alive = File("/proc/$pid").exists()
                    if (!alive) Logs.i("process $pid exited")
                    !alive
                }
            }
            if (remaining.isNotEmpty()) {
                Logs.w("processes still alive after 2s: $remaining")
            }
        }
    }
}
