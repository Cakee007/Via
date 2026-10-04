package dev.ujhhgtg.via.ui.dialog

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/** Original mark.via.common.widget.MaxHeightLayout. */
class MaxHeightLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, style: Int = 0) : FrameLayout(context, attrs, style) {
    var maximumHeight = -1
        set(value) { field = value; requestLayout() }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val adjusted = if (maximumHeight <= 0) heightMeasureSpec else if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED)
            MeasureSpec.makeMeasureSpec(maximumHeight, MeasureSpec.AT_MOST)
        else MeasureSpec.makeMeasureSpec(minOf(MeasureSpec.getSize(heightMeasureSpec), maximumHeight), MeasureSpec.getMode(heightMeasureSpec))
        super.onMeasure(widthMeasureSpec, adjusted)
    }
}
