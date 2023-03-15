package io.nekohasekai.sagernet.widget

import android.content.Context
import android.util.AttributeSet
import com.takisoft.preferencex.SimpleMenuPreference
import io.nekohasekai.sagernet.database.SagerDatabase

class GroupPreference : SimpleMenuPreference {

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context, attrs, defStyle
    )

    constructor(
        context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        val groups = SagerDatabase.groupDao.allGroups()

        entries = groups.map { it.displayName() }.toTypedArray()
        entryValues = groups.map { "${it.id}" }.toTypedArray()
    }

    override fun getSummary(): CharSequence? {
        if (!value.isNullOrBlank() && value != "0") {
            return SagerDatabase.groupDao.getById(value.toLong())?.displayName()
                ?: super.getSummary()
        }
        return super.getSummary()
    }

}