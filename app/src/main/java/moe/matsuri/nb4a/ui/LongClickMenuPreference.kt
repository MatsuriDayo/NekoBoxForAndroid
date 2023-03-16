package moe.matsuri.nb4a.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.preference.PreferenceViewHolder
import com.takisoft.preferencex.SimpleMenuPreference
import io.nekohasekai.sagernet.R

class LongClickMenuPreference
@JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.takisoft.preferencex.simplemenu.R.attr.simpleMenuPreferenceStyle,
    defStyleRes: Int = R.style.Preference_SimpleMenuPreference
) : SimpleMenuPreference(
    context, attrs, defStyleAttr, defStyleRes
) {
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
