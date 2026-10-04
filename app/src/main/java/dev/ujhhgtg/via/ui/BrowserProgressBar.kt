package dev.ujhhgtg.via.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.animation.DecelerateInterpolator
import android.widget.ProgressBar
import kotlin.math.abs
import kotlin.math.pow

/** c8.f8/y7 + support.widget.w: the original 1000-unit animated browser progress. */
internal class BrowserProgressBar(context: Context) : ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal) {
    // The 3dp strip height is independent of x8.h.x's 2dp drawable corner radius.
    val stripHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, resources.displayMetrics).toInt()
    private val full = 1000
    private val pending = 800
    private var running = false
    private var motion: ValueAnimator? = null

    init {
        id = generateViewId()
        progress = 0
        max = full
        isIndeterminate = false
        visibility = GONE
        setBackgroundColor(Color.TRANSPARENT)
        progressDrawable = LayerDrawable(arrayOf(
            GradientDrawable().apply { setColor(Color.TRANSPARENT) },
            ClipDrawable(GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xff59c2e5.toInt(), 0xffa687e0.toInt())).apply {
                cornerRadius = context.dp(2f).toFloat()
            }, Gravity.START, ClipDrawable.HORIZONTAL),
        )).apply { setId(0, android.R.id.background); setId(1, android.R.id.progress) }
    }

    /** The caller supplies c8.s6.A's page progress plus 20, without clamping at 100. */
    fun setPageProgress(value: Int) = update(value, false)

    private fun update(value: Int, force: Boolean) {
        val target = value * 10
        if (!running && target < full) {
            alpha = 1f
            visibility = VISIBLE
            running = true
        }
        if (!running) return
        val current = progress
        if (target < full && current != 0 && abs(target - current) < full / 8) return
        motion?.let { old ->
            // w.e removes the completion callback before cancelling; a new
            // request must not execute the previous request's hide/reset.
            old.removeAllListeners()
            old.cancel()
            alpha = 1f
        }
        val animation = ValueAnimator.ofFloat(0f, 1f)
        motion = animation
        val distance = abs(current - target).toLong()
        val duration: Long
        when {
            target < pending && !force && current >= target -> {
                duration = (abs(pending - current) * 30).toLong()
                animation.addUpdateListener {
                    val fraction = (it.animatedValue as Float).toDouble()
                    progress = (current.toDouble() + fraction.pow(2.0) * abs(pending - current)).toInt()
                }
            }
            target < pending -> {
                duration = (abs(pending - target) * 30).toLong() + distance
                val ratio = duration.toFloat() / distance.toFloat()
                animation.addUpdateListener {
                    val fraction = it.animatedValue as Float
                    progress = (current + minOf(ratio * fraction, 1f) * (target - current) +
                        fraction * abs(pending - target)).toInt()
                }
            }
            target < full -> {
                duration = distance
                animation.addUpdateListener {
                    progress = (current + (it.animatedValue as Float) * (target - current)).toInt()
                }
            }
            else -> {
                duration = distance / 2L
                animation.addUpdateListener {
                    val fraction = it.animatedValue as Float
                    progress = (current + (full - current) * fraction).toInt()
                    alpha = 1f - fraction
                }
                animation.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        visibility = GONE
                        progress = 0
                        running = false
                    }
                })
            }
        }
        animation.interpolator = DecelerateInterpolator()
        animation.duration = duration
        animation.start()
    }
}
