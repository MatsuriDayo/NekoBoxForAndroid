package io.nekohasekai.sagernet.widget

import android.content.Context
import android.util.AttributeSet
import com.takisoft.preferencex.EditTextPreference
import io.nekohasekai.sagernet.ktx.USER_AGENT

class UserAgentPreference : EditTextPreference {

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context, attrs, defStyle
    )

    constructor(
        context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    public override fun notifyChanged() {
        super.notifyChanged()
    }

    override fun getSummary(): CharSequence? {
        if (text.isNullOrBlank()) {
            return USER_AGENT
        }
        return super.getSummary()
    }

}