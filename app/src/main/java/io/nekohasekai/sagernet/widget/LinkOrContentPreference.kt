package io.nekohasekai.sagernet.widget

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import androidx.core.widget.addTextChangedListener
import com.google.android.material.textfield.TextInputLayout
import com.takisoft.preferencex.EditTextPreference
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.readableMessage
import okhttp3.HttpUrl.Companion.toHttpUrl

class LinkOrContentPreference : EditTextPreference {

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    constructor(
        context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)


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