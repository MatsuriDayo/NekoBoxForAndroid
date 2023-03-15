package io.nekohasekai.sagernet.widget

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.*
import io.nekohasekai.sagernet.R

object ListHolderListener : OnApplyWindowInsetsListener {
    override fun onApplyWindowInsets(view: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
        view.setPadding(statusBarInsets.left,
            statusBarInsets.top,
            statusBarInsets.right,
            statusBarInsets.bottom)
        return WindowInsetsCompat.Builder(insets).apply {
            setInsets(WindowInsetsCompat.Type.statusBars(), Insets.NONE)
            /*setInsets(WindowInsetsCompat.Type.navigationBars(),
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()))*/
        }.build()
    }

    fun setup(activity: AppCompatActivity) = activity.findViewById<View>(android.R.id.content).let {
        ViewCompat.setOnApplyWindowInsetsListener(it, ListHolderListener)
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
    }
}

object MainListListener : OnApplyWindowInsetsListener {
    override fun onApplyWindowInsets(view: View, insets: WindowInsetsCompat) = insets.apply {
        view.updatePadding(bottom = view.resources.getDimensionPixelOffset(R.dimen.main_list_padding_bottom) +
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
    }
}

object ListListener : OnApplyWindowInsetsListener {
    override fun onApplyWindowInsets(view: View, insets: WindowInsetsCompat) = insets.apply {
        view.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
    }
}
