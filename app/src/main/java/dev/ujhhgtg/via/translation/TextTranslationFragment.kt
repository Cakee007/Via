package dev.ujhhgtg.via.translation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.settingsColor
import dev.ujhhgtg.via.skins.SkinResources
import dev.ujhhgtg.via.ui.BrowserOverlayFragment
import dev.ujhhgtg.via.ui.PageProgress
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import dev.ujhhgtg.via.ui.dp
import dev.ujhhgtg.via.common.launchIo
import kotlinx.coroutines.Job
import java.util.Locale
import java.util.Random

/** f8.w0: the native text translation pane hosted inside the browser overlay container. */
class TextTranslationFragment : BrowserOverlayFragment() {
    private lateinit var source: EditText
    private lateinit var output: TextView
    private lateinit var clear: ImageView
    private lateinit var copy: ImageView
    private lateinit var translate: ImageView
    private lateinit var from: TextView
    private lateinit var to: TextView
    private lateinit var progress: PageProgress
    private lateinit var client: TextTranslationClient
    private lateinit var sourceLanguage: TranslationLanguage
    private lateinit var targetLanguage: TranslationLanguage
    private var result: TranslationResult? = null
    private var request: Job? = null
    private var mode = 0
    private val random = Random()

    // c8.s6.pb creates this pane without kb(), unlike tab and page-information sheets.
    override val scrimEnabled = false
    override fun paneTitle(): CharSequence = getString(R.string.action_translate)
    override fun createContent(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.text_translation, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val locale = resources.configuration.locales[0]
        client = TextTranslationClient(locale)
        sourceLanguage = client.sourceLanguage("auto").apply { name = getString(R.string.detect_language) }
        targetLanguage = client.targetLanguage(defaultLanguage(locale))
        source = view.findViewById(R.id.translation_source)
        output = view.findViewById(R.id.translation_result)
        clear = view.findViewById(R.id.translation_clear)
        copy = view.findViewById(R.id.translation_copy)
        translate = view.findViewById(R.id.translation_start)
        from = view.findViewById(R.id.translation_source_language)
        to = view.findViewById(R.id.translation_target_language)
        val typeface = BrowserPreferences(requireContext()).selectedTypeface()
        listOf(source, output, from, to).forEach { it.typeface = typeface }

        // f8.w0.V1 dynamically skins only this action and the clear/exit states.
        SkinResources.drawable(requireContext(), R.drawable.translation_start, "ic_translate")?.mutate()?.let {
            it.setColorFilter(color(R.attr.viaAccentTextColor, Color.WHITE), PorterDuff.Mode.SRC_IN)
            translate.setImageDrawable(it)
        }
        fun circle(fill: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(fill) }
        translate.background = RippleDrawable(ColorStateList.valueOf(color(R.attr.viaDarkAccentColor, 0xff5b73b5.toInt())),
            circle(color(R.attr.viaAccentColor, 0xff6f8de1.toInt())), circle(Color.BLACK))
        val bar = view.findViewById<ProgressBar>(R.id.translation_progress)
        bar.progressDrawable = LayerDrawable(arrayOf(
            GradientDrawable().apply { setColor(Color.TRANSPARENT) },
            ClipDrawable(GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xff59c2e5.toInt(), 0xffa687e0.toInt())).apply { cornerRadius = requireContext().dp(2f).toFloat() },
                Gravity.START, ClipDrawable.HORIZONTAL),
        )).apply { setId(0, android.R.id.background); setId(1, android.R.id.progress) }
        progress = PageProgress(bar)

        source.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(text: Editable) {
                val hasText = text.isNotEmpty()
                clear.setImageDrawable(SkinResources.drawable(requireContext(), if (hasText) R.drawable.close else R.drawable.power,
                    if (hasText) "ic_close" else "ic_exit"))
                clear.contentDescription = getString(if (hasText) R.string.desc_clear_text else R.string.exit)
            }
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })
        source.setOnFocusChangeListener { _, focused ->
            if (focused) showMode(INPUT) else if (result != null) showMode(RESULT)
        }
        clear.setOnClickListener {
            if (source.length() > 0) { source.setText(""); showMode(INPUT) }
            else requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        view.findViewById<View>(R.id.translation_swap).setOnClickListener { swapLanguages() }
        output.setOnClickListener { showMode(RESULT) }
        copy.setOnClickListener {
            if (output.length() > 0) {
                (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("", output.text.toString()))
                ViaToast.show(requireContext(), R.string.toast_copy_text_successful)
            }
        }
        from.text = sourceLanguage.name
        to.text = targetLanguage.name
        from.setOnClickListener { chooseLanguage(true) }
        to.setOnClickListener { chooseLanguage(false) }
        translate.setOnClickListener { requestTranslation() }
        restoreCache()
        showMode(INPUT)
        arguments?.getString("text")?.takeIf(String::isNotEmpty)?.let {
            source.setText(it)
            requestTranslation()
        }
    }

    /** c8.s6.pb reuses an existing pane, replaces its input, then clicks Translate. */
    fun replaceTextAndTranslate(text: String?) {
        source.setText(text)
        requestTranslation()
    }

    override fun focusContent() {
        if (!source.isAttachedToWindow || !source.isShown) return
        source.requestFocus()
        source.performAccessibilityAction(64, null)
    }

    private fun showMode(next: Int) {
        if (mode == next) return
        mode = next
        when (next) {
            INPUT -> if (result == null) {
                source.setLines(7)
                output.visibility = View.GONE
                copy.visibility = View.GONE
            } else {
                source.setLines(5)
                output.scrollX = 0; output.setLines(1)
                output.visibility = View.VISIBLE; copy.visibility = View.VISIBLE
            }
            RESULT -> {
                source.setLines(1); source.scrollX = 0
                output.setLines(5); output.scrollX = 0
                output.visibility = View.VISIBLE; copy.visibility = View.VISIBLE
                output.requestFocus()
                hideKeyboard()
            }
        }
    }

    private fun swapLanguages() {
        var actualSource = sourceLanguage
        if (actualSource.code == "auto") result?.let { actualSource = client.sourceLanguage(it.from) }
        if (actualSource.code == "auto" || actualSource.code == targetLanguage.code) return
        sourceLanguage = targetLanguage
        targetLanguage = actualSource
        from.text = sourceLanguage.name; to.text = targetLanguage.name
        val oldInput = source.text.toString()
        source.setText(output.text.toString())
        output.text = oldInput
    }

    private fun chooseLanguage(sourceSide: Boolean) {
        val languages = if (sourceSide) client.sourceLanguages else client.targetLanguages
        val current = if (sourceSide) sourceLanguage else targetLanguage
        ViaDialog(requireActivity()).title(if (sourceSide) R.string.title_source_language else R.string.title_target_language)
            .singleChoice(languages.map { it.name }.toTypedArray(), languages.indexOfFirst { it.code == current.code }.coerceAtLeast(0)) {
                val selected = languages[it]
                if (selected.code != current.code) {
                    if (sourceSide) { sourceLanguage = selected; from.text = selected.name }
                    else { targetLanguage = selected; to.text = selected.name }
                }
            }.show()
    }

    private fun requestTranslation() {
        request?.cancel()
        val query = source.text.toString().trim()
        if (query.isEmpty()) {
            progress.update(100); result = null; showMode(INPUT)
            return
        }
        progress.update(random.nextInt(35) + 10, true)
        val fromCode = sourceLanguage.code
        val toCode = targetLanguage.code
        request = viewLifecycleOwner.launchIo({ Response(client.translate(query, fromCode, toCode)) }, { response ->
                progress.update(100)
                result = response.result
                if (response.result != null) {
                    output.text = response.result.target
                    hideKeyboard(); showMode(RESULT)
                } else showMode(INPUT)
            }, { error -> android.util.Log.e("ViaTranslation", "Text translation failed", error) })
    }

    private fun restoreCache() {
        val saved = cache ?: return
        if (saved.expires < SystemClock.elapsedRealtime() / 1000) { cache = null; return }
        if (saved.from.isEmpty() || saved.to.isEmpty()) return
        sourceLanguage = client.sourceLanguage(saved.from)
        targetLanguage = client.targetLanguage(saved.to)
        from.text = sourceLanguage.name; to.text = targetLanguage.name
        source.setText(saved.query)
    }
    private fun hideKeyboard() {
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(source.windowToken, 0)
    }
    private fun color(attribute: Int, fallback: Int) = settingsColor(requireContext(), attribute, fallback)
    override fun onDestroyView() {
        // f8.w0.D1 / v5.b: process-local state, expiring 180 seconds after dismissal.
        cache = Cache(source.text.toString(), sourceLanguage.code, targetLanguage.code, SystemClock.elapsedRealtime() / 1000 + 180)
        request?.cancel()
        super.onDestroyView()
    }

    private data class Response(val result: TranslationResult?)
    private data class Cache(val query: String, val from: String, val to: String, val expires: Long)
    companion object {
        const val TAG = "text_translation"
        private const val INPUT = 1
        private const val RESULT = 2
        private var cache: Cache? = null

        fun newInstance(text: String?, width: Int, gravity: Int, homepage: Boolean = false) = TextTranslationFragment().apply {
            arguments = Bundle().apply { putString("text", text); putInt("width", width); putInt("gravity", gravity); putBoolean("homepage", homepage) }
        }

        /** f8.w0.V1 uses t1.m/n, including the original unsupported zh_HANT fallback. */
        private fun defaultLanguage(locale: Locale): String {
            var language = when (locale.language) {
                "in" -> "id"; "iw" -> "he"; "ji" -> "yi"; "jw" -> "jv"; "tl" -> "fil"
                else -> locale.language
            }
            if (language == "no" && locale.country == "NO" && locale.variant == "NY") language = "nn"
            if (language == "zh" && locale.country == "TW") language = "zh_HANT"
            return language
        }
    }
}
