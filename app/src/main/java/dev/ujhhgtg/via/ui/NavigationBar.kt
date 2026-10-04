package dev.ujhhgtg.via.ui

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Typeface
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isEmpty
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.skins.setSkinImageResource
import java.util.Locale
import java.util.WeakHashMap

/** mark.via.common.widget.g0: original five controls and embedded-address layout. */
class NavigationBar(context: Context) : LinearLayout(context) {
    private val clickTimes = WeakHashMap<View, Long>()
    var onItemClick: ((Int) -> Unit)? = null
    var onItemLongClick: ((Int) -> Boolean)? = null
    private val addressContainer = FrameLayout(context)
    private val tabContainer = FrameLayout(context)
    private val tabIcon = ImageView(context)
    val tabCount = TextView(context)
    val backButton: ImageView
    val forwardButton: ImageView
    val homeButton: ImageView
    val menuButton: ImageView

    init {
        orientation = HORIZONTAL
        addressContainer.setPaddingRelative(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics).toInt(), 0, 0, 0)
        addView(addressContainer, LayoutParams(0, -1, 0f))
        backButton = button(0, R.drawable.chevron_left, R.string.operation_goback)
        forwardButton = button(1, R.drawable.chevron_right, R.string.operation_goforward)
        homeButton = button(2, R.drawable.house, R.string.desc_home)
        addView(backButton); addView(forwardButton); addView(homeButton)
        tabIcon.setSkinImageResource(R.drawable.tab_square)
        tabIcon.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        tabIcon.setColorFilter(controlColor())
        val padding = resources.getDimensionPixelSize(R.dimen.menu_control_padding)
        tabIcon.setPadding(padding, padding, padding, padding)
        tabCount.gravity = Gravity.CENTER
        tabCount.typeface = Typeface.DEFAULT_BOLD
        tabCount.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11f)
        tabCount.setTextColor(controlColor())
        tabCount.setPaddingRelative(0, 0, 0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics).toInt())
        tabCount.setBackgroundResource(R.drawable.rounded_rect_ripple)
        listeners(tabCount, 3)
        tabCount.text = "1"
        tabContainer.addView(tabIcon, FrameLayout.LayoutParams(-1, -1))
        tabContainer.addView(tabCount, FrameLayout.LayoutParams(-1, -1))
        addView(tabContainer, LayoutParams(0, -1, 1f))
        menuButton = button(4, R.drawable.menu, R.string.desc_menu)
        addView(menuButton)
        postInvalidate()
    }

    private fun controlColor(): Int = context.obtainStyledAttributes(intArrayOf(R.attr.viaSubtleColor)).let {
        try { it.getColor(0, 0) } finally { it.recycle() }
    }

    private fun button(index: Int, drawable: Int, label: Int) = ImageView(context).apply {
        layoutParams = LayoutParams(0, -1, 1f)
        setSkinImageResource(drawable)
        setColorFilter(controlColor())
        val padding = resources.getDimensionPixelSize(R.dimen.menu_control_padding)
        setPadding(padding, padding, padding, padding)
        setBackgroundResource(R.drawable.rounded_rect_ripple)
        contentDescription = context.getString(label)
        listeners(this, index)
    }

    private fun listeners(view: View, index: Int) {
        view.setOnClickListener {
            val time = SystemClock.elapsedRealtime()
            val previous = clickTimes[it]
            if (previous == null || kotlin.math.abs(time - previous) > 300) {
                clickTimes[it] = time
                onItemClick?.invoke(index)
            }
        }
        view.setOnLongClickListener { onItemLongClick?.invoke(index) == true }
    }

    fun embedAddress(view: View) {
        if (addressContainer.isEmpty()) addressContainer.addView(view)
        else {
            if (addressContainer.getChildAt(0) === view) return
            addressContainer.removeAllViews()
            addressContainer.addView(view)
        }
        if (measuredWidth > 0) sizeAddress(measuredWidth)
    }

    fun removeAddress() {
        if (addressContainer.isEmpty()) return
        backButton.visibility = VISIBLE
        forwardButton.visibility = VISIBLE
        (addressContainer.layoutParams as LayoutParams).weight = 0f
        addressContainer.removeAllViews()
    }

    fun setTabPosition(position: Int, count: Int) {
        if (position < 0) { tabIcon.alpha = 1f; setTabCount(count) }
        else {
            tabCount.text = String.format(Locale.ROOT, "%d/%d", position, count)
            tabCount.contentDescription = resources.getString(R.string.tab_position_description, position, count)
            tabIcon.alpha = .15f
        }
    }

    fun setTabCount(count: Int) {
        tabCount.text = if (count <= 99) count.toString() else ":)"
        tabCount.contentDescription = resources.getQuantityString(R.plurals.open_tabs, count, count)
    }

    /** g0.j: the original tab-counter cell gives a short 0→−30%→0 feedback. */
    fun bounceTabCount() {
        ObjectAnimator.ofFloat(tabContainer, "translationY", 0f, tabContainer.height * -.3f, 0f).apply {
            duration = 280
            interpolator = PathInterpolator(.2f, .2f, .8f, .8f)
        }.start()
    }

    fun setAccentColor(color: Int) {
        listOf(backButton, forwardButton, homeButton, menuButton, tabIcon).forEach { it.setColorFilter(color) }
        tabCount.setTextColor(color)
    }

    private fun sizeAddress(width: Int) {
        if (addressContainer.isEmpty()) return
        val available = (width / resources.displayMetrics.density + .5f).toInt()
        val unit = if (available < 420) available / 7 else if (available > 900) 72 else 60
        val units = if (unit <= 0) 6 else available / unit
        val showArrows = units > 8
        backButton.visibility = if (showArrows) VISIBLE else GONE
        forwardButton.visibility = if (showArrows) VISIBLE else GONE
        (addressContainer.layoutParams as LayoutParams).weight = (units - 3 - if (showArrows) 2 else 0).toFloat()
        post { requestLayout() }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        sizeAddress(width)
    }
}
