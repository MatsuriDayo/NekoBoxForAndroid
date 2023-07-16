package io.nekohasekai.sagernet.widget

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import androidx.core.content.res.TypedArrayUtils
import androidx.core.widget.addTextChangedListener
import androidx.preference.EditTextPreference
import com.google.android.material.textfield.TextInputLayout
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.readableMessage
import okhttp3.HttpUrl.Companion.toHttpUrl

class LinkOrContentPreference
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = TypedArrayUtils.getAttr(
        context, R.attr.editTextPreferenceStyle,
        android.R.attr.editTextPreferenceStyle
    ),
    defStyleRes: Int = 0
) : EditTextPreference(context, attrs, defStyleAttr, defStyleRes) {

    init {
        dialogLayoutResource = R.layout.layout_urltest_preference_dialog

        setOnBindEditTextListener {
            val linkLayout = it.rootView.findViewById<TextInputLayout>(R.id.input_layout)
            fun validate() {
                val link = it.text
                if (link.isBlank()) {
                    linkLayout.isErrorEnabled = false
                    return
                }

                try {
                    if (Uri.parse(link.toString()).scheme == "content") {
                        linkLayout.isErrorEnabled = false
                        return
                    }
                    val url = link.toString().toHttpUrl()
                    if ("http".equals(url.scheme, true)) {
                        linkLayout.error = app.getString(R.string.cleartext_http_warning)
                        linkLayout.isErrorEnabled = true
                    } else {
                        linkLayout.isErrorEnabled = false
                    }
                    if (link.contains("\n")) {
                        linkLayout.error = "Unexpected new line"
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
    }

}