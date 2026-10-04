package dev.ujhhgtg.via.settings

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.skins.setSkinImageResource
import dev.ujhhgtg.via.ui.NavigationBar
import dev.ujhhgtg.via.ui.dp

/** ib.i0: the 300dp model retains its layout size while its entire surface is scaled to 80%. */
internal class ToolbarPreviewView(context: Context) : FrameLayout(context) {
    private val topToolbar = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val bottomToolbar = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val address = PreviewAddressBar(context)
    private val navigation = NavigationBar(context)
    private val page = View(context)
    private val tabs = PreviewTabBar(context)
    private val radius = resources.getDimensionPixelSize(R.dimen.menu_corner_radius).toFloat()

    init {
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        layoutParams = RecyclerView.LayoutParams(-1, -2)
        val surface = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = radius
                setStroke(context.dp(2f), 0x30808080)
            }
            addView(topToolbar, LinearLayout.LayoutParams(-1, -2))
            addView(page, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(bottomToolbar, LinearLayout.LayoutParams(-1, -2))
            scaleX = .8f
            scaleY = .8f
        }
        // h6.a.n(1, 300) truncates TypedValue.applyDimension, unlike g6.y.h.
        val height = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 300f, resources.displayMetrics).toInt()
        addView(surface, LayoutParams(-1, height, Gravity.CENTER_HORIZONTAL))
        isEnabled = false
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = true

    fun bind(mode: Int, showTabs: Boolean, titleMode: Int) {
        topToolbar.removeAllViews()
        bottomToolbar.removeAllViews()
        navigation.removeAddress()
        fun LinearLayout.addAddress() = addView(address, LinearLayout.LayoutParams(-1, context.dp(48f)))
        fun LinearLayout.addNavigation() = addView(navigation, LinearLayout.LayoutParams(-1, context.dp(48f)))
        fun LinearLayout.addTabs() = addView(tabs, LinearLayout.LayoutParams(-1, context.dp(36f)))
        when (mode) {
            0 -> { topToolbar.addAddress(); bottomToolbar.addNavigation() }
            1 -> { navigation.embedAddress(address); topToolbar.addNavigation(); topToolbar.addTabs() }
            2 -> { bottomToolbar.addTabs(); navigation.embedAddress(address); bottomToolbar.addNavigation() }
            3 -> { bottomToolbar.addAddress(); bottomToolbar.addNavigation() }
        }
        tabs.visibility = if (showTabs && mode in 1..2) VISIBLE else GONE
        address.bind(mode, titleMode)
        // ib.i0.t: only the page's exposed corners are rounded; its gray fill
        // is separate from the transparent outline and toolbar backgrounds.
        val topRadius = if (mode == 2 || mode == 3) radius else 0f
        val bottomRadius = if (mode == 1) radius else 0f
        page.background = GradientDrawable().apply {
            setColor(0x18808080)
            cornerRadii = floatArrayOf(topRadius, topRadius, topRadius, topRadius,
                bottomRadius, bottomRadius, bottomRadius, bottomRadius)
        }
    }
}

/** n0.m/j/k: address controls use 48dp cells with 13dp horizontal padding. */
private class PreviewAddressBar(context: Context) : LinearLayout(context) {
    private val title = TextView(context).apply {
        background = ContextCompat.getDrawable(context, R.drawable.focus_outline)
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, Color.BLACK))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.toolbar_preview_address_text_size).toFloat())
        textDirection = TEXT_DIRECTION_LOCALE
        ellipsize = null
        setSingleLine(true)
        setFadingEdgeLength(context.dp(24f))
        isHorizontalFadingEdgeEnabled = true
    }

    init {
        orientation = HORIZONTAL
        addView(control(R.drawable.search, R.string.site_info), LayoutParams(context.dp(48f), -1))
        addView(title, LayoutParams(0, -1, 1f))
        addView(control(R.drawable.resource_sniffer_button, R.string.resource_sniffer).apply { visibility = GONE }, LayoutParams(context.dp(48f), -1))
        addView(control(R.drawable.reload, R.string.operation_reload), LayoutParams(context.dp(48f), -1))
    }

    private fun control(icon: Int, label: Int) = ImageView(context).apply {
        setPaddingRelative(context.dp(13f), 0, context.dp(13f), 0)
        background = ContextCompat.getDrawable(context, R.drawable.circle_ripple)
        setSkinImageResource(icon)
        contentDescription = context.getString(label)
        setColorFilter(settingsColor(context, R.attr.viaSubtleColor, Color.DKGRAY))
    }

    fun bind(mode: Int, titleMode: Int) {
        title.text = when (titleMode) {
            0 -> context.getString(R.string.action_webpage)
            1 -> "https://example.com/"
            else -> "example.com"
        }
        if (mode == 0) {
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(context.dp(12f), 0, context.dp(12f), 0)
        } else {
            val inset = context.dp(4f)
            val horizontal = if (mode == 3) inset * 4 else inset
            val padding = if (mode == 3) horizontal else 0
            setPadding(padding, 0, padding, 0)
            background = LayerDrawable(arrayOf(GradientDrawable().apply {
                setColor(if (BrowserPreferences(context).isNightMode) 0x22ffffff else 0x10000000)
                cornerRadius = resources.getDimensionPixelSize(R.dimen.menu_corner_radius).toFloat()
            })).apply { setLayerInset(0, horizontal, inset, horizontal, inset) }
        }
    }
}

