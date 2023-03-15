package io.nekohasekai.sagernet.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.progressindicator.CircularProgressIndicator

class FabProgressBehavior(context: Context, attrs: AttributeSet?) :
    CoordinatorLayout.Behavior<CircularProgressIndicator>(context, attrs) {
    override fun layoutDependsOn(
        parent: CoordinatorLayout,
        child: CircularProgressIndicator,
        dependency: View,
    ): Boolean {
        return dependency.id == (child.layoutParams as CoordinatorLayout.LayoutParams).anchorId
    }

    override fun onLayoutChild(
        parent: CoordinatorLayout, child: CircularProgressIndicator,
        layoutDirection: Int,
    ): Boolean {
        val size = parent.getDependencies(child).single().measuredHeight + child.trackThickness
        return if (child.indicatorSize != size) {
            child.indicatorSize = size
            true
        } else false
    }
}
