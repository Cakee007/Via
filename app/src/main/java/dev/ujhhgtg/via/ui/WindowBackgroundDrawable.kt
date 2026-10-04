package dev.ujhhgtg.via.ui

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import androidx.core.graphics.drawable.toDrawable

/** c8.b: raw image, native window filter, then the current page's covering color. */
class WindowBackgroundDrawable : LayerDrawable(arrayOf(0.toDrawable(), 0.toDrawable(), 0.toDrawable())) {
    init { setId(0, 1) }
    val hasImage: Boolean get() = getDrawable(0) !is ColorDrawable

    fun setImage(image: Drawable?) {
        if (image == null && getDrawable(0) is ColorDrawable) (getDrawable(0) as ColorDrawable).color = 0
        else setDrawableByLayerId(1, image ?: 0.toDrawable())
        invalidateDrawable(getDrawable(0))
    }

    fun setFilterColor(color: Int) {
        (getDrawable(1) as ColorDrawable).color = color
        invalidateDrawable(getDrawable(1))
    }

    fun setCoverColor(color: Int) { (getDrawable(2) as ColorDrawable).color = color }

    /** c8.b.a clones drawable state so each background has independent bounds and callbacks. */
    fun snapshot(fallbackColor: Int): WindowBackgroundDrawable = WindowBackgroundDrawable().also { copy ->
        val image = getDrawable(0).constantState
        if (image == null) copy.setFilterColor(fallbackColor)
        else {
            copy.setImage(image.newDrawable())
            copy.setFilterColor((getDrawable(1) as ColorDrawable).color)
            copy.setCoverColor((getDrawable(2) as ColorDrawable).color)
        }
    }
}