/** ib.i0's miniature tab row uses the same b0 icon/title/close structure as the tab bar. */
private class PreviewTabBar(context: Context) : LinearLayout(context) {
    private val tab = LinearLayout(context).apply {
        orientation = HORIZONTAL
        addView(ImageView(context).apply {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            setPaddingRelative(context.dp(13f), 0, context.dp(13f), 0)
            setSkinImageResource(R.drawable.globe)
            setColorFilter(settingsColor(context, R.attr.viaSubtleColor, Color.DKGRAY))
        }, LayoutParams(context.dp(48f), -1))
        addView(TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
            setSingleLine()
            gravity = Gravity.CENTER_VERTICAL
            setFadingEdgeLength(context.dp(24f))
            isHorizontalFadingEdgeEnabled = false
            textDirection = TEXT_DIRECTION_LOCALE
            typeface = BrowserPreferences(context).selectedTypeface()
            ellipsize = TextUtils.TruncateAt.END
            setText(R.string.action_webpage)
            setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, Color.BLACK))
        }, LayoutParams(0, -1, 1f))
        addView(ImageView(context).apply {
            // b0 uses h6.a.N(1, 14), so this particular padding truncates dp.
            val padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14f, resources.displayMetrics).toInt()
            setPaddingRelative(padding, 0, padding, 0)
            setSkinImageResource(R.drawable.close)
            setColorFilter(settingsColor(context, R.attr.viaSubtleColor, Color.DKGRAY))
            contentDescription = context.getString(R.string.operation_closetab)
            background = controlRipple(context)
        }, LayoutParams(context.dp(48f), -1))
    }
    private val space = Space(context)

    init {
        orientation = HORIZONTAL
        visibility = GONE
        addView(tab, LayoutParams(0, -1))
        addView(space, LayoutParams(0, -1))
        addView(ImageView(context).apply {
            val padding = (context.dp(36f) - context.dp(22f)) / 2
            setPaddingRelative(0, padding, 0, padding)
            setSkinImageResource(R.drawable.plus)
            setColorFilter(settingsColor(context, R.attr.viaSubtleColor, Color.DKGRAY))
        }, LayoutParams(0, -1, 1f))
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width <= 0) return
        val available = (width / resources.displayMetrics.density + .5f).toInt()
        val unit = if (available < 420) available / 7 else if (available > 900) 72 else 60
        if (unit <= 0) return
        val units = available / unit
        val first = tab.layoutParams as LayoutParams
        first.weight = maxOf(3, (units - 1) / 2 - 1).toFloat()
        tab.layoutParams = first
        // ib.i0.u deliberately reuses the first child's LayoutParams object.
        val second = tab.layoutParams as LayoutParams
        second.weight = maxOf(units - first.weight - 1f, 1f)
        space.layoutParams = second
        post { requestLayout() }
    }
}

/** hb.l7.n3 / support.widget.c: original three fullscreen illustrations. */
internal class ToolbarFullscreenChoices(context: Context, selected: Int, onSelected: (Int) -> Unit) : LinearLayout(context) {
    init {
        val labels = intArrayOf(R.string.fullscreen_0, R.string.fullscreen_1, R.string.fullscreen_2)
        val icons = intArrayOf(R.drawable.toolbar_fullscreen_normal, R.drawable.toolbar_fullscreen_status, R.drawable.toolbar_fullscreen_all)
        val selectedColor = settingsColor(context, R.attr.viaAccentColor, Color.BLUE)
        val textColor = settingsColor(context, R.attr.viaPrimaryTextColor, Color.BLACK)
        val iconColor = settingsColor(context, R.attr.viaSubtleColor, Color.DKGRAY)
        labels.forEachIndexed { index, label ->
            val highlighted = index == selected
            addView(TextView(context).apply {
                val icon = ContextCompat.getDrawable(context, icons[index])!!.mutate().apply {
                    setBounds(0, 0, context.dp(50f), context.dp(90f))
                    setColorFilter(if (highlighted) selectedColor else iconColor, android.graphics.PorterDuff.Mode.SRC_ATOP)
                }
                setText(label)
                setCompoundDrawables(null, icon, null, null)
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.menu_text_size).toFloat())
                // c.b's TypedValue defaults are truncated before they become px.
                val padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics).toInt()
                setPadding(padding, padding, padding, padding)
                compoundDrawablePadding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6f, resources.displayMetrics).toInt()
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                background = controlRipple(context)
                isClickable = true
                isFocusable = true
                setTextColor(if (highlighted) selectedColor else textColor)
                typeface = Typeface.defaultFromStyle(if (highlighted) Typeface.BOLD else Typeface.NORMAL)
                setOnClickListener {
                    for (choice in 0 until this@ToolbarFullscreenChoices.childCount) {
                        val item = this@ToolbarFullscreenChoices.getChildAt(choice) as TextView
                        val active = choice == index
                        item.setTextColor(if (active) selectedColor else textColor)
                        item.typeface = Typeface.defaultFromStyle(if (active) Typeface.BOLD else Typeface.NORMAL)
                        item.compoundDrawables[1]?.setColorFilter(if (active) selectedColor else iconColor, android.graphics.PorterDuff.Mode.SRC_ATOP)
                    }
                    onSelected(index)
                }
            }, LayoutParams(0, -2, 1f).apply {
                val margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics).toInt()
                setMargins(margin, margin, margin, margin)
            })
        }
    }
}
