package io.nekohasekai.sagernet.ui.importer

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import io.nekohasekai.sagernet.R

class QuickImportDialogFragment(val callback: (String, Boolean) -> Unit) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.paste_config_here)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
        }

        var autoConnect = true

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.import_quick)
            .setView(input)
            .setMultiChoiceItems(arrayOf(getString(R.string.auto_connect_after_import)), booleanArrayOf(true)) { _, _, isChecked ->
                autoConnect = isChecked
            }
            .setPositiveButton(R.string.import_btn) { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotBlank()) callback(text, autoConnect)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

}
