package dji.sampleV5.util.animation

import android.view.View
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.RelativeLayout

/**
 * 视图放大/缩小 切换动画
 */
class ResizeAnimation(
    private val mView: View,
    private val mFromWidth: Int,
    private val mFromHeight: Int,
    private val mToWidth: Int,
    private val mToHeight: Int,
    private val mMargin: Int
) :
    Animation() {
    init {
        duration = 300
    }

    override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
        val height = (mToHeight - mFromHeight) * interpolatedTime + mFromHeight
        val width = (mToWidth - mFromWidth) * interpolatedTime + mFromWidth
        val p = mView.layoutParams as RelativeLayout.LayoutParams
        p.height = height.toInt()
        p.width = width.toInt()
        p.rightMargin = mMargin
        p.bottomMargin = mMargin
        mView.requestLayout()
    }
}