package dev.ujhhgtg.via.settings

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.PrecomputedText
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.URLUtil
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.browser.script.ScriptStore
import dev.ujhhgtg.via.browser.script.UserScript
import dev.ujhhgtg.via.ui.PageProgress
import dev.ujhhgtg.via.ui.SwipeBackLayout
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import dev.ujhhgtg.via.ui.dp
import dev.ujhhgtg.via.ui.widgets.FastScrollView
import java.util.Random
import java.util.concurrent.Executors

/** sa.o: source editor, original metadata template, help and script_result_v2 contract. */
class ScriptEditorFragment : SettingsPageFragment() {
    private lateinit var input: EditText
    private lateinit var scroll: FastScrollView
    private lateinit var progress: PageProgress
    private val worker = Executors.newSingleThreadExecutor()
    private var scriptId = -1
    private var initial = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scriptId = savedInstanceState?.getInt("id") ?: arguments?.getInt("id", -1) ?: -1
    }

    override fun configureToolbar(toolbar: SettingsToolbar) {
        toolbar.setTitle(if (scriptId > 0) R.string.action_edit else R.string.add_script)
        toolbar.addAction(null, R.string.help) { showHelp() }
        toolbar.addAction(null, R.string.action_save) { save { closeEditor() } }
    }

    override fun createContent(inflater: LayoutInflater, container: ViewGroup?): View {
        val context = requireContext()
        input = EditText(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(context.dp(12f), context.dp(16f), context.dp(12f), context.dp(16f))
            if (Build.VERSION.SDK_INT >= 35) isLocalePreferredLineHeightForMinimumUsed = false
            inputType = 655361
            setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, Color.BLACK))
            setHintTextColor(settingsColor(context, R.attr.viaSecondaryTextColor, Color.DKGRAY))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
            gravity = Gravity.TOP or Gravity.START
            typeface = Typeface.MONOSPACE
            textDirection = View.TEXT_DIRECTION_LTR
            hint = getString(R.string.script_code)
        }
        scroll = FastScrollView(context).apply {
            isFillViewport = true
            overScrollMode = if (Build.VERSION.SDK_INT >= 32) View.OVER_SCROLL_IF_CONTENT_SCROLLS else View.OVER_SCROLL_NEVER
            addView(input, FrameLayout.LayoutParams(-1, -2))
        }
        val bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false; max = 100; progress = 1
            progressDrawable = LayerDrawable(arrayOf(
                GradientDrawable().apply { setColor(Color.TRANSPARENT) },
                ClipDrawable(GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(0xff59c2e5.toInt(), 0xffa687e0.toInt())).apply { cornerRadius = context.dp(2f).toFloat() }, Gravity.START, ClipDrawable.HORIZONTAL),
            )).apply { setId(0, android.R.id.background); setId(1, android.R.id.progress) }
        }
        progress = PageProgress(bar)
        return FrameLayout(context).apply {
            addView(scroll, FrameLayout.LayoutParams(-1, -1))
            addView(bar, FrameLayout.LayoutParams(-1, context.dp(3f)))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (view as? SwipeBackLayout)?.setGestureEnabled(false)
        if (!predictiveBackSupported()) requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) { override fun handleOnBackPressed() = onToolbarBack() })
        if (savedInstanceState?.containsKey("source") == true) {
            input.setText(savedInstanceState.getString("source"))
            initial = savedInstanceState.getString("initial").orEmpty()
        } else if (scriptId > 0) {
            input.isEnabled = false; input.alpha = .25f; progress.update(30)
            val context = requireContext().applicationContext
            worker.execute {
                val source = runCatching { ScriptStore(context).use { it.find(scriptId)?.content.orEmpty() } }.getOrDefault("")
                activity?.runOnUiThread {
                    if (this.view != null) {
                        initial = source; showLoadedSource(source)
                    }
                }
            }
        } else {
            val random = Random()
            val suffix = buildString { repeat(6) { append("abcdefghijklmnopqrstuvwxyz0123456789"[random.nextInt(36)]) } }
            var template = METADATA + "(function() {\n    'use strict';\n\n    // Your code here...\n})();"
            arguments?.getString("url")?.takeIf(URLUtil::isNetworkUrl)?.let { template = template.replace("https://*/*", it) }
            template = template.replace("https://viayoo.com/", "https://viayoo.com/$suffix")
            input.setText(template); initial = template
        }
    }

    /** x8.g.g: lay out large scripts off the UI thread before re-enabling editing. */
    private fun showLoadedSource(source: String) {
        fun ready() { progress.update(100); input.isEnabled = true; input.alpha = 1f }
        if (source.length < 16_384) { input.setText(source); ready(); return }
        input.setText(source.substring(0, 16_384))
        val metrics = input.textMetricsParams
        worker.execute {
            val computed = PrecomputedText.create(source, metrics)
            activity?.runOnUiThread {
                if (view != null) {
                    input.setText(computed)
                    input.postDelayed({ if (view != null) ready() }, minOf(50, source.length / 16_384) * 10L)
                }
            }
        }
    }

    private fun unchanged() = !input.isEnabled || input.text.isEmpty() || input.text.toString() == initial
    override fun allowPredictiveBack() = unchanged()
    override fun onToolbarBack() = leave { closeEditor() }
    private fun closeEditor() = super.onToolbarBack()

    private fun leave(action: () -> Unit) {
        if (unchanged()) action()
        else ViaDialog(requireActivity()).title(R.string.message).message(R.string.modified_content_has_not_been_saved_message)
            .positive(R.string.save_and_exit) { _, _ -> save(action) }.negative(R.string.exit) { action() }.show()
    }

    private fun showHelp() {
        ViaDialog(requireActivity()).title(R.string.help)
            .message(Html.fromHtml(getString(R.string.simple_guide_of_writing_userscript), Html.FROM_HTML_MODE_LEGACY))
            .positive(android.R.string.ok)
            .neutral(R.string.learn_more) {
                leave { (requireActivity() as Shell).openRecordFromRecords("https://www.tampermonkey.net/documentation.php", false) }
            }.show()
    }

    private fun save(after: () -> Unit) {
        if (!input.isEnabled) { after(); return }
        val context = requireContext().applicationContext
        val source = input.text.toString()
        val existing = ScriptStore(context).use { it.find(scriptId) }
        val parsed = UserScript.parse(source, existing?.downloadUrl)
        if (parsed == null) {
            ViaDialog(requireActivity()).title(R.string.title_add_metadata_block).message(R.string.message_add_metadata_block)
                .positive(android.R.string.ok) { _, _ ->
                    input.text.replace(0, 0, METADATA)
                    input.clearFocus(); scroll.scrollTo(0, 0)
                    scroll.alpha = 0f; scroll.visibility = View.VISIBLE
                    scroll.animate().alpha(1f).setInterpolator(android.view.animation.PathInterpolator(.2f, .2f, .8f, .8f)).setDuration(250).start()
                }.negative(android.R.string.cancel).show()
            return
        }
        worker.execute {
            val savedId = runCatching {
                ScriptStore(context).use { store ->
                    val value = parsed.copy(id = scriptId, enabled = existing?.enabled ?: parsed.enabled)
                    if (existing == null) store.insert(value) else store.save(value)
                }
            }.getOrDefault(0)
            activity?.runOnUiThread {
                if (view != null) {
                    if (savedId > 0) {
                        scriptId = savedId; initial = source
                        parentFragmentManager.setFragmentResult(RESULT, Bundle().apply { putInt("id", savedId) })
                        after()
                    } else ViaToast.show(requireContext(), getString(R.string.toast_install_script_failed_unknown, parsed.name))
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("id", scriptId)
        if (::input.isInitialized) { outState.putString("source", input.text.toString()); outState.putString("initial", initial) }
    }
    override fun onPause() {
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        super.onPause()
    }
    override fun onDestroy() { worker.shutdown(); super.onDestroy() }

    companion object {
        const val RESULT = "script_result_v2"
        private const val METADATA = "// ==UserScript==\n// @name         New Userscript\n// @namespace    https://viayoo.com/\n// @version      0.1\n// @description  try to take over the world!\n// @author       You\n// @run-at       document-end\n// @match        https://*/*\n// @grant        none\n// ==/UserScript==\n\n"
        fun newInstance(id: Int) = ScriptEditorFragment().apply { arguments = Bundle().apply { putInt("id", id) } }
        fun forUrl(url: String) = ScriptEditorFragment().apply { arguments = Bundle().apply { putString("url", url) } }
    }
}
