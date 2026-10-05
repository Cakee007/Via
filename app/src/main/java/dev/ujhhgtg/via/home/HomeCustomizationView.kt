package dev.ujhhgtg.via.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.get
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.common.GeneratedDocumentState
import dev.ujhhgtg.via.common.WindowInsetsHelper
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.FavoritesRepository
import dev.ujhhgtg.via.settings.SettingsToolbar
import dev.ujhhgtg.via.settings.TextEditorFragment
import dev.ujhhgtg.via.settings.settingsColor
import dev.ujhhgtg.via.ui.HomeDocument
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import java.io.File
import java.util.concurrent.Executors

/** d9.q/t and e9.u/i/l/v/y, retaining the source preview and panel event contracts. */
@SuppressLint("ViewConstructor")
class HomeCustomizationView(
    private val activity: Activity,
    private val onClose: () -> Unit,
    private val onChanged: () -> Unit = {},
    private val launchForResult: (Intent, Int) -> Unit,
    private val editorOwner: LifecycleOwner = activity as LifecycleOwner,
) : FrameLayout(activity), AutoCloseable {
    private val preferences = BrowserPreferences(context)
    private val database = BrowserDatabase.shared(context)
    private val document = HomeDocument(context, preferences)
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val preview = WebView(context)
    private var controls = HomeCustomizationControls(context)
    private val panel: FrameLayout
    private val tabs: RadioGroup
    private val paneIds = intArrayOf(R.id.home_custom_logo, R.id.home_custom_search, R.id.home_custom_background,
        R.id.home_custom_favorites, R.id.home_custom_advanced)
    private var selectedPane = -1
    private var pickedImage = ""
    private var previewLoaded = false
    private var backgroundLayer: Drawable? = null
    private var backgroundAlpha = -1
    private var closed = false
    private var pendingEditor = false
    private fun text(id: Int) = context.getString(id)
    private fun night() = preferences.isNightMode
    private fun surface() = settingsColor(context, R.attr.viaSurfaceColor, Color.WHITE)

    init {
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xff222222.toInt()) }
        val toolbar = SettingsToolbar(context, onClose).apply {
            setTitle(R.string.settings_skin)
            setBackgroundColor(0xff222222.toInt())
            setContentColor(Color.WHITE)
        }
        root.addView(toolbar)
        val content = LayoutInflater.from(context).inflate(R.layout.home_customization, root, false)
        panel = content.findViewById(R.id.home_custom_panel)
        tabs = content.findViewById(R.id.home_custom_tabs)
        tabs.clipToPadding = true
        tabs.background = panelBackground(true)
        panel.background = panelBackground(true)
        panel.addView(controls, LayoutParams(-1, -1))
        configurePreview()
        content.findViewById<FrameLayout>(R.id.home_custom_preview).addView(preview, LayoutParams(-1, -1))
        val prompt = content.findViewById<View>(R.id.home_custom_prompt)
        prompt.visibility = if (preferences.home == "about:home") GONE else VISIBLE
        content.findViewById<View>(R.id.home_custom_set_home).setHomeControlClickListener {
            prompt.animate().alpha(0f).translationY(-100f).setDuration(240L).withEndAction { prompt.visibility = GONE }.start()
            preferences.home = "about:home"
            GeneratedDocumentState.mark(GeneratedDocumentState.PAGE_SETTINGS)
            onChanged()
        }
        paneIds.forEachIndexed { index, id ->
            content.findViewById<RadioButton>(id).apply {
                typeface = preferences.selectedTypeface()
                setHomeControlClickListener { selectPane(if (selectedPane == index) -1 else index) }
            }
        }
        controls.onPaletteClick = ::chooseBackground
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(root, LayoutParams(-1, -1))
        WindowInsetsHelper.apply(root, null, tabs)
        WindowInsetsHelper.setLightStatusBar(context, false)
        WindowInsetsHelper.setLightNavigationBar(context, HomeDesign.isLight(surface()))
        refreshPreview(backgroundChanged = true)
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun configurePreview() {
        preview.settings.apply {
            javaScriptEnabled = true; cacheMode = WebSettings.LOAD_NO_CACHE
            allowContentAccess = true; allowFileAccess = true
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
        }
        preview.setOnTouchListener { _, _ -> true }
        preview.isHorizontalScrollBarEnabled = false; preview.isVerticalScrollBarEnabled = false
        preview.setBackgroundColor(Color.TRANSPARENT)
        preview.scaleX = .95f; preview.scaleY = .95f
        preview.isClickable = false; preview.isFocusable = false
        preview.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, resources.getDimensionPixelSize(R.dimen.menu_corner_radius).toFloat())
            }
        }
        preview.clipToOutline = true
        preview.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = css(request.url.toString())
            @Deprecated("Android API")
            override fun shouldInterceptRequest(view: WebView, url: String?): WebResourceResponse? = css(url)
        }
    }

    private fun css(url: String?): WebResourceResponse? {
        if (url?.endsWith("homepage.css") != true) return null
        return runCatching {
            WebResourceResponse("text/css", "UTF-8", File(context.filesDir, "homepage.css").inputStream()).apply {
                responseHeaders = mapOf("Cache-Control" to "no-cache", "Access-Control-Allow-Origin" to "*", "Content-Type" to "text/css")
            }
        }.getOrNull()
    }

    private fun refreshPreview(backgroundChanged: Boolean = false, clearCache: Boolean = false) {
        if (closed) return
        if (clearCache) preview.clearCache(false)
        if (!backgroundChanged) updateBackground()
        val dark = night()
        io.execute {
            runCatching {
                val uri = document.write(FavoritesRepository(database).list(), dark, false)
                val background = if (backgroundChanged) {
                    HomeBackground.createWindowImage(context, preferences.backgroundHome) ?: (preferences.urlBarColor.takeIf { it != -1 }
                        ?: settingsColor(
                            context,
                            R.attr.viaBackgroundColor,
                            Color.WHITE
                        )).toDrawable()
                } else null
                uri to background
            }.onSuccess { result -> main.post {
                if (closed) return@post
                result.second?.let { backgroundLayer = it; backgroundAlpha = -1; updateBackground() }
                if (previewLoaded) preview.reload() else { previewLoaded = true; preview.loadUrl(result.first) }
            } }.onFailure { android.util.Log.w("ViaCustomization", "Unable to update homepage preview", it) }
        }
        onChanged()
    }

    /** d9.q.z3: the native black filter is independent of the generated CSS. */
    private fun updateBackground() {
        val base = backgroundLayer ?: return
        val nightAlpha = if (night()) if (HomeDesign.isLight(preferences.urlBarColor)) 128 else 64 else 0
        val alpha = maxOf(nightAlpha, ((preferences.backgroundInfo and 127) / 100f * 255).toInt())
        if (alpha == backgroundAlpha) return
        backgroundAlpha = alpha
        preview.background = LayerDrawable(arrayOf(base, Color.argb(alpha, 0, 0, 0).toDrawable()))
    }

    private fun panelBackground(rounded: Boolean) = GradientDrawable().apply {
        setColor(surface())
        val radius = if (rounded) resources.getDimensionPixelSize(R.dimen.menu_corner_radius).toFloat() else 0f
        cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
    }

    private fun selectPane(index: Int) {
        selectedPane = index
        if (index < 0) {
            tabs.clearCheck()
            if (panel.isVisible) panel.animate().translationY(panel.height.toFloat())
                .setDuration(200L).setInterpolator(PathInterpolator(.2f, .2f, .8f, .8f))
                .withEndAction { tabs.background = panelBackground(true); panel.visibility = GONE }.start()
            return
        }
        tabs.check(paneIds[index])
        panel.removeAllViews()
        controls = HomeCustomizationControls(context).also {
            it.onPaletteClick = ::chooseBackground
            panel.addView(it, LayoutParams(-1, -1))
        }
        if (panel.isGone) panel.animate().translationY(0f)
            .setDuration(200L).setInterpolator(PathInterpolator(.2f, .2f, .8f, .8f))
            .withStartAction { panel.visibility = VISIBLE }.withEndAction { tabs.background = panelBackground(false) }.start()
        controls.backgroundPane = index == 2
        when (index) {
            0 -> logoControls()
            1 -> searchControls()
            2 -> {
                backgroundControls(show = !preferences.backgroundHome.isNullOrEmpty())
                if (preferences.backgroundHome.isNullOrEmpty()) controls.post { if (!closed && selectedPane == 2) controls.showPalette(backgroundSelection()) }
            }
            3 -> favoriteControls()
            4 -> advancedControls()
        }
    }

    private fun range(icon: Int, title: Int, value: Int, minimum: Int, maximum: Int, format: String, adaptive: Boolean = false,
        save: (Int) -> Unit) = HomeControl.Range(icon, text(title), if (adaptive && value == 0) minimum else value,
        minimum, maximum, format, if (adaptive) minimum else -1, if (adaptive) text(R.string.adaptive) else null) {
        save(if (adaptive && it == minimum) 0 else it)
        GeneratedDocumentState.mark(GeneratedDocumentState.HOME_STYLE)
        refreshPreview()
    }

    private fun toggle(icon: Int, title: Int, checked: Boolean, on: Int, off: Int, save: (Boolean) -> Unit) =
        HomeControl.Toggle(icon, text(title), checked, text(on), text(off)) { save(it); GeneratedDocumentState.mark(GeneratedDocumentState.HOME_STYLE); refreshPreview() }

    /** e9.l: 24 means Adaptive and stores zero; toggles retain their own source labels/icons. */
    private fun favoriteControls() {
        val bits = preferences.favoritesInfo
        controls.setControls(listOf(
            range(R.drawable.arrows_horizontal, R.string.width, bits and 127, 24, 80, "%dpx", true) {
                preferences.favoritesInfo = HomeDesign.bits(preferences.favoritesInfo, 0, 7, it)
            },
            range(R.drawable.custom_height, R.string.height, bits shr 7 and 127, 24, 80, "%dpx", true) {
                preferences.favoritesInfo = HomeDesign.bits(preferences.favoritesInfo, 7, 7, it)
            },
            range(R.drawable.custom_opacity, R.string.corner_radius, bits shr 14 and 127, 0, 100, "%d%%") {
                preferences.favoritesInfo = HomeDesign.bits(preferences.favoritesInfo, 14, 7, it)
            },
            toggle(R.drawable.custom_background, R.string.favorites_icon_style, bits and 2097152 != 0,
                R.string.favorites_icon_style_text, R.string.favorites_icon_style_icon_first) {
                preferences.favoritesInfo = HomeDesign.bit(preferences.favoritesInfo, 2097152, it)
                GeneratedDocumentState.mark(GeneratedDocumentState.HOME_CONTENT)
            },
            toggle(R.drawable.custom_theme, R.string.favorites_icon_color, bits and 4194304 != 0,
                R.string.favorites_icon_color_transparent, R.string.favorites_icon_color_colorful) {
                preferences.favoritesInfo = HomeDesign.bit(preferences.favoritesInfo, 4194304, it)
                GeneratedDocumentState.mark(GeneratedDocumentState.HOME_CONTENT)
            },
        ))
    }

    /** e9.v */
    private fun searchControls() {
        val bits = preferences.searchInfo
        controls.setControls(listOf(
            range(R.drawable.custom_opacity, R.string.corner_radius, bits and 127, 0, 100, "%d%%") { preferences.searchInfo = HomeDesign.bits(preferences.searchInfo, 0, 7, it) },
            range(R.drawable.custom_theme_fill, R.string.opacity, bits shr 7 and 127, 0, 100, "%d%%") { preferences.searchInfo = HomeDesign.bits(preferences.searchInfo, 7, 7, it) },
            range(R.drawable.custom_search_line, R.string.stroke_width, bits shr 21 and 7, 0, 7, "%dpx") { preferences.searchInfo = HomeDesign.bits(preferences.searchInfo, 21, 3, it) },
            range(R.drawable.custom_search_rectangle, R.string.stroke_opacity, bits shr 14 and 127, 0, 100, "%d%%") { preferences.searchInfo = HomeDesign.bits(preferences.searchInfo, 14, 7, it) },
            toggle(R.drawable.custom_favorite, R.string.search_bar_style, bits and 16777216 != 0, R.string.search_bar_style_line, R.string.search_bar_style_rectangle) {
                preferences.searchInfo = HomeDesign.bit(preferences.searchInfo, 16777216, it)
            },
        ))
    }

    /** e9.u */
    private fun logoControls() {
        val mode = preferences.logoChoice
        val bits = preferences.logoInfo
        val choices = intArrayOf(R.string.default_set, R.string.picture, R.string.text, R.string.html_code, R.string.none)
        val rows = mutableListOf<HomeControl>(HomeControl.Action(R.drawable.custom_logo, text(R.string.skin_logo), text(choices[mode.coerceIn(0, 4)]), ::chooseLogo))
        if (mode == 0 || mode == 1) {
            rows += range(R.drawable.arrows_horizontal, R.string.width, bits and 127, 10, 127, "%dpx", true) { preferences.logoInfo = HomeDesign.bits(preferences.logoInfo, 0, 7, it) }
            rows += range(R.drawable.custom_height, R.string.height, bits shr 7 and 127, 10, 127, "%dpx", true) { preferences.logoInfo = HomeDesign.bits(preferences.logoInfo, 7, 7, it) }
            rows += range(R.drawable.custom_opacity, R.string.corner_radius, bits shr 14 and 127, 0, 100, "%d%%") { preferences.logoInfo = HomeDesign.bits(preferences.logoInfo, 14, 7, it) }
        } else if (mode == 2) {
            rows += range(R.drawable.custom_size, R.string.size, bits shr 21 and 63, 10, 63, "%dpx") { preferences.logoInfo = HomeDesign.bits(preferences.logoInfo, 21, 6, it) }
            // e9.u.T2 shows both words initially if either bit is set; U2 uses the actual pair thereafter.
            val initial = if (bits and 402653184 != 0) (text(R.string.bold) + " " + text(R.string.italics)).trim() else null
            lateinit var font: HomeControl.Action
            font = HomeControl.Action(R.drawable.custom_font_style, text(R.string.font_style), initial) {
                val current = preferences.logoInfo
                val next = ((if (current and 134217728 != 0) 1 else 0) + (if (current and 268435456 != 0) 2 else 0) + 1) % 4
                preferences.logoInfo = HomeDesign.bit(HomeDesign.bit(current, 134217728, next and 1 != 0), 268435456, next and 2 != 0)
                font.subtitle = listOfNotNull(text(R.string.bold).takeIf { next and 1 != 0 }, text(R.string.italics).takeIf { next and 2 != 0 }).joinToString(" ").ifEmpty { null }
                GeneratedDocumentState.mark(GeneratedDocumentState.HOME_STYLE)
                controls.refreshControl(font); refreshPreview()
            }
            rows += font
        }
        controls.setControls(rows)
    }

    private fun chooseLogo() {
        val labels = intArrayOf(R.string.default_set, R.string.picture, R.string.text, R.string.html_code, R.string.none).map(::text).toTypedArray()
        ViaDialog(activity).title(R.string.skin_logo).singleChoice(labels, preferences.logoChoice) { mode ->
            when (mode) {
                0 -> {
                    preferences.logoChoice = 0; preferences.homeTag = null
                    preferences.logoInfo = HomeDesign.bits(HomeDesign.bits(HomeDesign.bits(preferences.logoInfo, 0, 7, 0), 7, 7, 72), 14, 7, 0)
                    GeneratedDocumentState.mark(GeneratedDocumentState.HOME_CONTENT or GeneratedDocumentState.HOME_STYLE)
                    logoControls(); refreshPreview()
                }
                1 -> pickImage("logo")
                2 -> editLogo(false)
                3 -> editLogo(true)
                4 -> {
                    preferences.logoChoice = 4; preferences.homeTag = "<br>"
                    GeneratedDocumentState.mark(GeneratedDocumentState.HOME_CONTENT)
                    logoControls(); refreshPreview()
                }
            }
        }.show()
    }

    private fun editLogo(html: Boolean) {
        val mode = if (html) 3 else 2
        val key = if (html) "KEY_LOGO_HTML" else "KEY_LOGO_TEXT"
        val initial = preferences.homeTag?.takeIf { preferences.logoChoice == mode && it.isNotEmpty() }
            ?: HomeCustomizationMedia.readLogoCache(context, key)
        fun save(value: String) {
            preferences.logoChoice = mode
            preferences.homeTag = if (html && value.isEmpty()) "<br>" else value
            GeneratedDocumentState.mark(GeneratedDocumentState.HOME_CONTENT)
            logoControls(); refreshPreview()
            io.execute { runCatching { HomeCustomizationMedia.writeLogoCache(context, key, value) } }
        }
        if (html) editCode(R.string.html_code, initial, ::save)
        else ViaDialog(activity).title(R.string.text).input(initial, "", 4)
            .positive(android.R.string.ok) { _, result -> result.edit?.firstOrNull()?.let(::save) }
            .negative(android.R.string.cancel).show()
    }

    /** e9.i */
    private fun backgroundControls(show: Boolean = true) {
        val image = !preferences.backgroundHome.isNullOrEmpty()
        if (!image && backgroundSelection() < 0) {
            controls.setControls(emptyList(), show)
            return
        }
        val label = if (image) R.string.background_image else if (preferences.urlBarColor == -1) R.string.default_set else R.string.background_color
        val rows = mutableListOf<HomeControl>(HomeControl.Action(R.drawable.custom_background_choice, text(R.string.skin_bg), text(label)) {
            controls.showPalette(backgroundSelection())
        })
        if (image) {
            val info = preferences.backgroundInfo
            val light = if (info and 128 != 0) info and 256 != 0 else HomeDesign.isLight(preferences.urlBarColor)
            rows += toggle(R.drawable.pencil_tilted, R.string.theme_color, light, R.string.theme_color_dark, R.string.theme_color_light) {
                preferences.backgroundInfo = HomeDesign.bit(preferences.backgroundInfo or 128, 256, it)
            }
            rows += range(R.drawable.custom_background_opacity, R.string.opacity, info and 127, 0, 80, "%d%%") {
                preferences.backgroundInfo = HomeDesign.bits(preferences.backgroundInfo, 0, 7, it)
            }
        }
        controls.setControls(rows, show)
    }

    private fun backgroundSelection(): Int {
        if (!preferences.backgroundHome.isNullOrEmpty()) return 1
        val color = preferences.urlBarColor.takeIf { it != -1 } ?: 0
        return HomeCustomizationControls.PALETTE.indexOfFirst { it.color == color }
    }

    private fun chooseBackground(position: Int) {
        if (position == 1) { pickImage("background"); return }
        if (position == backgroundSelection()) return
        HomeBackground.clearCache(context)
        val color = HomeCustomizationControls.PALETTE[position].color
        preferences.backgroundHome = ""
        preferences.urlBarColor = if (color == 0) -1 else color
        preferences.backgroundInfo = preferences.backgroundInfo and 255.inv()
        GeneratedDocumentState.mark(GeneratedDocumentState.HOME_CONTENT or GeneratedDocumentState.HOME_STYLE)
        controls.selectPalette(position)
        backgroundControls(show = false)
        refreshPreview(backgroundChanged = true)
    }

    /** e9.y / d9.t.q */
    private fun advancedControls() {
        controls.setControls(listOf(
            toggle(R.drawable.custom_favorite, R.string.search_bar_effect, preferences.customInfo and 1 != 0, R.string.search_bar_effect_blur, R.string.search_bar_effect_transparent) {
                preferences.customInfo = HomeDesign.bit(preferences.customInfo, 1, it)
            },
            toggle(R.drawable.custom_favorite_color, R.string.search_part, preferences.customInfo and 2 != 0, R.string.search_part_hide, R.string.search_part_show) {
                preferences.customInfo = HomeDesign.bit(preferences.customInfo, 2, it)
            },
            HomeControl.Action(R.drawable.plus, text(R.string.custom_css_special)) {
                editCode(R.string.custom_css_special, preferences.cssTheme.orEmpty()) {
                    preferences.cssTheme = it
                    GeneratedDocumentState.mark(GeneratedDocumentState.HOME_STYLE)
                    refreshPreview()
                }
            },
            HomeControl.Action(R.drawable.trash_tapered, text(R.string.favorites_clear_icons)) {
                ViaDialog(activity).title(R.string.clear_website_icon_cache).message(R.string.clear_website_icon_cache_message)
                    .positive(android.R.string.ok) { _, _ ->
                        if (deleteTree(File(context.filesDir, "icon"))) {
                            ViaToast.makeText(context, R.string.data_cleared, ViaToast.LENGTH_SHORT).show()
                            refreshPreview(clearCache = true)
                        }
                    }.negative(android.R.string.cancel).show()
            },
            HomeControl.Action(R.drawable.reload, text(R.string.customization_reset)) {
                ViaDialog(activity).title(R.string.reset_to_default_settings).message(R.string.dialog_sure)
                    .positive(android.R.string.ok) { _, _ -> resetDesign() }.negative(android.R.string.cancel).show()
            },
        ))
    }

    private fun resetDesign() {
        preferences.homeTag = null; preferences.logoChoice = 0; preferences.logoInfo = 193766400
        preferences.favoritesInfo = 1638446; preferences.searchInfo = 2490468
        preferences.backgroundHome = null; preferences.urlBarColor = -1
        preferences.customInfo = 0; preferences.backgroundInfo = 0; preferences.cssTheme = null
        GeneratedDocumentState.mark(GeneratedDocumentState.HOME_CONTENT or GeneratedDocumentState.HOME_STYLE)
        selectPane(-1)
        refreshPreview(backgroundChanged = true, clearCache = true)
    }

    private fun deleteTree(file: File): Boolean {
        if (!file.exists()) return true
        if (file.isDirectory) for (child in file.listFiles().orEmpty()) if (!deleteTree(child)) return false
        return file.delete()
    }

    private fun editCode(title: Int, value: String, saved: (String) -> Unit) {
        val shell = activity as Shell
        val manager = shell.supportFragmentManager
        pendingEditor = true
        manager.setFragmentResultListener("edit_text_result", editorOwner) { _, result ->
            manager.clearFragmentResultListener("edit_text_result"); pendingEditor = false
            result.getString("text")?.let(saved)
        }
        shell.navigate(TextEditorFragment.newInstance(text(title), value, text(title), true))
    }

    private fun pickImage(kind: String) {
        pickedImage = kind
        try { launchForResult(HomeCustomizationMedia.pickerIntent(context), 7300) }
        catch (error: ActivityNotFoundException) { android.util.Log.w("ViaCustomization", "Unable to open image picker", error) }
    }

    fun onActivityResult(request: Int, result: Int, data: Intent?): Boolean {
        if (request != 7300 || closed) return false
        val uri = HomeCustomizationMedia.selectedImage(result, data) ?: return true
        val logo = pickedImage == "logo"
        io.execute {
            runCatching { HomeCustomizationMedia.importImage(context, uri, if (logo) "logo" else "bg") }
                .onSuccess { path -> if (path != null) main.post { if (!closed) importedImage(path, logo) } }
                .onFailure { android.util.Log.w("ViaCustomization", "Unable to import image", it) }
        }
        return true
    }

    private fun importedImage(path: String, logo: Boolean) {
        if (logo) {
            preferences.logoChoice = 1
            preferences.homeTag = "<img class=\"smaller\" src=\"file://$path\" />"
            preferences.logoInfo = HomeDesign.bits(HomeDesign.bits(HomeDesign.bits(preferences.logoInfo, 0, 7, 80), 7, 7, 80), 14, 7, 100)
            GeneratedDocumentState.mark(GeneratedDocumentState.HOME_CONTENT or GeneratedDocumentState.HOME_STYLE)
            logoControls(); refreshPreview(clearCache = true)
            return
        }
        val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 8 })
        val valid = bitmap != null && bitmap.width > 0 && bitmap.height > 0
        if (valid) {
            val color = bitmap[0, 0].takeIf { it != 0 } ?: Color.BLACK
            preferences.urlBarColor = if (color < 0) color else -1
            preferences.backgroundHome = path
            preferences.backgroundInfo = preferences.backgroundInfo and 128.inv()
        } else {
            preferences.urlBarColor = -1; preferences.backgroundHome = null
            preferences.backgroundInfo = preferences.backgroundInfo and 255.inv()
        }
        bitmap?.recycle()
        GeneratedDocumentState.mark(GeneratedDocumentState.HOME_CONTENT or GeneratedDocumentState.HOME_STYLE)
        controls.selectPalette(if (valid) 1 else 0)
        backgroundControls()
        refreshPreview()
        io.execute {
            HomeBackground.clearCache(context)
            if (valid) HomeBackground.process(context, File(path))
            main.post { if (!closed) refreshPreview(backgroundChanged = true) }
        }
    }

    fun restoreState(state: Bundle?) {
        if (state == null) return
        pickedImage = state.getString("pickedImage").orEmpty()
        val selected = state.getInt("selectedPane", -1)
        if (selected in paneIds.indices) selectPane(selected)
    }
    fun saveState(out: Bundle) { out.putString("pickedImage", pickedImage); out.putInt("selectedPane", selectedPane) }
    fun onBack(): Boolean { onClose(); return true }
    fun onHostPause() { preview.onPause() }
    fun onHostResume() { preview.onResume() }
    override fun close() {
        if (closed) return
        closed = true
        if (pendingEditor) (activity as? Shell)?.supportFragmentManager?.clearFragmentResultListener("edit_text_result")
        main.removeCallbacksAndMessages(null)
        io.shutdown()
        (preview.parent as? ViewGroup)?.removeView(preview)
        preview.stopLoading(); preview.removeAllViews(); preview.destroy()
    }
}
