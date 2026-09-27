package io.nekohasekai.sagernet.bg

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteWorkManager
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.app
import java.io.File
import java.util.concurrent.TimeUnit

object SrsRuleSetUpdater {

    private const val WORK_NAME = "SrsRuleSetUpdater"
    /** 默认间隔：24 小时 */
    private const val INTERVAL_HOURS = 24L

    suspend fun reconfigureUpdater() {
        val wm = RemoteWorkManager.getInstance(app)
        wm.cancelUniqueWork(WORK_NAME)

        val needUpdate = SagerDatabase.rulesDao.allRules().any {
            it.srsAutoUpdate && it.srsUrl.isNotBlank() && it.srsName.isNotBlank()
        }
        if (!needUpdate) return

        wm.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequest.Builder(
                UpdateTask::class.java,
                INTERVAL_HOURS,
                TimeUnit.HOURS
            ).build()
        )
    }

    class UpdateTask(
        context: Context,
        params: WorkerParameters
    ) : CoroutineWorker(context, params) {

        override suspend fun doWork(): Result {
            val now = System.currentTimeMillis() / 1000L
            val intervalSec = INTERVAL_HOURS * 3600

            val rules = SagerDatabase.rulesDao.allRules().filter {
                it.srsAutoUpdate &&
                    it.srsUrl.isNotBlank() &&
                    it.srsName.isNotBlank() &&
                    (now - it.srsLastUpdated) >= intervalSec
            }

            for (rule in rules) {
                try {
                    Logs.d("SrsRuleSetUpdater: updating ${rule.srsName}")
                    download(rule.srsName, rule.srsUrl)
                    rule.srsLastUpdated = now
                    SagerDatabase.rulesDao.updateRule(rule)
                } catch (e: Exception) {
                    Logs.e(e)
                }
            }
            return Result.success()
        }

        private fun download(srsName: String, srsUrl: String) {
            val outFile = File(SagerNet.application.externalAssets, srsName)
            outFile.parentFile?.mkdirs()

            val client = libcore.Libcore.newHttpClient().apply {
                modernTLS()
                keepAlive()
                trySocks5(DataStore.mixedPort)
            }
            try {
                val response = client.newRequest().apply { setURL(srsUrl) }.execute()
                val tmp = File(outFile.parentFile, "$srsName.tmp")
                response.writeTo(tmp.canonicalPath)
                tmp.renameTo(outFile)
            } finally {
                client.close()
            }
        }
    }
}
