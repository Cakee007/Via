package dev.ujhhgtg.via.tools

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.ujhhgtg.via.ui.ViaToast
import androidx.core.graphics.drawable.toDrawable
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.common.WindowInsetsHelper
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.SettingsToolbar
import dev.ujhhgtg.via.ui.dp

/** In-process za/g PDF page, including its title, night overlay and page-count controls. */
@SuppressLint("ViewConstructor")
class PdfViewerView(context: Context, private val onClose: () -> Unit = {}) : FrameLayout(context), AutoCloseable {
    val viewer = PdfViewer(context)
    private var title: TextView
    private val counter = TextView(context).apply {
        setTextColor(0xffdddddd.toInt())
        textSize = 12f
        setPadding(context.dp(8f), context.dp(4f), context.dp(8f), context.dp(4f))
        alpha = 0f
        setBackgroundColor(0xaa222222.toInt())
    }
    private val hide = Runnable { counter.animate().alpha(0f).setDuration(220).start() }

    init {
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val toolbar = SettingsToolbar(context) { onClose() }
        title = toolbar.titleView
        root.addView(toolbar)
        val night = BrowserPreferences(context).isNightMode
        if (night) viewer.foreground = Int.MIN_VALUE.toDrawable()
        val content = FrameLayout(context)
        content.addView(viewer, LayoutParams(-1, -1))
        content.addView(counter, LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { topMargin = context.dp(12f); marginStart = context.dp(12f) })
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(root, LayoutParams(-1, -1))
        WindowInsetsHelper.apply(root)
        @SuppressLint("SetTextI18n")
        viewer.setOnPageChangedListener { page, count -> counter.text = "${page + 1} / $count" }
        viewer.onInteraction = { active ->
            counter.animate().cancel()
            counter.alpha = 1f
            counter.removeCallbacks(hide)
            if (!active) counter.postDelayed(hide, 2000)
        }
        viewer.onError = { ViaToast.makeText(context, context.getString(R.string.toast_operation_failed), ViaToast.LENGTH_LONG).show() }
    }
    fun open(uri: Uri, name: String? = null) {
        val displayName = name ?: if (uri.scheme == "content") runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() else null
        title.text = displayName ?: uri.lastPathSegment ?: context.getString(R.string.action_preview)
        viewer.open(uri)
    }
    fun onHostPause() { counter.removeCallbacks(hide); counter.animate().cancel() }
    override fun close() { onHostPause(); viewer.close() }
}
