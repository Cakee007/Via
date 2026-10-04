package dev.ujhhgtg.via.video

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.AudioManager
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.common.WindowInsetsHelper
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.skins.SkinResources
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Port of com.tuyafeng.support.widget.v fullscreen video chrome. */
class FullscreenVideoControls @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr) {
    /** v.b */
    interface Controller {
        /** v.b.a: reports 0 (unknown), 1 (paused) or 2 (playing). */
        fun playbackState(callback: (Int) -> Unit)
        /** v.b.b */
        fun seekBy(seconds: Int)
        /** v.b.c */
        fun setPlaybackRate(rate: Float)
        /** v.b.d */
        val playbackRate: Float
        /** v.b.getDuration */
        val duration: Float
    }

    // v.B / v.C: captured once by the constructor.  The controls are not
    // present when the platform cannot offer the feature.
    private val pipSupported = VideoPictureInPicture.supported(context)
    private val orientationSupported = VideoWindowPolicy.orientationSupported(context)
    private val preferences = BrowserPreferences(context)
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    private val gestureSlop = 12

    private val topScrim: View
    private val title: TextView
    private val lock: ImageView
    private val rotation: ImageView
    private val pip: ImageView
    private val speed: FrameLayout
    private val speedText: TextView
    private val progress: TextView

    private var controller: Controller? = null
    private var systemOrientation = -1
    private var hasTitle = false
    private var speedAdjustable = false
    private var progressGestureEnabled = false
    private var volumeGestureEnabled = false
    private var brightnessGestureEnabled = false
    private var videoIsLandscape = false
    private var locked = false
    private var inPip = false
    private var toolbarEnabled = false

    // v.M / v.L / v.N: the stored choice (auto, landscape, portrait, follow system).
    private var explicitOrientation: Boolean
    private var landscapeOrientation: Boolean
    private var followSystemOrientation: Boolean

    private var gesture = 0
    private var startVolume = 0
    private var startBrightness = -1f
    private var duration = 0f
    private var seekOffset = 0
    private var rate = 1f
    private var downX = 0
    private var downY = 0
    private var lastX = 0
    private var lastY = 0

    /** v.U */
    private val hideTask = Runnable { if (isShown) hideChrome() }

