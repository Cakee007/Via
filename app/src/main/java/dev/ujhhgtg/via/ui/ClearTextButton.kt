package dev.ujhhgtg.via.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.skins.SkinResources

/** g6.y.P/Q: an 18dp ic_close at the trailing edge while this field contains text. */
internal fun EditText.attachClearTextButton(tint: Int) {
    val icon = SkinResources.drawable(context, R.drawable.close)?.mutate() ?: return
    icon.setTint(tint)
    val size = context.dp(18f)
    icon.setBounds(0, 0, size, size)
    fun showIfNeeded() {
        val current = compoundDrawablesRelative
        val next = if (text.isNotEmpty()) icon else null
        if (current[2] !== next) setCompoundDrawablesRelative(current[0], current[1], next, current[3])
    }
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = showIfNeeded()
    })
    var clearing = false
    setOnTouchListener { _, event ->
        if (event.action == MotionEvent.ACTION_DOWN) {
            clearing = text.isNotEmpty() && if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                event.x <= compoundPaddingLeft
            } else event.x >= measuredWidth - compoundPaddingRight
        } else if (event.action == MotionEvent.ACTION_UP && clearing) setText("")
        clearing
    }
    showIfNeeded()
}
