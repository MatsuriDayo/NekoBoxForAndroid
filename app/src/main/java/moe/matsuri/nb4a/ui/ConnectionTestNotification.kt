package moe.matsuri.nb4a.ui

import android.content.Context
import androidx.core.app.NotificationCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.ktx.Logs

class ConnectionTestNotification(val context: Context, val title: String) {
    private val channelId = "connection-test"
    private val notificationId = 1001

    fun updateNotification(progress: Int, max: Int, finished: Boolean) {
        try {
            if (finished) {
                SagerNet.notification.cancel(notificationId)
                return
            }
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_service_active)
                .setContentTitle(title)
                .setOnlyAlertOnce(true)
                .setContentText("$progress / $max").setProgress(max, progress, false)
            SagerNet.notification.notify(notificationId, builder.build())
        } catch (e: Exception) {
            Logs.w(e)
        }
    }
}