    init {
        val choice = preferences.videoOrientation
        explicitOrientation = choice == 1 || choice == 2
        landscapeOrientation = choice == 1
        followSystemOrientation = choice == 3

        LayoutInflater.from(context).inflate(R.layout.video_controls, this, true)
        title = findViewById(R.id.video_title)
        topScrim = findViewById(R.id.video_top_scrim)
        topScrim.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(-1879048192, 0)).apply { shape = GradientDrawable.RECTANGLE }
        lock = findViewById(R.id.video_lock)
        lock.background = controlBackground()
        lock.setImageDrawable(SkinResources.drawable(context, R.drawable.video_lock, "ic_player_unlock"))
        rotation = findViewById(R.id.video_rotation)
        rotation.background = controlBackground()
        rotation.setImageDrawable(SkinResources.drawable(context, R.drawable.video_orientation, "ic_player_rotation"))
        pip = findViewById(R.id.video_pip)
        pip.background = controlBackground()
        pip.setImageDrawable(SkinResources.drawable(context, R.drawable.video_pip, "ic_player_pip"))
        speedText = findViewById(R.id.video_speed_text)
        speed = findViewById(R.id.video_speed)
        updateSpeedDescription()
        speed.background = controlBackground()
        val speedIcon = findViewById<ImageView>(R.id.video_speed_icon)
        speedIcon.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        speedIcon.setImageDrawable(SkinResources.drawable(context, R.drawable.video_progress_icon, "ic_player_speed"))
        progress = findViewById(R.id.video_note)
        progress.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Int.MIN_VALUE)
            cornerRadii = FloatArray(8) { dp(18).toFloat() }
        }
        speed.setOnClickListener { showSpeedMenu(it) }
        lock.setOnClickListener { toggleLock() }
        pip.setOnClickListener { enterPictureInPicture() }
        rotation.setOnClickListener { showOrientationMenu(it) }
        title.visibility = GONE
        topScrim.visibility = GONE
        speed.visibility = GONE
        lock.visibility = GONE
        rotation.visibility = GONE
        pip.visibility = GONE
        if (pipSupported) {
            VideoPictureInPicture.updateActions(context, 2, false)
            // v.j keeps the PiP source rectangle in sync with the fullscreen
            // control host after insets, rotation, or cutout changes.
            addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (left != oldLeft || right != oldRight || top != oldTop || bottom != oldBottom) {
                    val rect = Rect()
                    getGlobalVisibleRect(rect)
                    VideoPictureInPicture.updateSourceRect(context, rect)
                }
            }
        }
        installInsets()
    }

    /** g6.g().c(24dp).h(0x33000000).j(0x66000000).a(): a ripple over a translucent rounded surface. */
    private fun controlBackground(): Drawable {
        fun surface(color: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadii = FloatArray(8) { dp(24).toFloat() }
        }
        return RippleDrawable(ColorStateList.valueOf(0x66000000), surface(0x33000000), surface(-16777216))
    }

    /** v.y: the first control host keeps clear of system bars, or of a display cutout when PiP is available. */
    private fun installInsets() {
        val root = findViewById<RelativeLayout>(R.id.video_controls_root) ?: return
        if (!pipSupported) {
            WindowInsetsHelper.apply(root)
            return
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets -> avoidCutout(view, insets) }
    }

    /** v.d */
    private fun avoidCutout(view: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val cutout = insets.displayCutout
        val width = view.measuredWidth
        val height = view.measuredHeight
        if (cutout != null && width > 0 && height > 0) {
            view.setPadding(0, 0, cutout.safeInsetRight, 0)
            var top = 0
            val x = dp(16)
            val y = height / 2
            for (rect in cutout.boundingRects) {
                if (!rect.contains(x, y)) continue
                top = max(top, rect.bottom - y + dp(32))
            }
            val params = pip.layoutParams as RelativeLayout.LayoutParams
            params.topMargin = top
            pip.layoutParams = params
        }
        return insets
    }

    // ---- public surface ----

    /** v.setControllerCallback */
    fun setControllerCallback(value: Controller?) {
        controller = value
        if (value == null) {
            setProgressGestureEnabled(false)
            setSpeedAdjustable(false)
            return
        }
        duration = value.duration
        rate = value.playbackRate
        speedText.text = String.format(Locale.ROOT, "%sX", rate)
        updateSpeedDescription()
        val known = duration > 0f
        setSpeedAdjustable(speedAdjustable && known)
        setProgressGestureEnabled(progressGestureEnabled && known)
    }

    /** v.E */
    fun setVideoSize(w: Int, h: Int) {
        videoIsLandscape = w > h
        applyOrientation()
        VideoPictureInPicture.updateVideoSize(context, w, h)
    }

    fun setSystemOrientation(value: Int) { systemOrientation = value }

    /** v.setTitle; the original shows the text view for an empty title and hides it otherwise. */
    fun setTitle(value: String?) {
        val empty = value.isNullOrEmpty()
        hasTitle = !empty
        title.visibility = if (!empty) GONE else VISIBLE
        title.text = value
    }

    fun setToolbarEnabled(value: Boolean) { cancelHide(); hideChrome(); toolbarEnabled = value }

    fun setSpeedAdjustable(value: Boolean) {
        speedAdjustable = value
        if (!value && speed.isVisible) speed.visibility = GONE
    }

    fun setProgressGestureEnabled(value: Boolean) { progressGestureEnabled = value }
    fun setVolumeGestureEnabled(value: Boolean) { volumeGestureEnabled = value }
    fun setBrightnessGestureEnabled(value: Boolean) { brightnessGestureEnabled = value }

    fun setInPipMode(value: Boolean) {
        if (inPip == value) return
        inPip = value
        if (value) hideChrome() else showChrome()
    }

    val isInPipMode: Boolean get() = inPip

    // ---- gestures ----

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x.toInt()
                downY = event.y.toInt()
                val edge = dp(48)
                val lowerEdge = edge * 3 / 2
                if (downY >= edge && downY <= height - lowerEdge && downX >= edge / 2 && downX <= width - edge) {
                    lastX = downX
                    lastY = downY
                    gesture = 0
                } else {
                    gesture = 1
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (gesture == 1) return super.dispatchTouchEvent(event)
                val dx = event.x.toInt() - lastX
                val dy = event.y.toInt() - lastY
                lastX = event.x.toInt()
                lastY = event.y.toInt()
                if (!(locked || gesture != 0 || (abs(dx) <= gestureSlop && abs(dy) <= gestureSlop))) {
                    if (abs(dy).toDouble() > abs(dx).toDouble() * 1.4) {
                        val third = width / 3
                        if ((downX < third && brightnessGestureEnabled) || (downX > width * 2 / 3 && volumeGestureEnabled)) {
                            startVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                            startBrightness = if (startBrightness == -1f) VideoWindowPolicy.systemBrightness(context) else VideoWindowPolicy.windowBrightness(context)
                            val brightness = downX < width / 3
                            gesture = if (brightness) 2 else 3
                            showProgressIcon(if (brightness) R.drawable.video_brightness else R.drawable.video_volume)
                        }
                    } else if (abs(dx) > abs(dy) * 3) {
                        if (progressGestureEnabled) {
                            gesture = 4
                            showProgressIcon(0)
                        }
                    } else {
                        gesture = 1
                    }
                }
                if (gesture == 3 || gesture == 2 || gesture == 4) {
                    if (gesture == 4) {
                        adjustProgress((lastX - downX).toFloat() / width.toFloat())
                    } else {
                        val delta = (downY - lastY).toFloat() * 2f / height.toFloat()
                        if (gesture == 2) adjustBrightness(delta) else adjustVolume(delta)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (gesture != 3 && gesture != 2 && gesture != 4) {
                    if (gesture == 0) {
                        if (lock.isVisible) {
                            cancelHide()
                            hideTask.run()
                        } else if (toolbarEnabled) {
                            showAndScheduleHide()
                        }
                    }
                } else {
                    hide(progress)
                    if (gesture == 4) seek()
                }
                gesture = 0
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        VideoWindowPolicy.setBrightness(context, -1f)
        startBrightness = -1f
        super.onDetachedFromWindow()
    }

    /** v.B */
    @SuppressLint("SetTextI18n")
    private fun adjustBrightness(delta: Float) {
        var value = startBrightness + delta
        if (value > 1f) value = 1f
        if (value.toDouble() < 0.01) value = 0.01f
        VideoWindowPolicy.setBrightness(context, value)
        progress.text = "${(value * 100f).toInt()}%"
    }

    /** v.C */
    private fun adjustProgress(fraction: Float) {
        seekOffset = (fraction * min(duration, 300f)).toInt()
        val sign = if (seekOffset >= 0) "+" else "-"
        @SuppressLint("DefaultLocale") // v.C formats with the default locale
        val text = String.format("%s%02d:%02d", sign, abs(seekOffset / 60), abs(seekOffset % 60))
        progress.text = text
    }

    /** v.D */
    @SuppressLint("SetTextI18n")
    private fun adjustVolume(delta: Float) {
        var fraction = startVolume.toFloat() / maxVolume.toFloat() + delta
        if (fraction < 0f) fraction = 0f
        if (fraction > 1f) fraction = 1f
        val volume = (maxVolume.toFloat() * fraction).toInt()
        progress.text = "${(fraction * 100f).toInt()}%"
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
    }

    /** v.s */
    private fun seek() {
        controller?.seekBy(seekOffset)
        seekOffset = 0
    }

    /** v.G: the compound icon is explicitly bounded to 24dp before the indicator is shown. */
    private fun showProgressIcon(resource: Int) {
        val drawable = if (resource != 0) {
            val size = dp(24)
            context.getDrawable(resource)?.apply { setBounds(0, 0, size, size) }
        } else {
            null
        }
        progress.setCompoundDrawablesRelative(drawable, null, null, null)
        show(progress)
    }

    // ---- buttons ----

    /** v.A */
    private fun toggleLock() {
        cancelHide()
        locked = !locked
        lock.setImageDrawable(
            if (locked) SkinResources.drawable(context, R.drawable.video_lock_locked, "ic_player_lock")
            else SkinResources.drawable(context, R.drawable.video_lock, "ic_player_unlock"),
        )
        lock.contentDescription = context.getString(if (locked) R.string.unlock else R.string.lock)
        showAndScheduleHide()
        applyOrientation()
    }

    /** v.e */
    private fun enterPictureInPicture() {
        if (!pipSupported) return
        if (!VideoPictureInPicture.canEnter(context)) {
            Toast.makeText(context, context.getString(R.string.title_permission_denied), Toast.LENGTH_SHORT).show()
            return
        }
        if (VideoPictureInPicture.enter(context)) {
            // v.e sets the in-PiP state before asking its controller for
            // the paused/playing state used to build the actions.
            setInPipMode(true)
            controller?.playbackState { state -> VideoPictureInPicture.updateActions(context, state, true) }
        }
    }

    /** v.g */
    private fun showOrientationMenu(anchor: View) {
        cancelHide()
        val labels = arrayOf(
            context.getString(R.string.orientation_auto),
            context.getString(R.string.orientation_landscape),
            context.getString(R.string.orientation_portrait),
            context.getString(R.string.follow_system),
        )
        val selected = preferences.videoOrientation
        (anchor.context as? Activity)?.let { activity ->
            ViaDialog(activity)
                .maximumWidth(dp(110))
                .onDismiss { showAndScheduleHide() }
                .highlightedChoice(labels, selected) { which ->
                    // v.a
                    explicitOrientation = which == 1 || which == 2
                    landscapeOrientation = which == 1
                    followSystemOrientation = which == 3
                    preferences.videoOrientation = which
                    applyOrientation()
                }
                .showAnchored(anchor)
        }
    }

    /** v.l */
    private fun showSpeedMenu(anchor: View) {
        cancelHide()
        val labels = Array(SPEEDS.size) { SPEEDS[it].toString() }
        var selected = -1
        for (index in SPEEDS.indices) if (SPEEDS[index] == rate) selected = index
        (anchor.context as? Activity)?.let { activity ->
            ViaDialog(activity)
                .maximumWidth(dp(78))
                .onDismiss { showAndScheduleHide() }
                .highlightedChoice(labels, selected) { which ->
                    val current = controller
                    if (current != null) {
                        rate = SPEEDS[which]
                        current.setPlaybackRate(rate)
                        speedText.text = String.format(Locale.ROOT, "%sX", rate)
                        updateSpeedDescription()
                    }
                }
                .showAnchored(anchor)
        }
    }

    /** v.J */
    private fun updateSpeedDescription() {
        speed.contentDescription = context.getString(R.string.playback_speed_description, rate.toString())
    }

    /**
     * v.H: auto follows the video ratio; locked mode preserves a compatible
     * current rotation and otherwise requests the fixed portrait/landscape.
     */
    private fun applyOrientation() {
        if (!orientationSupported) return
        if (!isShown) return
        if (followSystemOrientation) {
            VideoWindowPolicy.requestOrientation(context, systemOrientation)
            return
        }
        val landscape = if (explicitOrientation) landscapeOrientation else videoIsLandscape
        val requested: Int
        if (locked) {
            val current = VideoWindowPolicy.currentOrientation(context)
            requested = if (landscape) {
                if (current == 0 || current == 8) current else 0
            } else {
                if (current == 1 || current == 8) current else 1
            }
        } else {
            requested = if (landscape) 6 else 7
        }
        VideoWindowPolicy.requestOrientation(context, requested)
    }

    // ---- chrome visibility ----

    /** v.t */
    private fun cancelHide() { speedText.removeCallbacks(hideTask) }

    /** v.F */
    private fun showAndScheduleHide() {
        if (!isShown) return
        speedText.removeCallbacks(hideTask)
        speedText.postDelayed(hideTask, 3000L)
        showChrome()
    }

    /** v.v */
    private fun showChrome() {
        show(lock)
        if (locked) {
            hide(title)
            hide(topScrim)
            hide(speed)
            hide(rotation)
            hide(pip)
            return
        }
        if (hasTitle) {
            show(title)
            show(topScrim)
        }
        if (speedAdjustable) show(speed)
        if (orientationSupported) show(rotation)
        if (pipSupported) show(pip)
    }

    /** v.x */
    private fun hideChrome() {
        hide(title)
        hide(topScrim)
        hide(speed)
        hide(rotation)
        hide(lock)
        hide(pip)
    }

    /** v.u */
    private fun show(view: View?) {
        if (view == null || view.isVisible) return
        view.alpha = 0f
        view.visibility = VISIBLE
        view.animate().alpha(1f).setInterpolator(PathInterpolator(.2f, .2f, .8f, .8f)).setDuration(150L).start()
    }

    /** v.w */
    private fun hide(view: View?) {
        if (view == null || view.isGone) return
        view.animate().alpha(0f).withEndAction { view.visibility = GONE }.setInterpolator(PathInterpolator(.2f, .2f, .8f, .8f)).setDuration(150L).start()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()

    private companion object {
        /** v.V */
        val SPEEDS = floatArrayOf(5f, 3f, 2f, 1.5f, 1.25f, 1f, .75f, .5f)
    }
}
