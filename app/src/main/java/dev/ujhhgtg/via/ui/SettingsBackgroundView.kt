package dev.ujhhgtg.via.ui

import android.content.Context
import android.widget.ImageView

/** mark.via.common.widget.n1: keep the image's full height while the IME resizes its viewport. */
internal class SettingsBackgroundView(context: Context) : ImageView(context) {
    private var imageWidth = 0
    private var imageHeight = 0
    init { scaleType = ScaleType.MATRIX }

    fun resetImageBounds() = updateBounds(false, width, height)

    private fun updateBounds(preserveHeight: Boolean, width: Int, height: Int) {
        val image = drawable ?: return
        val availableWidth = width - paddingLeft - paddingRight
        val availableHeight = height - paddingTop - paddingBottom
        if (availableWidth <= 0 || availableHeight <= 0) return
        if (!preserveHeight || imageWidth != availableWidth || availableHeight >= imageHeight) {
            imageWidth = availableWidth
            imageHeight = availableHeight
        }
        image.setBounds(0, 0, availableWidth, imageHeight)
        imageMatrix = imageMatrix.apply { reset() }
    }

    override fun setFrame(left: Int, top: Int, right: Int, bottom: Int): Boolean {
        val changed = super.setFrame(left, top, right, bottom)
        updateBounds(true, right - left, bottom - top)
        return changed
    }
}
