package dev.ujhhgtg.via.ui

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.widget.FrameLayout
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import dev.ujhhgtg.via.R

/** f8.c: in-browser fragment overlay, bounded by the host's toolbar/content container. */
abstract class BrowserOverlayFragment : Fragment() {
    protected lateinit var overlay: FrameLayout
        private set
    protected lateinit var content: View
        private set
    // f8.c o0: the sheet gravity is shared with subclasses (l0 reads it for
    // reverse layout and the entrance direction).
    protected var gravity = Gravity.BOTTOM or Gravity.END
        private set
    private var width = ViewGroup.LayoutParams.WRAP_CONTENT
    private var height = ViewGroup.LayoutParams.WRAP_CONTENT
    /** The source enables c8.f8.l in the caller; text translation does not call kb(). */
    protected open val scrimEnabled = true
    private var homepage = false
    private lateinit var scrim: View
    private var scrimOpacity = 1f

    override fun onAttach(context: Context) {
        super.onAttach(context)
        arguments?.let { gravity = it.getInt("gravity", gravity); width = it.getInt("width", width); height = it.getInt("height", height); homepage = it.getBoolean("homepage", false) }
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        overlay = FrameLayout(requireContext()).apply { layoutParams = FrameLayout.LayoutParams(-1, -1) }
        content = createContent(inflater, container, savedInstanceState)
        // c8.f8.l is outside the animated f8.c root in Via. Keep its properties
        // independent here; AndroidX retargets the returned fragment animator.
        val blurred = Build.VERSION.SDK_INT >= 31 && dev.ujhhgtg.via.data.BrowserPreferences(requireContext()).blurEffect
        scrimOpacity = if (blurred) .5f else 1f
        scrim = View(requireContext()).apply {
            // c8.s6.R1 -> a9 -> c8.f8.m replaces the constructor's #40808080:
            // transparent when the content/backdrop are blurred, #70808080 otherwise.
            background = (if (blurred) 0 else 0x70808080).toDrawable()
            alpha = scrimOpacity
            visibility = if (scrimEnabled) View.VISIBLE else View.GONE
            isClickable = true
            contentDescription = getString(R.string.hide_popup_window)
            setOnClickListener { if (!parentFragmentManager.isStateSaved) parentFragmentManager.popBackStack() }
        }
        overlay.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        overlay.addView(content, FrameLayout.LayoutParams(width, height, gravity))
        applyBackground(homepage)
        return overlay
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setAccessibilityPaneTitle(content, paneTitle())
        content.post { if (isAdded && content.isAttachedToWindow && content.isShown) focusContent() }
    }
    protected open fun paneTitle(): CharSequence = getString(R.string.app_name)
    protected abstract fun createContent(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View
    /** g6.y.K: the pane takes focus and announces itself for accessibility. */
    protected open fun focusContent() {
        if (!content.isAttachedToWindow || !content.isShown) return
        content.requestFocus()
        content.performAccessibilityAction(64, null)
    }
    fun moveTo(gravity: Int) {
        if (this.gravity == gravity || !::content.isInitialized) return
        this.gravity = gravity; arguments?.putInt("gravity", gravity)
        content.animate().alpha(0f).setDuration(120).withEndAction {
            overlay.removeView(content); overlay.addView(content, FrameLayout.LayoutParams(width, height, gravity))
            applyBackground(false); content.animate().alpha(1f).setDuration(120).start()
        }.start()
    }
    private fun applyBackground(allCorners: Boolean) {
        val radius = resources.getDimensionPixelSize(R.dimen.menu_corner_radius).toFloat()
        val bottom = gravity and Gravity.BOTTOM == Gravity.BOTTOM
        val upper = if (allCorners || bottom) radius else 0f
        val lower = if (allCorners || !bottom) radius else 0f
        val attributes = requireContext().obtainStyledAttributes(intArrayOf(R.attr.viaSurfaceColor))
        val color = try { attributes.getColor(0, 0) } finally { attributes.recycle() }
        content.background = GradientDrawable().apply { setColor(color); cornerRadii = floatArrayOf(upper, upper, upper, upper, lower, lower, lower, lower) }
    }
    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? = null
    /**
     * f8.c.y1 animates its scrim-free root: 160 ms, AccelerateInterpolator,
     * alpha 0<->1 and translationY +/-120 raw pixels. c8.f8.l/i independently
     * fade the stationary scrim for 160 ms using ViewPropertyAnimator's default
     * AccelerateDecelerateInterpolator.
     *
     * DefaultSpecialEffectsController calls animator.setTarget(fragment.view).
     * AnimatorSet forwards that call to every ObjectAnimator, so assigning an
     * ObjectAnimator to content here would still translate the entire overlay.
     * ValueAnimator updates retain the two explicit view references after that call.
     */
    override fun onCreateAnimator(transit: Int, enter: Boolean, nextAnim: Int): Animator? {
        val root = view ?: return null
        val pane = content
        val dim = scrim
        val offset = if (gravity and Gravity.BOTTOM == Gravity.BOTTOM) 120f else -120f
        val paneAnimator = ValueAnimator.ofFloat(if (enter) 0f else 1f, if (enter) 1f else 0f).apply {
            duration = 160L
            interpolator = AccelerateInterpolator()
            addUpdateListener {
                val visible = it.animatedValue as Float
                pane.alpha = visible
                pane.translationY = offset * (1f - visible)
            }
        }
        val dimAnimator = ValueAnimator.ofFloat(if (enter) 0f else scrimOpacity, if (enter) scrimOpacity else 0f).apply {
            duration = 160L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { dim.alpha = it.animatedValue as Float }
        }
        return AnimatorSet().apply {
            playTogether(paneAnimator, dimAnimator)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    // Fragment 1.7 tests visibility *after* onCreateAnimator. Alpha zero
                    // marks an entering view as INVISIBLE, so normalizing the root before
                    // this callback would make AnimatorEffect skip the whole animation.
                    root.alpha = 1f
                    root.translationY = 0f
                }
            })
        }
    }
}
