package io.nekohasekai.sagernet.widget

import android.view.View
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

object ListListener : OnApplyWindowInsetsListener {
    override fun onApplyWindowInsets(view: View, insets: WindowInsetsCompat) = insets.apply {
        view.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
    }
}
