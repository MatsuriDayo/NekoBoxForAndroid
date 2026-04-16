package io.nekohasekai.sagernet.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.DataStore

class ProxyToggleWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "io.nekohasekai.sagernet.WIDGET_TOGGLE"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, ProxyToggleWidget::class.java)
            )
            if (ids == null || ids.isEmpty()) return
            val intent = Intent(context, ProxyToggleWidget::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE) {
            handleToggle()
            return
        }
        super.onReceive(context, intent)
    }

    private fun handleToggle() {
        val state = DataStore.serviceState
        when {
            state.canStop -> SagerNet.stopService()
            state == BaseService.State.Stopped || state == BaseService.State.Idle ->
                SagerNet.startService()
        }
    }

    private fun updateWidget(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_proxy_toggle)
        val state = DataStore.serviceState

        val bgRes = when {
            state == BaseService.State.Connected -> R.drawable.widget_background_connected
            state == BaseService.State.Connecting || state == BaseService.State.Stopping ->
                R.drawable.widget_background_busy
            else -> R.drawable.widget_background
        }
        views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)

        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val toggleIntent = Intent(context, ProxyToggleWidget::class.java)
            .setAction(ACTION_TOGGLE)
        val pendingIntent = PendingIntent.getBroadcast(context, 0, toggleIntent, pendingFlags)
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        manager.updateAppWidget(widgetId, views)
    }
}
