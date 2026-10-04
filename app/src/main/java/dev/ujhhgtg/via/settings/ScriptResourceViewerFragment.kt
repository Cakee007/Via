package dev.ujhhgtg.via.settings

import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.ui.dp
import dev.ujhhgtg.via.ui.widgets.FastScrollView
import java.io.File
import java.util.concurrent.Executors

/** k8.k: selectable monospace file viewer reached from userscript resource information. */
class ScriptResourceViewerFragment : SettingsPageFragment() {
    private lateinit var text: TextView
    private lateinit var scroll: FastScrollView
    private lateinit var progress: ProgressBar
    private val worker = Executors.newSingleThreadExecutor()
    override fun configureToolbar(toolbar: SettingsToolbar) { toolbar.setTitle(R.string.view_downloads) }
    override fun createContent(inflater: LayoutInflater, container: ViewGroup?): View {
        val context = requireContext()
        text = TextView(context).apply {
            setPadding(context.dp(16f), context.dp(16f), context.dp(16f), context.dp(16f))
            setTextIsSelectable(true); typeface = Typeface.MONOSPACE; textDirection = View.TEXT_DIRECTION_LTR
            setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, 0xff000000.toInt()))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_summary_size).toFloat())
        }
        scroll = FastScrollView(context).apply {
            isFillViewport = true
            overScrollMode = if (Build.VERSION.SDK_INT >= 32) View.OVER_SCROLL_IF_CONTENT_SCROLLS else View.OVER_SCROLL_NEVER
            addView(text, ViewGroup.LayoutParams(-1, -2))
        }
        progress = ProgressBar(context).apply { isIndeterminate = false }
        return FrameLayout(context).apply {
            addView(progress, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
            addView(scroll, FrameLayout.LayoutParams(-2, -2))
        }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.postDelayed({
            if (this.view == null) return@postDelayed
            progress.visibility = View.VISIBLE; scroll.visibility = View.GONE
            val path = arguments?.getString("file_path")
            worker.execute {
                val file = path?.takeIf(String::isNotEmpty)?.let(::File)
                val content = runCatching { file?.bufferedReader()?.use { reader -> buildString { reader.forEachLine { append(it).append('\n') } } } }.getOrNull()
                activity?.runOnUiThread {
                    if (this.view != null) {
                        progress.visibility = View.GONE; scroll.visibility = View.VISIBLE
                        file?.name?.takeIf(String::isNotEmpty)?.let(toolbar::setTitle)
                        text.text = content ?: "Cannot read file at: $path"
                    }
                }
            }
        }, 200L)
    }
    override fun onDestroy() { worker.shutdown(); super.onDestroy() }
    companion object {
        fun newInstance(path: String) = ScriptResourceViewerFragment().apply { arguments = Bundle().apply { putString("file_path", path) } }
    }
}
