package dev.ujhhgtg.via.reader

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.settings.settingsColor
import dev.ujhhgtg.via.ui.dp
import kotlin.math.abs

/** s6.ib/u3: owns k1 in the full browser canvas (f8.e), outside the WebView and popup containers. */
class ReadAloudFloatingControls(
    private val canvas: ViewGroup,
    private val onAction: (Int) -> Unit,
) {
    private var controls: FloatingView? = null

    /** activeTaskId is s6.i1, which is assigned by a successful read request and cleared by Stop. */
    fun update(task: ReadAloudTask?, activeTaskId: String?, recreate: Boolean = false) {
        if (task != null && activeTaskId != null) {
            if (recreate && controls != null) {
                rememberedY = controls!!.translationY.toInt()
                removeControls()
            }
            val view = controls ?: FloatingView(canvas.context, onAction).also {
                controls = it
                it.alpha = 0f
                canvas.addView(it, ViewGroup.LayoutParams(-2, -2))
                it.translationY = (rememberedY ?: (canvas.height / 2)).toFloat()
                it.animate().alpha(1f).setDuration(100L).start()
            }
            view.setPlaying(task.isPlaying)
        } else controls?.let { view ->
            rememberedY = view.translationY.toInt()
            view.animate().alpha(0f).setDuration(100L).withEndAction(::removeControls).start()
        }
    }

    /** The browser removes its listener separately in D1 and stops its active task in B1. */
    fun dispose() { removeControls() }

    private fun removeControls() {
        controls?.let { view ->
            view.stopRotation()
            (view.parent as? ViewGroup)?.removeView(view)
        }
        controls = null
    }

    /** mark.via.common.widget.k1: exact three-button, vertical-drag implementation. */
    @SuppressLint("ViewConstructor", "ClickableViewAccessibility")
    private class FloatingView(context: Context, private val onAction: (Int) -> Unit) : LinearLayout(context) {
        private val panel: ImageView
        private val toggle: ImageView
        private val stop: ImageView
        private var playing = false
        private var rotationAnimator: ObjectAnimator? = null
        // k1.o = y.h(4) + dimen b8(36) + max(dimen bj(48), dimen b(48)).
        private val reserve = context.dp(4f) + context.dp(36f) + maxOf(context.dp(48f), context.dp(48f))
        private var previousX = 0f
        private var previousY = 0f

        init {
            orientation = HORIZONTAL
            val radius = resources.getDimensionPixelSize(R.dimen.via_corner_radius).toFloat()
            background = GradientDrawable().apply {
                setColor(settingsColor(context, R.attr.viaBackgroundColor, Color.WHITE))
                cornerRadii = floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f)
            }
            fun image(resource: Int, padding: Int, label: Int? = null) = ImageView(context).apply {
                setImageResource(resource)
                setColorFilter(settingsColor(context, R.attr.viaSubtleColor, Color.DKGRAY))
                setPaddingRelative(padding, padding, padding, padding)
                if (label != null) contentDescription = context.getString(label)
                addView(this, LayoutParams(context.dp(36f), context.dp(36f)))
            }
            panel = image(R.drawable.bars_circle, context.dp(6f), R.string.read_aloud_panel)
            toggle = image(R.drawable.reader_floating_initial, context.dp(7f))
            stop = image(R.drawable.close, context.dp(7f), R.string.stop_reading_aloud)
            panel.setOnClickListener { onAction(OPEN_PANEL) }
            toggle.setOnClickListener { onAction(if (playing) PAUSE else PLAY) }
            stop.setOnClickListener { onAction(STOP) }
        }

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { previousX = event.rawX; previousY = event.rawY }
                MotionEvent.ACTION_MOVE -> if (abs(event.rawX - previousX) < abs(event.rawY - previousY)) return true
            }
            return super.onInterceptTouchEvent(event)
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val parent = parent as? ViewGroup ?: return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { previousX = event.rawX; previousY = event.rawY }
                MotionEvent.ACTION_MOVE -> {
                    translationY = maxOf(reserve.toFloat(), minOf(translationY + event.rawY - previousY,
                        (parent.height - height - reserve).toFloat()))
                    previousX = event.rawX; previousY = event.rawY
                }
            }
            return true
        }
        fun setPlaying(value: Boolean) {
            if (playing == value) return
            playing = value
            if (value) {
                if (rotationAnimator == null) rotationAnimator = ObjectAnimator.ofFloat(panel,
                    ROTATION, panel.rotation, panel.rotation + 360f).apply {
                    duration = 3000L; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator(); start()
                }
            } else stopRotation()
            toggle.setImageResource(if (value) R.drawable.reader_pause else R.drawable.reader_play)
            toggle.contentDescription = context.getString(if (value) R.string.download_action_pause else R.string.download_action_resume)
            stop.visibility = if (value) GONE else VISIBLE
        }
        fun stopRotation() { rotationAnimator?.let { if (it.isStarted) it.cancel() }; rotationAnimator = null }
    }

    companion object {
        const val PLAY = 1
        const val PAUSE = 2
        const val STOP = 3
        const val OPEN_PANEL = 4
        // v5.b stores k1's integer y without an expiry, only on hide or themed recreation.
        private var rememberedY: Int? = null
    }
}
