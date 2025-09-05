@file:SuppressLint("SoonBlockedPrivateApi")

package io.nekohasekai.sagernet.ktx

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.os.Build
import android.system.Os
import android.system.OsConstants
import android.util.TypedValue
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.AttrRes
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.jakewharton.processphoenix.ProcessPhoenix
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.ThemedActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import moe.matsuri.nb4a.utils.NGUtil
import java.io.FileDescriptor
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Socket
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0

fun String?.blankAsNull(): String? = if (isNullOrBlank()) null else this

inline fun <T> Iterable<T>.forEachTry(action: (T) -> Unit) {
    var result: Exception? = null
    for (element in this) try {
        action(element)
    } catch (e: Exception) {
        if (result == null) result = e else result.addSuppressed(e)
    }
    if (result != null) {
        throw result
    }
}

val Throwable.readableMessage
    get() = localizedMessage.takeIf { !it.isNullOrBlank() } ?: javaClass.simpleName

/**
 * https://android.googlesource.com/platform/prebuilts/runtime/+/94fec32/appcompat/hiddenapi-light-greylist.txt#9466
 */

private val socketGetFileDescriptor = Socket::class.java.getDeclaredMethod("getFileDescriptor\$")
val Socket.fileDescriptor get() = socketGetFileDescriptor.invoke(this) as FileDescriptor

private val getInt = FileDescriptor::class.java.getDeclaredMethod("getInt$")
val FileDescriptor.int get() = getInt.invoke(this) as Int

suspend fun <T> HttpURLConnection.useCancellable(block: suspend HttpURLConnection.() -> T): T {
    return suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation {
            if (Build.VERSION.SDK_INT >= 26) disconnect() else GlobalScope.launch(Dispatchers.IO) { disconnect() }
        }
        GlobalScope.launch(Dispatchers.IO) {
            try {
                cont.resume(block())
            } catch (e: Throwable) {
                cont.resumeWithException(e)
            }
        }
    }
}

fun parsePort(str: String?, default: Int, min: Int = 1025): Int {
    val value = str?.toIntOrNull() ?: default
    return if (value < min || value > 65535) default else value
}

fun broadcastReceiver(callback: (Context, Intent) -> Unit): BroadcastReceiver =
    object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = callback(context, intent)
    }

fun Context.listenForPackageChanges(onetime: Boolean = true, callback: () -> Unit) =
    object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            callback()
            if (onetime) context.unregisterReceiver(this)
        }
    }.apply {
        registerReceiver(this, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        })
    }

/**
 * Based on: https://stackoverflow.com/a/26348729/2245107
 */
fun Resources.Theme.resolveResourceId(@AttrRes resId: Int): Int {
    val typedValue = TypedValue()
    if (!resolveAttribute(resId, typedValue, true)) throw Resources.NotFoundException()
    return typedValue.resourceId
}

fun Preference.remove() = parent!!.removePreference(this)

/**
 * A slightly more performant variant of parseNumericAddress.
 *
 * Bug in Android 9.0 and lower: https://issuetracker.google.com/issues/123456213
 */

private val parseNumericAddress by lazy {
    InetAddress::class.java.getDeclaredMethod("parseNumericAddress", String::class.java).apply {
        isAccessible = true
    }
}

fun String?.parseNumericAddress(): InetAddress? =
    Os.inet_pton(OsConstants.AF_INET, this) ?: Os.inet_pton(OsConstants.AF_INET6, this)?.let {
        if (Build.VERSION.SDK_INT >= 29) it else parseNumericAddress.invoke(
            null, this
        ) as InetAddress
    }

@JvmOverloads
fun DialogFragment.showAllowingStateLoss(fragmentManager: FragmentManager, tag: String? = null) {
    if (!fragmentManager.isStateSaved) show(fragmentManager, tag)
}

fun String.pathSafe(): String {
    // " " encoded as +
    return URLEncoder.encode(this, "UTF-8")
}

fun String.urlSafe(): String {
    return URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}

fun String.unUrlSafe(): String {
    return NGUtil.urlDecode(this)
}

fun RecyclerView.scrollTo(index: Int, force: Boolean = false) {
    if (force) post {
        scrollToPosition(index)
    }
    postDelayed({
        try {
            layoutManager?.startSmoothScroll(object : LinearSmoothScroller(context) {
                init {
                    targetPosition = index
                }

                override fun getVerticalSnapPreference(): Int {
                    return SNAP_TO_START
                }
            })
        } catch (ignored: IllegalArgumentException) {
        }
    }, 300L)
}

val app get() = SagerNet.application

val shortAnimTime by lazy {
    app.resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
}

