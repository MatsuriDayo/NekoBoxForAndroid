package moe.matsuri.nb4a.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder
import io.nekohasekai.sagernet.R

class LongClickListPreference
@JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = R.attr.dropdownPreferenceStyle
) : ListPreference(context, attrs, defStyle, 0) {
    private var mLongClickListener: View.OnLongClickListener? = null

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val itemView: View = holder.itemView
        itemView.setOnLongClickListener {
            mLongClickListener?.onLongClick(it) ?: true
        }
    }

    fun setOnLongClickListener(longClickListener: View.OnLongClickListener) {
        this.mLongClickListener = longClickListener
    }

}
