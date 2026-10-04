package dev.ujhhgtg.via.ui

import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.skins.SkinResources

/** g6.y.R/Q: the skin-aware 22dp eye control, preserving the caret when input type changes. */
internal fun EditText.attachPasswordVisibilityButton(tint: Int) {
    val on = SkinResources.drawable(context, R.drawable.password_eye_on)?.mutate() ?: return
    val off = SkinResources.drawable(context, R.drawable.password_eye_off)?.mutate() ?: return
    val size = context.dp(22f)
    for (icon in listOf(on, off)) { icon.setBounds(0, 0, size, size); icon.setTint(tint) }
    fun showState() {
        val current = compoundDrawablesRelative
        setCompoundDrawablesRelative(current[0], current[1], if (inputType and 0x90 == 0x90) on else off, current[3])
    }
    showState()
    var pressed = false
    setOnTouchListener { _, event ->
        if (event.action == MotionEvent.ACTION_DOWN) {
            pressed = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) event.x <= compoundPaddingLeft
                else event.x >= measuredWidth - compoundPaddingRight
        } else if (event.action == MotionEvent.ACTION_UP && pressed) {
            val cursor = selectionStart
            inputType = if (inputType and 0x90 == 0x90) inputType and 0x90.inv() or 0x80
                else inputType and 0x80.inv() or 0x90
            showState()
            setSelection(cursor)
        }
        pressed
    }
}
