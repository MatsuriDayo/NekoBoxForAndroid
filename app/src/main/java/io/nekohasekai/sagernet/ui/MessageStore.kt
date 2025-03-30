package io.nekohasekai.sagernet.ui

import android.app.Activity
import android.view.View
import androidx.annotation.StringRes
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.ktx.Logs
import java.lang.ref.WeakReference

object MessageStore {
    private var currentActivity: WeakReference<Activity>? = null
    private var snackbar: Snackbar? = null

    fun setCurrentActivity(activity: Activity) {
        currentActivity = WeakReference(activity)
    }

    fun showMessage(message: String) {
        val activity = currentActivity?.get() ?: return
        try {
            val rootView = activity.window.decorView.findViewById<View>(android.R.id.content)
            snackbar?.dismiss()
            snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG)
            snackbar?.show()
        } catch (e: Exception) {
            Logs.w("Failed to show snackbar", e)
        }
    }

    fun showMessage(activity: Activity, @StringRes resId: Int) {
        showMessage(activity.getString(resId))
    }

    fun showMessage(activity: Activity, message: String) {
        showMessage(message)
    }

    fun showMessage(activity: Activity, @StringRes resId: Int, vararg formatArgs: Any) {
        showMessage(activity.getString(resId, *formatArgs))
    }
} 
