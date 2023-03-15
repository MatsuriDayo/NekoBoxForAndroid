package io.nekohasekai.sagernet.widget

import android.content.Context
import android.util.AttributeSet
import androidx.core.widget.addTextChangedListener
import com.google.android.material.textfield.TextInputLayout
import com.takisoft.preferencex.EditTextPreference
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.readableMessage
import okhttp3.HttpUrl.Companion.toHttpUrl

class LinkPreference : EditTextPreference {

    var defaultValue: String? = null

    constructor(context: Context) : this(context, null)

    constructor(
        context: Context,
        attrs: AttributeSet?,
    ) : this(context, attrs, com.takisoft.preferencex.R.attr.editTextPreferenceStyle)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
    ) : this(context, attrs, defStyleAttr, 0)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        val a = context.obtainStyledAttributes(
            attrs, R.styleable.Preference, defStyleAttr, defStyleRes
        )
        if (a.hasValue(androidx.preference.R.styleable.Preference_defaultValue)) {
            defaultValue = onGetDefaultValue(
                a, androidx.preference.R.styleable.Preference_defaultValue
            )?.toString()
        } else if (a.hasValue(androidx.preference.R.styleable.Preference_android_defaultValue)) {
            defaultValue = onGetDefaultValue(
                a, androidx.preference.R.styleable.Preference_android_defaultValue
            )?.toString()
        }
    }

    init {
        dialogLayoutResource = R.layout.layout_link_dialog

        setOnBindEditTextListener {
            val linkLayout = it.rootView.findViewById<TextInputLayout>(R.id.input_layout)
            fun validate() {
                val link = it.text
                if (link.isBlank()) {
                    linkLayout.isErrorEnabled = false
                    return
                }
                try {
                    val url = link.toString().toHttpUrl()
                    if ("http".equals(url.scheme, true)) {
                        linkLayout.error = app.getString(R.string.cleartext_http_warning)
                        linkLayout.isErrorEnabled = true
                    } else {
                        linkLayout.isErrorEnabled = false
                    }
                } catch (e: Exception) {
                    linkLayout.error = e.readableMessage
                    linkLayout.isErrorEnabled = true
                }
            }
            validate()
            it.addTextChangedListener {
                validate()
            }
        }

        setOnPreferenceChangeListener { _, newValue ->
            if ((newValue as String).isBlank()) {
                text = defaultValue
                false
            } else try {
                newValue.toHttpUrl()
                true
            } catch (ignored: Exception) {
                false
            }
        }
    }

}