fun View.crossFadeFrom(other: View) {
    clearAnimation()
    other.clearAnimation()
    if (isVisible && other.isGone) return
    alpha = 0F
    visibility = View.VISIBLE
    animate().alpha(1F).duration = shortAnimTime
    other.animate().alpha(0F).setListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            other.visibility = View.GONE
        }
    }).duration = shortAnimTime
}


fun Fragment.snackbar(textId: Int) = (requireActivity() as MainActivity).snackbar(textId)
fun Fragment.snackbar(text: CharSequence) = (requireActivity() as MainActivity).snackbar(text)

fun ThemedActivity.startFilesForResult(
    launcher: ActivityResultLauncher<String>, input: String
) {
    try {
        return launcher.launch(input)
    } catch (_: ActivityNotFoundException) {
    } catch (_: SecurityException) {
    }
    snackbar(getString(R.string.file_manager_missing)).show()
}

fun Fragment.startFilesForResult(
    launcher: ActivityResultLauncher<String>, input: String
) {
    try {
        return launcher.launch(input)
    } catch (_: ActivityNotFoundException) {
    } catch (_: SecurityException) {
    }
    (requireActivity() as ThemedActivity).snackbar(getString(R.string.file_manager_missing)).show()
}

fun Fragment.needReload() {
    if (DataStore.serviceState.started) {
        snackbar(getString(R.string.need_reload)).setAction(R.string.apply) {
            SagerNet.reloadService()
        }.show()
    }
}

fun Fragment.needRestart() {
    snackbar(R.string.need_restart).setAction(R.string.apply) {
        triggerFullRestart(requireContext())
    }.show()
}

fun triggerFullRestart(ctx: Context) {
    runOnDefaultDispatcher {
        SagerNet.stopService()
        delay(500)
        SagerConnection.restartingApp = true
        val connection = SagerConnection(SagerConnection.CONNECTION_ID_RESTART_BG)
        connection.connect(ctx, RestartCallback {
            ProcessPhoenix.triggerRebirth(ctx, Intent(ctx, MainActivity::class.java))
        })
    }
}

private class RestartCallback(val callback: () -> Unit) : SagerConnection.Callback {
    override fun stateChanged(
        state: BaseService.State,
        profileName: String?,
        msg: String?
    ) {
    }

    override fun onServiceConnected(service: ISagerNetService) {
        callback()
    }
}

fun Context.getColour(@ColorRes colorRes: Int): Int {
    return ContextCompat.getColor(this, colorRes)
}

fun Context.getColorAttr(@AttrRes resId: Int): Int {
    return ContextCompat.getColor(this, TypedValue().also {
        theme.resolveAttribute(resId, it, true)
    }.resourceId)
}

val isExpert: Boolean by lazy { BuildConfig.DEBUG || DataStore.isExpert }
const val isOss = BuildConfig.FLAVOR == "oss"
const val isPlay = BuildConfig.FLAVOR == "play"
const val isPreview = BuildConfig.FLAVOR == "preview"

fun <T> Continuation<T>.tryResume(value: T) {
    try {
        resumeWith(Result.success(value))
    } catch (ignored: IllegalStateException) {
    }
}

fun <T> Continuation<T>.tryResumeWithException(exception: Throwable) {
    try {
        resumeWith(Result.failure(exception))
    } catch (ignored: IllegalStateException) {
    }
}

operator fun <F> KProperty0<F>.getValue(thisRef: Any?, property: KProperty<*>): F = get()
operator fun <F> KMutableProperty0<F>.setValue(
    thisRef: Any?, property: KProperty<*>, value: F
) = set(value)

operator fun AtomicBoolean.getValue(thisRef: Any?, property: KProperty<*>): Boolean = get()
operator fun AtomicBoolean.setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) =
    set(value)

operator fun AtomicInteger.getValue(thisRef: Any?, property: KProperty<*>): Int = get()
operator fun AtomicInteger.setValue(thisRef: Any?, property: KProperty<*>, value: Int) = set(value)

operator fun AtomicLong.getValue(thisRef: Any?, property: KProperty<*>): Long = get()
operator fun AtomicLong.setValue(thisRef: Any?, property: KProperty<*>, value: Long) = set(value)

operator fun <T> AtomicReference<T>.getValue(thisRef: Any?, property: KProperty<*>): T = get()
operator fun <T> AtomicReference<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) =
    set(value)

operator fun <K, V> Map<K, V>.getValue(thisRef: K, property: KProperty<*>) = get(thisRef)
operator fun <K, V> MutableMap<K, V>.setValue(thisRef: K, property: KProperty<*>, value: V?) {

    if (value != null) {

        put(thisRef, value)

    } else {

        remove(thisRef)

    }

}
