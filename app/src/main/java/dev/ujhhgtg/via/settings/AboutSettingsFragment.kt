package dev.ujhhgtg.via.settings

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.net.toUri
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.BuildConfig
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import dev.ujhhgtg.via.ui.dp
import java.util.Locale

/**
 * Kotlin port of hb.d.  The original About page is a full o8.g list, with a
 * version header and ordinary list rows that launch external links.  Keeping
 * those rows in a SettingsPageFragment avoids the old SettingsController
 * fallback which rendered a generic dialog for every About action.
 */
class AboutSettingsFragment : SettingsListFragment() {
    override fun configureToolbar(toolbar: SettingsToolbar) = toolbar.setTitle(R.string.settings_about)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rows = SettingsRowsAdapter(::open)
        list.itemAnimator = null
        rows.submit(buildRows())
        list.adapter = ConcatAdapter(object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = 1
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = object : RecyclerView.ViewHolder(header()) {}
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
        }, rows)
    }

    private fun buildRows() = buildList {
        add(SettingsRow(12, getString(R.string.check_for_updates)))
        add(SettingsRow(4, getString(R.string.open_source_repository)))
        add(SettingsRow(1, getString(R.string.open_source_licenses)))
    }

    /** ib.e: 60dp source vector, app name and condensed version with 24dp vertical padding. */
    private fun header(): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, context.dp(24f), 0, context.dp(24f)); layoutParams = RecyclerView.LayoutParams(-1, -2)
            addView(ImageView(context).apply {
                setImageResource(R.drawable.settings_about_logo); contentDescription = getString(R.string.debugging_info)
                setOnClickListener { (requireActivity() as Shell).navigate(DebuggingInfoFragment()) }
                setOnLongClickListener { runCatching { startActivity(Intent("com.android.webview.SHOW_DEV_UI")) }; true }
            }, LinearLayout.LayoutParams(context.dp(60f), context.dp(60f)))
            addView(TextView(context).apply {
                setText(R.string.app_name)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_toolbar_title_size).toFloat())
                setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, 0xff000000.toInt()))
                typeface = Typeface.create(BrowserPreferences(context).selectedTypeface(), Typeface.BOLD)
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(12f) })
            addView(TextView(context).apply {
                text = BuildConfig.VERSION_NAME
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
                setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, 0xff000000.toInt()))
                typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
                setOnClickListener {
                    ViaDialog(requireActivity()).title(BuildConfig.VERSION_NAME).message(if (Locale.getDefault().country.equals("CN", true)) "莫忧世事兼身事，须著人间比梦间。" else "Less is more.")
                        .positive(android.R.string.ok).show()
                }
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(2f) })
        }
    }

    private fun open(row: SettingsRow) {
        val url = when (row.id) {
            4 -> "https://github.com/Ujhhgtg/Via"
            else -> null
        }
        when {
            row.id == 1 -> (requireActivity() as Shell).navigate(OpenSourceLicensesFragment())
            // hb.d case 12: announce the check before Shell.c0 starts it.
            row.id == 12 -> { ViaToast.show(requireContext(), R.string.checking_for_updates); (requireActivity() as Shell).checkForUpdates() }
            !url.isNullOrEmpty() -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
        }
    }
}
