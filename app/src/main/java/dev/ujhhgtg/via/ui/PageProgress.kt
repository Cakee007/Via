package dev.ujhhgtg.via.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ProgressBar
import kotlin.math.abs
import kotlin.math.min

/** com.tuyafeng.support.widget.w; the original progress easing and completion fade. */
class PageProgress(private val bar: ProgressBar) {
    private val maximum = bar.max * 10
    private val plateau = maximum / 10 * 8
    private var animation: ValueAnimator? = null
    private var state = 0
    init { bar.visibility = View.GONE; bar.max = maximum }

    fun update(progress: Int, immediate: Boolean = false) {
        val target = progress * 10
        if (state == 0 && target < maximum) { bar.alpha = 1f; bar.visibility = View.VISIBLE; state = 1 }
        if (state == 0) return
        val previous = bar.progress
        if (target < maximum && previous != 0 && abs(target - previous) < maximum / 8) return
        animation?.let { it.removeAllListeners(); it.cancel(); bar.alpha = 1f }
        var duration = abs(previous - target).toLong()
        val next = ValueAnimator.ofFloat(0f, 1f)
        when {
            target < plateau -> if (immediate || previous < target) {
                val extended = abs(plateau - target) * 30L + duration
                val ratio = extended.toFloat() / duration.toFloat()
                next.addUpdateListener {
                    val fraction = it.animatedValue as Float
                    bar.progress = (previous + min(ratio * fraction, 1f) * (target - previous) + fraction * abs(plateau - target)).toInt()
                }
                duration = extended
            } else {
                duration = abs(plateau - previous) * 30L
                next.addUpdateListener { val fraction = it.animatedValue as Float; bar.progress = (previous + fraction.toDouble() * fraction * abs(plateau - previous)).toInt() }
            }
            target < maximum -> next.addUpdateListener { bar.progress = (previous + (it.animatedValue as Float) * (target - previous)).toInt() }
            else -> {
                duration /= 2
                next.addUpdateListener { val fraction = it.animatedValue as Float; bar.progress = (previous + (maximum - previous) * fraction).toInt(); bar.alpha = 1f - fraction }
                next.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { bar.visibility = View.GONE; bar.progress = 0; state = 0 }
                })
            }
        }
        animation = next
        next.interpolator = DecelerateInterpolator(); next.duration = duration; next.start()
    }
}
