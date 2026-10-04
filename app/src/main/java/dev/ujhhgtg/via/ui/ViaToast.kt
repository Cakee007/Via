package dev.ujhhgtg.via.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences

/** f6.h/i: Via's transient in-app message, including its action affordance. */
@SuppressLint("StaticFieldLeak")
object ViaToast {
    const val LENGTH_SHORT = 0
    const val LENGTH_LONG = 1
    private val main = Handler(Looper.getMainLooper())
    private var current: View? = null
    private var showRunnable: Runnable? = null
    private var dismissRunnable: Runnable? = null
    private var cancelAction: (() -> Unit)? = null

    fun makeText(context: Context, text: CharSequence, duration: Int) = ToastHandle(context, text, duration)
    fun makeText(context: Context, resource: Int, duration: Int) = ToastHandle(context, context.getString(resource), duration)

    fun show(context: Context, text: CharSequence, duration: Int = LENGTH_SHORT,
        actionText: CharSequence? = null, onActionLongClick: (() -> Boolean)? = null,
        onCancel: (() -> Unit)? = null, singleLine: Boolean = false, action: (() -> Unit)? = null
    ) {
        val activity = activity(context) ?: return
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        activity.runOnUiThread {
            // h.s() ignores hosts that are not visible; i.a() replaces both
            // the currently shown message and any message still queued to show.
            if (!root.isShown) return@runOnUiThread
            dismissCurrent(false)
            if (text.isEmpty()) return@runOnUiThread
            val density = activity.resources.displayMetrics.density
            fun dp(value: Float) = (value * density + .5f).toInt()
            // h6.a truncates dimensions used for padding and margins.
            fun layoutDp(value: Int) = (value * density).toInt()
            val textSize = activity.resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat()
            val typeface = BrowserPreferences(activity).selectedTypeface()
            val surface = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                contentDescription = text
                setPaddingRelative(layoutDp(16), 0, layoutDp(16), 0)
                background = GradientDrawable().apply {
                    setColor(0xcc1e1e1e.toInt())
                    cornerRadius = dp(30f).toFloat()
                }
            }
            val message = TextView(activity).apply {
                this.text = text
                setTextColor(resolveColor(activity, R.attr.viaOverlayTextColor, Color.WHITE))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
                setTypeface(typeface, Typeface.NORMAL)
                maxWidth = dp(360f)
                setPaddingRelative(layoutDp(4), layoutDp(14), layoutDp(4), layoutDp(14))
                // f6.h.g: h$b.j(true), set only by c8.s6.W (the undo toasts), truncates to one line.
                if (singleLine) { setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END }
            }
            surface.addView(message, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (actionText != null) {
                surface.addView(TextView(activity).apply {
                    this.text = actionText
                    setTextColor(resolveColor(activity, R.attr.viaAccentColor, 0xff6f8de1.toInt()))
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
                    setTypeface(typeface, Typeface.BOLD)
                    isAllCaps = true
                    gravity = Gravity.CENTER
                    minWidth = dp(56f)
                    maxWidth = dp(200f)
                    // f6.h uses the same rounded control background as the
                    // rest of Via; the action is not an elevated platform
                    // button.
                    setBackgroundResource(R.drawable.rounded_rect_ripple)
                    setPaddingRelative(layoutDp(8), layoutDp(8), layoutDp(8), layoutDp(8))
                    setOnClickListener { cancelAction = null; action?.invoke(); dismissCurrent(true) }
                    setOnLongClickListener { onActionLongClick?.invoke() ?: false }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            surface.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                topMargin = layoutDp(16)
                bottomMargin = dp(120f)
                marginStart = layoutDp(16); marginEnd = layoutDp(16)
            }
            current = surface
            cancelAction = onCancel
            var previousInset = 0
            ViewCompat.setOnApplyWindowInsetsListener(surface) { view, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val bottom = bars.bottom - bars.top
                val params = view.layoutParams as? FrameLayout.LayoutParams
                if (params != null && bottom != previousInset) {
                    previousInset = bottom
                    val base = dp(120f)
                    params.bottomMargin = if (bottom <= 0) base else minOf(base, dp(32f)) + bottom
                    view.layoutParams = params
                }
                insets
            }
            val display = Runnable {
                showRunnable = null
                root.addView(surface)
                animate(surface, true)
                surface.post { ViewCompat.requestApplyInsets(surface) }
                // f6.i posts the original event code after insertion.
                surface.postDelayed({ surface.sendAccessibilityEvent(128) }, 30L)
                val timeout = Runnable { dismissCurrent(true) }
                dismissRunnable = timeout
                main.postDelayed(timeout, if (actionText.isNullOrEmpty()) 1600L else 3500L)
            }
            showRunnable = display
            main.post(display)
        }
    }

    fun show(context: Context, resource: Int, duration: Int = LENGTH_SHORT, actionText: Int? = null,
        onActionLongClick: (() -> Boolean)? = null, onCancel: (() -> Unit)? = null, action: (() -> Unit)? = null) =
        show(context, context.getString(resource), duration, actionText?.let(context::getString), onActionLongClick, onCancel, action = action)

    fun dismissCurrent(animated: Boolean = false) {
        showRunnable?.let(main::removeCallbacks); showRunnable = null
        dismissRunnable?.let(main::removeCallbacks); dismissRunnable = null
        val view = current ?: return
        current = null
        val cancelled = cancelAction
        cancelAction = null
        cancelled?.invoke()
        val remove = Runnable { (view.parent as? ViewGroup)?.removeView(view) }
        if (animated && view.isShown) animate(view, false, remove)
        else remove.run()
    }

    private fun animate(view: View, showing: Boolean, end: Runnable? = null) {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.ALPHA, if (showing) 0f else 1f, if (showing) 1f else 0f),
                ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, if (showing) 20f else 0f, if (showing) 0f else 20f),
            )
            duration = 180L
            interpolator = DecelerateInterpolator()
            if (end != null) addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = end.run()
            })
            start()
        }
    }

    class ToastHandle internal constructor(private val context: Context, private val text: CharSequence, private val duration: Int) {
        fun show() = show(context, text, duration)
    }

    private fun activity(context: Context): Activity? {
        var current: Context? = context
        while (current is android.content.ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
    private fun resolveColor(context: Context, attribute: Int, fallback: Int): Int = context.obtainStyledAttributes(intArrayOf(attribute)).let { values ->
        try { values.getColor(0, fallback) } finally { values.recycle() }
    }
}
