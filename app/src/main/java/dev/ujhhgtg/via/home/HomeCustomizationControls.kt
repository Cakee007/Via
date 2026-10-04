package dev.ujhhgtg.via.home

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.reader.ReaderControls
import dev.ujhhgtg.via.settings.settingsColor
import dev.ujhhgtg.via.ui.dp
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/** h9.a/b/c/d: the original control kinds keep their value while the preview refreshes. */
internal sealed class HomeControl(val icon: Int, val title: String) {
    abstract val subtitle: String?
    class Range(icon: Int, title: String, var value: Int, val minimum: Int, val maximum: Int,
        private val format: String, val adaptive: Int = -1, private val adaptiveLabel: String? = null,
        val commit: (Int) -> Unit,
    ) : HomeControl(icon, title) {
        override val subtitle get() = if (value == adaptive) adaptiveLabel else String.format(Locale.getDefault(), format, value)
    }
    class Action(icon: Int, title: String, override var subtitle: String? = "", val action: () -> Unit) : HomeControl(icon, title)
    class Toggle(icon: Int, title: String, var checked: Boolean, private val onLabel: String, private val offLabel: String,
        val commit: (Boolean) -> Unit,
    ) : HomeControl(icon, title) {
        override val subtitle get() = if (checked) onLabel else offLabel
    }
}

/** e9.k / f9.b and original layouts r/y, including the persistent selected slider cell. */
internal class HomeCustomizationControls(context: Context) : FrameLayout(context) {
    private val controls = mutableListOf<HomeControl>()
    private val list: RecyclerView
    private val slider: SeekBar
    private val layout = HomeControlLayoutManager(context)
    private val adapter = ControlAdapter()
    private var selected = -1
    private var paletteSelected = -1
    private val palette = PaletteAdapter()
    var onPaletteClick: ((Int) -> Unit)? = null
    var backgroundPane = false

