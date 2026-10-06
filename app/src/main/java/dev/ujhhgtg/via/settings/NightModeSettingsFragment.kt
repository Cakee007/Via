package dev.ujhhgtg.via.settings

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.common.GeneratedDocumentState
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import dev.ujhhgtg.via.ui.dp
import java.util.Locale

/** hb.p4: the two night-mode rows and its cached night-filter preview. */
class NightModeSettingsFragment : SettingsListFragment() {
    private lateinit var preferences: BrowserPreferences
    private lateinit var rows: SettingsRowsAdapter
    private var preview: View? = null

    override fun configureToolbar(toolbar: SettingsToolbar) { toolbar.setTitle(R.string.action_night) }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = BrowserPreferences(requireContext())
        GeneratedDocumentState.initialize(preferences)
        rows = SettingsRowsAdapter { item -> when (item.id) {
            1 -> ViaDialog(requireActivity()).customView(preview()).show()
            2 -> {
                preferences.forceDarkPages = !preferences.forceDarkPages
                GeneratedDocumentState.mark(GeneratedDocumentState.PAGE_SETTINGS)
                rows.submit(items())
            }
            3 -> {
                preferences.disableHomeBackgroundDimming = !preferences.disableHomeBackgroundDimming
                GeneratedDocumentState.mark(GeneratedDocumentState.HOME_STYLE or GeneratedDocumentState.PAGE_SETTINGS)
                rows.submit(items())
            }
        } }
        list.itemAnimator = null
        list.adapter = rows
        rows.submit(items())
    }
    private fun items() = listOfNotNull(
        SettingsRow(1, getString(R.string.night_filter_for_web_contents)),
        SettingsToggleRow(2, getString(R.string.force_dark_mode_for_web_contents), getString(R.string.force_dark_mode_for_web_contents_description), preferences.forceDarkPages)
            .takeIf { dev.ujhhgtg.via.engine.Engines.backend.capabilities.algorithmicDarkening },
        SettingsToggleRow(3, getString(R.string.disable_home_background_dimming), getString(R.string.disable_home_background_dimming_description), preferences.disableHomeBackgroundDimming),
    )
    private fun preview(): View {
        preview?.let { (it.parent as? ViewGroup)?.removeView(it); return it }
        val context = requireContext()
        fun units(value: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val shade = FrameLayout(context)
        val text = TextView(context).apply {
            // z8.f.h checks country CN, including non-Chinese languages configured for China.
            setText(if (Locale.getDefault().country.equals("CN", true)) R.string.night_filter_preview_china else R.string.night_filter_preview)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.night_preview_text_size).toFloat())
            setTextColor(color(context, R.attr.viaPrimaryTextColor))
            setPaddingRelative(0, units(32), 0, units(32))
        }
        shade.addView(text, ViewGroup.LayoutParams(-1, -2))
        body.addView(shade, LinearLayout.LayoutParams(-1, -2).apply { setMargins(units(22), units(16), units(22), units(16)) })
        val slider = SeekBar(ContextThemeWrapper(requireActivity(), R.style.Seekbar)).apply {
            max = 154; minimumHeight = context.dp(2f)
            setPaddingRelative(units(16), 0, units(16), 0)
            val track = GradientDrawable().apply { cornerRadius = context.dp(5f).toFloat(); setColor(0x40808080); setSize(0, context.dp(2f)) }
            val fill = GradientDrawable().apply { cornerRadius = context.dp(5f).toFloat(); setColor(color(context, R.attr.viaAccentColor)); setSize(0, context.dp(2f)) }
            progressDrawable = LayerDrawable(arrayOf(track, ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL))).apply { setId(0, android.R.id.background); setId(1, android.R.id.progress) }
            thumb = GradientDrawable().apply { cornerRadius = context.dp(6f).toFloat(); setColor(color(context, R.attr.viaAccentColor)); setSize(context.dp(12f), context.dp(12f)) }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) { shade.foreground = GradientDrawable().apply { cornerRadius = resources.getDimensionPixelSize(R.dimen.menu_corner_radius).toFloat(); setColor(Color.argb(value, 0, 0, 0)) } }
                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    preferences.nightFilter = seekBar.progress
                    GeneratedDocumentState.mark(GeneratedDocumentState.PAGE_SETTINGS)
                }
            })
        }
        body.addView(slider, LinearLayout.LayoutParams(-1, -2).apply { setMargins(units(16), 0, units(16), units(16)) })
        slider.progress = preferences.nightFilter
        preview = body
        return body
    }
    private fun color(context: Context, attribute: Int): Int = context.obtainStyledAttributes(intArrayOf(attribute)).let { try { it.getColor(0, Color.TRANSPARENT) } finally { it.recycle() } }
    override fun onDestroyView() { preview = null; super.onDestroyView() }
}
