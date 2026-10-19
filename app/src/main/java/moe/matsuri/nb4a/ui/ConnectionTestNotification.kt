package moe.matsuri.nb4a.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                SagerNet.notification.notify(notificationId, builder.build())
            }
        } catch (e: Exception) {
            Logs.w(e)
        }
    }
}
