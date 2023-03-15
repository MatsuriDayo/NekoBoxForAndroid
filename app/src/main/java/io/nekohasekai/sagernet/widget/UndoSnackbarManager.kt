package io.nekohasekai.sagernet.widget

import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ui.ThemedActivity

/**
 * @param activity ThemedActivity.
 * //@param view The view to find a parent from.
 * @param undo Callback for undoing removals.
 * @param commit Callback for committing removals.
 * @tparam T Item type.
 */
class UndoSnackbarManager<in T>(
    private val activity: ThemedActivity,
    private val callback: Interface<T>,
) {

    interface Interface<in T> {
        fun undo(actions: List<Pair<Int, T>>)
        fun commit(actions: List<Pair<Int, T>>)
    }

    private val recycleBin = ArrayList<Pair<Int, T>>()
    private val removedCallback = object : Snackbar.Callback() {
        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
            if (last === transientBottomBar && event != DISMISS_EVENT_ACTION) {
                callback.commit(recycleBin)
                recycleBin.clear()
                last = null
            }
        }
    }

    private var last: Snackbar? = null

    fun remove(items: Collection<Pair<Int, T>>) {
        recycleBin.addAll(items)
        val count = recycleBin.size
        activity.snackbar(activity.resources.getQuantityString(R.plurals.removed, count, count))
            .apply {
                addCallback(removedCallback)
                setAction(R.string.undo) {
                    callback.undo(recycleBin.reversed())
                    recycleBin.clear()
                }
                last = this
                show()
            }
    }

    fun remove(vararg items: Pair<Int, T>) = remove(items.toList())

    fun flush() = last?.dismiss()
}
