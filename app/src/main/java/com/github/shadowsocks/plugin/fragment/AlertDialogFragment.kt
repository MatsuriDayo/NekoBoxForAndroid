package com.github.shadowsocks.plugin.fragment

import android.app.Activity
import android.content.DialogInterface
import android.os.Bundle
import android.os.Parcelable
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Based on: https://android.googlesource.com/platform/
packages/apps/ExactCalculator/+/8c43f06/src/com/android/calculator2/AlertDialogFragment.java
 */
abstract class AlertDialogFragment<Arg : Parcelable, Ret : Parcelable?> :
    AppCompatDialogFragment(), DialogInterface.OnClickListener {
    companion object {
        private const val KEY_RESULT = "result"
        private const val KEY_ARG = "arg"
        private const val KEY_RET = "ret"
        private const val KEY_WHICH = "which"

        fun <Ret : Parcelable> setResultListener(fragment: Fragment, requestKey: String,
                                                 listener: (Int, Ret?) -> Unit) {
            fragment.setFragmentResultListener(requestKey) { _, bundle ->
                listener(bundle.getInt(KEY_WHICH, Activity.RESULT_CANCELED), bundle.getParcelable(KEY_RET))
            }
        }
        inline fun <reified T : AlertDialogFragment<*, Ret>, Ret : Parcelable?> setResultListener(
            fragment: Fragment, noinline listener: (Int, Ret?) -> Unit) =
            setResultListener(fragment, T::class.java.name, listener)
    }
    protected abstract fun AlertDialog.Builder.prepare(listener: DialogInterface.OnClickListener)

    private val resultKey get() = requireArguments().getString(KEY_RESULT)
    protected val arg by lazy { requireArguments().getParcelable<Arg>(KEY_ARG)!! }
    protected open fun ret(which: Int): Ret? = null

    private fun args() = arguments ?: Bundle().also { arguments = it }
    fun arg(arg: Arg) = args().putParcelable(KEY_ARG, arg)
    fun key(resultKey: String = javaClass.name) = args().putString(KEY_RESULT, resultKey)

    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog =
        MaterialAlertDialogBuilder(requireContext()).also { it.prepare(this) }.create()

    override fun onClick(dialog: DialogInterface?, which: Int) {
        setFragmentResult(resultKey ?: return, Bundle().apply {
            putInt(KEY_WHICH, which)
            putParcelable(KEY_RET, ret(which) ?: return@apply)
        })
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onClick(null, Activity.RESULT_CANCELED)
    }
}
