package moe.matsuri.nb4a.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.preference.PreferenceViewHolder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.takisoft.preferencex.SimpleMenuPreference

class MTUPreference : SimpleMenuPreference {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    constructor(
        context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        setSummaryProvider {
            value.toString()
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val itemView: View = holder.itemView
        itemView.setOnLongClickListener {
            val view = EditText(context).apply {
                inputType = EditorInfo.TYPE_CLASS_NUMBER
                setText(preferenceDataStore?.getString(key, "") ?: "")
            }

            MaterialAlertDialogBuilder(context).setTitle("MTU")
                .setView(view)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val mtu = view.text.toString().toInt()
                    if (mtu < 1000 || mtu > 10000) return@setPositiveButton
                    value = mtu.toString()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }
    }

}