    init {
        val content = LayoutInflater.from(context).inflate(R.layout.home_customization_controls, this, false)
        addView(content)
        list = content.findViewById<RecyclerView>(R.id.home_custom_controls).apply {
            layoutManager = layout; itemAnimator = null; adapter = this@HomeCustomizationControls.adapter
            setOnTouchListener { view, event ->
                if (backgroundPane) when (event.action) {
                    MotionEvent.ACTION_DOWN -> view.parent.requestDisallowInterceptTouchEvent(view.canScrollHorizontally(-1))
                    MotionEvent.ACTION_UP -> view.parent.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
        }
        slider = content.findViewById<SeekBar>(R.id.home_custom_slider).apply { ReaderControls.styleSeekBar(this); thumbOffset = 0 }
        list.requestFocus()
    }

    fun setControls(values: List<HomeControl>, show: Boolean = true) {
        controls.clear(); controls.addAll(values); selected = -1
        slider.visibility = GONE
        adapter.notifyDataSetChanged()
        if (show) showControls()
    }

    fun showControls() { layout.columns = 5; list.adapter = adapter }

    fun showPalette(selectedIndex: Int) {
        clearSelection()
        paletteSelected = selectedIndex
        val count = ceil(list.width.toFloat() / context.dp(56f)).toInt().coerceAtLeast(1)
        layout.columns = count
        list.adapter = palette
        list.scrollToPosition(maxOf(0, selectedIndex - count / 2))
    }

    fun selectPalette(index: Int) {
        val old = paletteSelected
        paletteSelected = index
        if (old in PALETTE.indices) palette.notifyItemChanged(old)
        if (index in PALETTE.indices) palette.notifyItemChanged(index)
    }

    fun refreshControl(control: HomeControl) {
        val index = controls.indexOf(control)
        if (index >= 0) adapter.notifyItemChanged(index)
    }

    private fun clearSelection() {
        val old = selected
        selected = -1
        slider.visibility = GONE
        if (old in controls.indices) adapter.notifyItemChanged(old)
    }

    private fun click(position: Int) {
        when (val control = controls[position]) {
            is HomeControl.Range -> {
                if (selected == position) { clearSelection(); return }
                clearSelection()
                selected = position; adapter.notifyItemChanged(position)
                slider.visibility = VISIBLE
                slider.setOnSeekBarChangeListener(null)
                slider.max = control.maximum - control.minimum
                slider.progress = control.value - control.minimum
                slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                        control.value = progress + control.minimum
                        adapter.notifyItemChanged(position)
                    }
                    override fun onStartTrackingTouch(bar: SeekBar) = Unit
                    override fun onStopTrackingTouch(bar: SeekBar) { control.commit(control.value) }
                })
                slider.requestFocus()
            }
            is HomeControl.Toggle -> {
                clearSelection()
                control.checked = !control.checked
                adapter.notifyItemChanged(position)
                control.commit(control.checked)
            }
            is HomeControl.Action -> { clearSelection(); control.action() }
        }
    }

    private inner class ControlAdapter : RecyclerView.Adapter<Cell>() {
        override fun getItemCount() = controls.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Cell(LayoutInflater.from(parent.context).inflate(R.layout.home_customization_control, parent, false))
        override fun onBindViewHolder(holder: Cell, position: Int) {
            val control = controls[position]
            val active = position == selected
            holder.icon.setImageResource(control.icon)
            holder.icon.setColorFilter(settingsColor(context, if (active) R.attr.viaAccentColor else R.attr.viaSubtleColor, Color.BLACK))
            holder.icon.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            holder.title.text = control.title
            holder.value.text = control.subtitle
            holder.value.visibility = if (control.subtitle == null) GONE else VISIBLE
            val face = BrowserPreferences(context).selectedTypeface()
            for (text in arrayOf(holder.title, holder.value)) {
                text.isSelected = active; text.setTypeface(face, if (active) Typeface.BOLD else Typeface.NORMAL)
            }
            holder.itemView.setHomeControlClickListener { click(holder.bindingAdapterPosition.takeIf { it >= 0 } ?: return@setHomeControlClickListener) }
        }
    }

    private class Cell(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.home_custom_control_icon)
        val title: TextView = view.findViewById(R.id.home_custom_control_title)
        val value: TextView = view.findViewById(R.id.home_custom_control_value)
    }

    /** f9.a/b6.f.e: the palette consists only of centered 48dp image cells, not labels or HEX text. */
    private inner class PaletteAdapter : RecyclerView.Adapter<PaletteCell>() {
        override fun getItemCount() = PALETTE.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaletteCell {
            val frame = FrameLayout(context).apply { layoutParams = RecyclerView.LayoutParams(-1, -1) }
            val image = ImageView(context).apply { setPadding(context.dp(6f), context.dp(6f), context.dp(6f), context.dp(6f)) }
            frame.addView(image, LayoutParams(context.dp(48f), context.dp(48f), Gravity.CENTER))
            return PaletteCell(frame, image)
        }
        override fun onBindViewHolder(holder: PaletteCell, position: Int) {
            val entry = PALETTE[position]
            val tint = settingsColor(context, R.attr.viaSubtleColor, Color.BLACK)
            val icon = if (position == 1) {
                LayerDrawable(arrayOf(circle(0, tint, 2), ContextCompat.getDrawable(context, R.drawable.image)!!.mutate().apply { setTint(tint) })).apply {
                    val padding = context.dp(16f); setLayerInset(1, padding, padding, padding, padding)
                }
            } else if (entry.color == 0) circle(0, tint, 1) else circle(entry.color)
            holder.icon.setImageDrawable(icon)
            holder.icon.contentDescription = when (position) {
                0 -> context.getString(R.string.default_set)
                1 -> context.getString(R.string.picture)
                else -> entry.title
            }
            holder.icon.background = if (position == paletteSelected) circle(0x40808080) else ContextCompat.getDrawable(context, R.drawable.circle_ripple)
            holder.itemView.setHomeControlClickListener { onPaletteClick?.invoke(position) }
        }
        private fun circle(color: Int, stroke: Int = 0, width: Int = 0): Drawable = GradientDrawable().apply {
            setColor(color); cornerRadius = context.dp(32f).toFloat()
            if (stroke != 0) setStroke(context.dp(width.toFloat()), stroke)
        }
    }
    private class PaletteCell(view: View, val icon: ImageView) : RecyclerView.ViewHolder(view)

    /** Original LinearLayoutPagerManager, including rounding rather than a minimum cell width. */
    private class HomeControlLayoutManager(context: Context) : LinearLayoutManager(context, RecyclerView.HORIZONTAL, false) {
        var columns = 5
            set(value) { if (field != value) { field = value; requestLayout() } }
        private fun size() = (width.toFloat() / columns).roundToInt()
        override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams = super.generateDefaultLayoutParams().apply { width = size() }
        override fun generateLayoutParams(lp: ViewGroup.LayoutParams): RecyclerView.LayoutParams = super.generateLayoutParams(lp).apply { width = size() }
        override fun checkLayoutParams(lp: RecyclerView.LayoutParams) = super.checkLayoutParams(lp) && lp.width == size()
    }

    companion object {
        data class PaletteEntry(val color: Int, val title: String)
        val PALETTE = listOf(
            PaletteEntry(0, ""), PaletteEntry(0, ""), PaletteEntry(-13928247, "Classic blue"), PaletteEntry(-13287859, "Indigo"),
            PaletteEntry(-7707309, "Leather"), PaletteEntry(-4162752, "Walnut"), PaletteEntry(-1919870, "Birch"),
            PaletteEntry(-11776948, "Limestone"), PaletteEntry(-3848376, "Red"), PaletteEntry(-482746, "Orange"),
            PaletteEntry(-79799, "Yellow"), PaletteEntry(-4931980, "Green"), PaletteEntry(-9780028, "Cyan"),
            PaletteEntry(-10391622, "Blue"), PaletteEntry(-6592854, "Purple"), PaletteEntry(-3432857, "Brown"),
            PaletteEntry(-6578261, "Light grey"), PaletteEntry(-9876171, "Maroon"), PaletteEntry(-6801101, "Light Pink"),
            PaletteEntry(-2519416, "Carnation"), PaletteEntry(-2121393, "Hay"), PaletteEntry(-10790341, "Tawny"),
            PaletteEntry(-4480632, "Light brown"), PaletteEntry(-10727322, "Deep purple"),
        )
    }
}

/** v5.c, used by d9.q and y5.a for their original 300ms per-view click interval. */
internal fun View.setHomeControlClickListener(clicked: (View) -> Unit) {
    var previous: Long? = null
    setOnClickListener { view ->
        val now = SystemClock.elapsedRealtime()
        val last = previous
        if (last == null || kotlin.math.abs(now - last) > 300L) {
            previous = now
            clicked(view)
        }
    }
}
