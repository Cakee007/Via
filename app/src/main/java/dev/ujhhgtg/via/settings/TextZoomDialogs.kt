package dev.ujhhgtg.via.settings

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.engine.EnginePage
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import dev.ujhhgtg.via.ui.dp
import java.lang.ref.WeakReference
import java.util.Locale

/** The confirmed settings preview (jb.k5.t3) and immediate browser popup (c8.s6.bb/S4). */
object TextZoomDialogs {
    fun sample(context: Context): String = context.getString(
        if (Locale.getDefault().country.equals("CN", true)) R.string.night_filter_preview_china else R.string.night_filter_preview)

    internal fun editException(activity: Activity, repository: TextZoomRepository, domain: String,
        initial: Int, existing: Boolean, changed: () -> Unit) {
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(-1, -2)
        }
        val preview = label(activity, sample(activity)).apply { minHeight = activity.dp(76f) }
        body.addView(preview, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(activity.dp(12f), activity.dp(16f), activity.dp(12f), activity.dp(16f))
        })
        val slider = TextZoomSeekBar(activity).apply {
            highlightProgress = (repository.global - 50) / 5
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    val base = (14f * resources.displayMetrics.scaledDensity + .5f).toInt()
                    val pixels = (base.toDouble() * (progress * 5 + 50) / 100).toInt()
                    preview.setTextSize(TypedValue.COMPLEX_UNIT_PX, pixels.toFloat())
                }
                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
            progress = (initial - 50) / 5
        }
        body.addView(slider, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = activity.dp(16f) })
        ViaDialog(activity).customView(body)
            .positive(android.R.string.ok) { _, _ -> repository.saveException(domain, slider.progress * 5 + 50); changed() }
            .negative(android.R.string.cancel)
            .apply { if (existing) neutral(R.string.action_delete) { repository.deleteException(domain); changed() } }
            .show()
    }

    /**
     * Ordinary menu action 29 passes siteOnly=false. Existing enabled exceptions select the site
     * automatically. Progress previews only the EnginePage captured when dragging starts; stopping
     * persists the choice and applies browser preferences, with no confirmation or rollback.
     */
    fun showBrowser(activity: Activity, currentWebView: () -> EnginePage?, siteOnly: Boolean = false,
        onChanged: () -> Unit, onCancelled: (() -> Unit)? = null) {
        val repository = TextZoomRepository(activity)
        val url = currentWebView()?.url.orEmpty()
        val siteOverride = repository.overrideForUrl(url)
        val global = repository.global
        val forSite = (siteOverride > 0 || siteOnly) && dev.ujhhgtg.via.engine.Engines.backend.capabilities.perPageTextZoom
        val initial = if (forSite && siteOverride != 0) siteOverride else global
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(-1, -2)
        }
        val value = label(activity, String.format(Locale.ROOT, "%d%%", initial))
        body.addView(value, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = activity.dp(16f); bottomMargin = activity.dp(16f)
        })
        val slider = TextZoomSeekBar(activity).apply {
            progress = (initial - 50) / 5
            highlightProgress = ((if (forSite) global else 100) - 50) / 5
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                private var preview: WeakReference<EnginePage>? = null
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    val percent = progress * 5 + 50
                    value.text = String.format(Locale.ROOT, "%d%%", percent)
                    preview?.get()?.setTextZoom(percent)
                }
                override fun onStartTrackingTouch(bar: SeekBar) {
                    preview = currentWebView()?.takeUnless { it.url.orEmpty().startsWith("file://", true) }?.let(::WeakReference)
                }
                override fun onStopTrackingTouch(bar: SeekBar) {
                    val percent = bar.progress * 5 + 50
                    if (forSite) repository.saveForUrl(url, percent) else repository.global = percent
                    dev.ujhhgtg.via.common.GeneratedDocumentState.mark(dev.ujhhgtg.via.common.GeneratedDocumentState.PAGE_SETTINGS)
                    onChanged()
                }
            })
        }
        body.addView(slider, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = activity.dp(16f) })
        if (forSite) body.addView(label(activity, activity.getString(R.string.only_take_effect_for_current_site)).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_summary_size).toFloat())
            setTextColor(settingsColor(activity, R.attr.viaSecondaryTextColor, Color.GRAY))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (2 * activity.resources.displayMetrics.density).toInt(); bottomMargin = activity.dp(8f) })
        ViaDialog(activity).customView(body).blurMode(2)
            .onCancel { if (siteOnly) onCancelled?.invoke() }.show()
    }

    private fun label(context: Context, value: String) = TextView(context).apply {
        text = value; gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_toolbar_title_size).toFloat())
        setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, Color.BLACK))
    }
}
