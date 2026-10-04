package dev.ujhhgtg.via.ui.dialog

import android.app.Activity
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import java.util.function.Consumer

/** p8.l and z8.l.a: original cross-window blur and render-effect fallback. */
internal object DialogWindowBlur {
    fun apply(activity: Activity, window: Window, mode: Int) {
        if (mode == 0 || Build.VERSION.SDK_INT < 31 || !BrowserPreferences(activity).blurEffect) return
        val attrs = activity.obtainStyledAttributes(intArrayOf(R.attr.viaDialogBackground))
        val background = try { attrs.getDrawable(0) } finally { attrs.recycle() } ?: return
        val behind = mode == 1
        var fallbackActive = false
        val dark = BrowserPreferences(activity).isNightMode
        window.setBackgroundDrawable(background)
        if (behind) window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND or WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        fun fallback(enabled: Boolean) {
            if (!behind) return
            if (enabled) fallbackActive = true
            activity.window.decorView.setRenderEffect(if (enabled) RenderEffect.createBlurEffect(30f, 30f, Shader.TileMode.MIRROR) else null)
            background.alpha = if (enabled) { if (dark) 229 else 216 } else 255
            window.setDimAmount(if (enabled) .2f else .4f)
        }
        val changed = Consumer<Boolean> { enabled ->
            if (!enabled) { if (behind) fallback(true) }
            else {
                background.alpha = if (dark) 216 else 204
                window.setDimAmount(.2f)
                window.setBackgroundBlurRadius(80)
                if (behind) window.attributes.blurBehindRadius = 30
                window.attributes = window.attributes
            }
        }
        window.decorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) { window.windowManager.addCrossWindowBlurEnabledListener(changed) }
            override fun onViewDetachedFromWindow(view: View) {
                window.windowManager.removeCrossWindowBlurEnabledListener(changed)
                if (fallbackActive) fallback(false)
            }
        })
    }
}
