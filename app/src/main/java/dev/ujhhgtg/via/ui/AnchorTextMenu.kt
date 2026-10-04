package dev.ujhhgtg.via.ui

import android.app.Activity
import android.content.Context
import android.app.Dialog
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.dialog.DialogWindowBlur

/**
 * z5.a: the small text-item popup anchored to a browser view (the toolbar
 * address label). A floating dialog positioned above or below the anchor's
 * screen edge, with 48dp rows (16dp side padding, square pressed ripple,
 * 14sp primary-color text) inside an 18dp padded container.
 */
class AnchorTextMenu(private val context: Context) {
    private class Item(val label: String, val click: (View) -> Unit)

    private val items = mutableListOf<Item>()
    private var dialog: Dialog? = null

    fun add(label: String, click: (View) -> Unit): AnchorTextMenu {
        items += Item(label, click)
        return this
    }

    fun show(anchor: View) {
        if (items.isEmpty()) return
        val container = LinearLayout(context).apply {
            // x7.n.p = 0x7f070029 -> dimen/ae = 18dp, including API variants.
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(18f), 0, dp(18f), 0)
        }
        items.forEach { item ->
            val text = TextView(context).apply {
                // z5.a$b: 48dp tall rows with 16dp side padding, centered a6
                // text (14sp primary color) over the square pressed ripple.
                layoutParams = LinearLayout.LayoutParams(-2, dp(48f))
                setPadding(dp(16f), 0, dp(16f), 0)
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(themedColor(R.attr.viaPrimaryTextColor))
                typeface = BrowserPreferences(context).selectedTypeface() ?: Typeface.DEFAULT
                background = ContextCompat.getDrawable(context, R.drawable.flat_ripple)
                text = item.label
                setOnClickListener { view ->
                    dismiss()
                    item.click(view)
                }
            }
            container.addView(text)
        }

        val dialog = Dialog(context, R.style.Via_Dialog).apply {
            setContentView(FrameLayout(context).apply {
                addView(container, FrameLayout.LayoutParams(-2, -2))
            })
            setCancelable(true)
            setCanceledOnTouchOutside(true)
        }
        this.dialog = dialog

        dialog.window?.let { window ->
            val content = (context as Activity).findViewById<View>(android.R.id.content)
            val contentHeight = content.height
            val contentWidth = content.width
            val contentLocation = IntArray(2)
            content.getLocationOnScreen(contentLocation)
            val contentY = contentLocation[1]
            val anchorPosition = IntArray(2)
            anchor.getLocationOnScreen(anchorPosition)
            val attributes = window.attributes
            var x = maxOf(anchorPosition[0], dp(6f))
            var y: Int
            val gravity: Int
            // z5.a.f: anchor in the lower half of the screen -> the popup
            // grows upward from the anchor's top edge, otherwise downward
            // from its bottom edge, compensated by the content offset.
            if (anchorPosition[1] > contentHeight / 2) {
                gravity = Gravity.BOTTOM or Gravity.START
                y = contentHeight - anchorPosition[1]
                if (contentY > 0) y += contentY
            } else {
                gravity = Gravity.TOP or Gravity.START
                y = anchorPosition[1] + anchor.height
                if (contentY > 0) y -= contentY
            }
            if (Build.VERSION.SDK_INT >= 30) {
                val insets = (context.getSystemService(WindowManager::class.java))
                    .currentWindowMetrics.windowInsets
                if (insets.isVisible(android.view.WindowInsets.Type.statusBars())) {
                    val bars = insets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars())
                    y -= if (gravity and Gravity.BOTTOM == Gravity.BOTTOM) bars.bottom else bars.top
                }
            }
            attributes.gravity = gravity
            attributes.x = x
            attributes.y = y
            attributes.width = WindowManager.LayoutParams.WRAP_CONTENT
            attributes.height = WindowManager.LayoutParams.WRAP_CONTENT
            // z5.a.f: flags &= -3 clears FLAG_DIM_BEHIND, so the popup does
            // not dim the page behind it.
            attributes.flags = attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
            window.setAttributes(attributes)
            // z5.a.f calls z8.l.a(window, 2): blur the popup surface when
            // the experimental blur effect is enabled, without blurring
            // or dimming the browser behind this anchored menu.
            DialogWindowBlur.apply(context as Activity, window, 2)
        }
        dialog.show()
    }

    fun dismiss() {
        dialog?.takeIf { it.isShowing }?.dismiss()
        dialog = null
    }

    private fun themedColor(attribute: Int): Int = context.obtainStyledAttributes(intArrayOf(attribute)).let {
        try { it.getColor(0, 0) } finally { it.recycle() }
    }

    private fun dp(value: Float): Int = (value * context.resources.displayMetrics.density + .5f).toInt()
}
