package dev.ujhhgtg.via.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.skins.setSkinImageResource

/**
 * Retained single-page settings view used while original child fragments are ported.
 *
 * The root order is the order built by hb.o6.g3(): General, Customization, Privacy,
 * Advanced, Scripts, optional Experimental, and About. Page rows below are taken from
 * each corresponding fragment's row construction (hb.c4, d9.q, hb.y4, hb.z, sa.d1,
 * and hb.d). Activities persist changes through [Listener]; this view has no store.
 */
@SuppressLint("ViewConstructor")
class SettingsScreen(
    context: Context,
    private var state: State,
    private val listener: Listener,
    val page: Page = Page.ROOT,
) : LinearLayout(context) {

    data class State(
        val restoreTabs: Int = 0,
        val safeBrowsing: Boolean = true,
        val doNotTrack: Boolean = false,
        val disableWebRtc: Boolean = false,
        val doNotSellOrShare: Boolean = false,
        val saveData: Boolean = false,
        val webPageDebug: Boolean = false,
        val experimentalAvailable: Boolean = false,
        val showUndoCloseTab: Boolean = false,
        val showSnifferButton: Boolean = false,
        val disablePredictiveBack: Boolean = false,
        val disableCustomTabs: Boolean = false,
        val scriptsEnabled: Boolean = true,
        val blurEffect: Boolean = false,
        val showSettingsBackground: Boolean = false,
        val nightCss: Boolean = false,
        val readerConfirmation: Boolean = false,
        val typeface: Typeface = Typeface.DEFAULT,
    )

    interface Listener {
        fun onBack()

        fun onReadState(): State
        fun onSafeBrowsingChanged(enabled: Boolean)

        /** Handles a real Via settings action (the semantic key is a source string name). */
        fun onAction(name: String)
    }

    private fun themeColor(attribute: Int, fallback: Int): Int {
        val values = context.obtainStyledAttributes(intArrayOf(attribute))
        return try { values.getColor(0, fallback) } finally { values.recycle() }
    }
    private val rowColor = themeColor(android.R.attr.textColorPrimary, context.textAppearanceColor())
    private val backgroundColor = themeColor(android.R.attr.colorBackground, Color.WHITE)
    private data class Row(val label: String, val description: String?, val checked: Boolean?, val action: (Boolean) -> Unit)
    private val rows = mutableListOf<Row>()
    private val pageBody = RecyclerView(context)
    private val rowAdapter = object : RecyclerView.Adapter<RowHolder>() {
        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int) = if (rows[position].checked == null) 0 else 1
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int) = RowHolder(viewType == 1)
        override fun onBindViewHolder(holder: RowHolder, position: Int) = holder.bind(rows[position])
    }
    private val titleView = TextView(context)
    private var titleClickTime = 0L
    private var titleClickCount = 0

    enum class Page(val action: String, val title: Int) {
        ROOT("settings", R.string.settings), GENERAL("settings_general", R.string.settings_general),
        PRIVACY("settings_privacy", R.string.settings_privacy), ADVANCED("settings_advanced", R.string.settings_advanced),
        SCRIPTS("settings_script", R.string.settings_script), EXPERIMENTAL("experimental", R.string.experimental),
        ABOUT("settings_about", R.string.settings_about), NIGHT("action_night", R.string.action_night), READER("reader_mode", R.string.reader_mode);

        companion object { fun fromAction(action: String?) = entries.firstOrNull { it.action == action } }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(backgroundColor)
        addView(createToolbar(), LayoutParams(MATCH_PARENT, context.dp(54f)))
        pageBody.layoutManager = LinearLayoutManager(context)
        pageBody.adapter = rowAdapter
        pageBody.clipToPadding = false
        pageBody.isVerticalScrollBarEnabled = false
        addView(pageBody, LayoutParams(MATCH_PARENT, 0, 1f))
        showPage()

    }

    private fun createToolbar(): View = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = backgroundColor.toDrawable()
        addView(ImageView(context).apply {
            setSkinImageResource(R.drawable.chevron_left); setColorFilter(rowColor)
            setPadding(context.dp(13f), 0, context.dp(13f), 0)
            background = selectableBackground()
            contentDescription = context.getString(R.string.operation_goback)
            setOnClickListener { listener.onBack() }
        }, LayoutParams(context.dp(48f), MATCH_PARENT).apply { setMargins(context.dp(1f), context.dp(1f), context.dp(1f), context.dp(1f)) })
        titleView.apply {
            textSize = 16f; setTextColor(rowColor); gravity = Gravity.CENTER_VERTICAL
            typeface = Typeface.create(state.typeface, Typeface.BOLD); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            if (page == Page.ROOT) setOnClickListener {
                val now = SystemClock.elapsedRealtime()
                titleClickCount = if (now - titleClickTime < 500) titleClickCount + 1 else 1
                titleClickTime = now
                if (titleClickCount == 5) {
                    titleClickCount = 0
                    listener.onAction("experimental_available:${!state.experimentalAvailable}")
                    refresh()
                }
            }
        }
        addView(titleView, LayoutParams(0, MATCH_PARENT, 1f))
    }

    private fun showPage() {
        state = listener.onReadState()
        titleView.text = context.getString(page.title)
        rows.clear()
        when (page) {
            Page.ROOT -> {
                rootRow(Page.GENERAL)
                actionRow("homepage_customization", R.string.settings_skin)
                listOf(Page.PRIVACY, Page.ADVANCED, Page.SCRIPTS).forEach(::rootRow)
                if (state.experimentalAvailable) rootRow(Page.EXPERIMENTAL)
                rootRow(Page.ABOUT)
            }
            Page.GENERAL -> showGeneral()
            Page.PRIVACY -> {
                toggleRow("do_not_track", R.string.do_not_track, state.doNotTrack)
                toggleRow("disable_webrtc", R.string.disable_webrtc, state.disableWebRtc)
                toggleRow("do_not_sell_or_share", R.string.do_not_sell_or_share, state.doNotSellOrShare, R.string.do_not_sell_or_share_description)
            }
            Page.ADVANCED -> {
                toggleRow("save_data", R.string.save_data, state.saveData, R.string.save_data_description)
                toggleRow("show_sniffer_btn_automatically", R.string.show_sniffer_btn_automatically, state.showSnifferButton)
                toggleRow("web_page_debug", R.string.web_page_debug, state.webPageDebug)
                if (Build.VERSION.SDK_INT >= 33) toggleRow("disable_predictive_back_gesture", R.string.disable_predictive_back_gesture, state.disablePredictiveBack)
                toggleRow("disable_custom_tabs", R.string.disable_custom_tabs, state.disableCustomTabs, R.string.disable_custom_tabs_description)
                toggleRow("title_disable_safe_browsing", R.string.title_disable_safe_browsing, !state.safeBrowsing, R.string.title_disable_safe_browsing_description) { listener.onSafeBrowsingChanged(!it) }
                actionRow("title_ignore_ssl_warnings", R.string.title_ignore_ssl_warnings, R.string.title_ignore_ssl_warnings_description)
            }
            Page.SCRIPTS -> {
                toggleRow("enable_scripts", R.string.enable_scripts, state.scriptsEnabled)
                actionRow("update_interval", R.string.update_interval); actionRow("settings_script", R.string.settings_script)
            }
            Page.EXPERIMENTAL -> {
                actionRow("skins", R.string.skins)
                if (Build.VERSION.SDK_INT >= 31) toggleRow("blur_effect", R.string.blur_effect, state.blurEffect, R.string.blur_effect_description)
                toggleRow("show_background_in_settings", R.string.show_background_in_settings, state.showSettingsBackground)
            }
            Page.ABOUT -> listOf("debugging_info" to R.string.debugging_info, "check_for_updates" to R.string.check_for_updates, "join_telegram_group" to R.string.join_telegram_group, "join_qq_group" to R.string.join_qq_group, "email_me" to R.string.email_me, "wechat_official_account" to R.string.wechat_official_account,
                "help_us_translate" to R.string.help_us_translate, "terms_of_use" to R.string.terms_of_use, "privacy_policy" to R.string.privacy_policy, "open_source_licenses" to R.string.open_source_licenses).forEach { (action, label) -> actionRow(action, label) }
            Page.NIGHT -> {
                actionRow("night_filter_for_web_contents", R.string.night_filter_for_web_contents); toggleRow("force_dark_mode_for_web_contents", R.string.force_dark_mode_for_web_contents, state.nightCss, R.string.force_dark_mode_for_web_contents_description)
            }
            Page.READER -> {
                toggleRow("require_confirmation_to_enable_reader_mode", R.string.require_confirmation_to_enable_reader_mode, state.readerConfirmation)
                actionRow("theme_color", R.string.theme_color); actionRow("text_size", R.string.size); actionRow("custom_reader_css", R.string.custom_reader_css)
            }
        }
        rowAdapter.notifyDataSetChanged()
    }

    private fun rootRow(target: Page) = actionRow(target.action, target.title)

    private fun showGeneral() {
        listOf("user_syns" to R.string.user_syns, "agent" to R.string.agent, "clear_data" to R.string.clear_data, "block_ads" to R.string.block_ads, "site_conf" to R.string.site_conf, "password_manager" to R.string.password_manager, "action_night" to R.string.action_night, "reader_mode" to R.string.reader_mode,
            "toolbars_settings" to R.string.toolbars_settings, "customize_menu" to R.string.customize_menu, "customize_context_menu" to R.string.customize_context_menu, "language" to R.string.language, "home" to R.string.home, "search_settings" to R.string.search_settings,
            "orientation" to R.string.orientation, "download" to R.string.download, "addon_download" to R.string.addon_download, "external_video_player" to R.string.external_video_player, "clear_data_on_exit" to R.string.clear_data_on_exit, "font" to R.string.font, "settings_operation" to R.string.settings_operation,
            "import_or_export_bookmarks" to R.string.import_or_export_bookmarks, "import_data" to R.string.import_data, "export_data" to R.string.export_data, "restore_tabs" to R.string.restore_tabs).forEach { (action, label) -> actionRow(action, label) }
        toggleRow("show_toast_to_undo_closing_tab", R.string.show_toast_to_undo_closing_tab, state.showUndoCloseTab, R.string.show_toast_to_undo_closing_tab_description)
        actionRow("setting_default", R.string.setting_default)
    }

    fun refresh() {
        val scroll = pageBody.layoutManager?.onSaveInstanceState()
        showPage()
        pageBody.layoutManager?.onRestoreInstanceState(scroll)
        titleView.typeface = Typeface.create(state.typeface, Typeface.BOLD)
    }

    private fun actionRow(action: String, label: Int, description: Int? = null) {
        rows += Row(context.getString(label), description?.let(context::getString), null) { listener.onAction(action) }
    }

    private fun toggleRow(key: String, label: Int, checked: Boolean, description: Int? = null,
        changed: (Boolean) -> Unit = { listener.onAction("$key:$it") }) {
        rows += Row(context.getString(label), description?.let(context::getString), checked, changed)
    }

    private fun selectableBackground(): android.graphics.drawable.Drawable? {
        val values = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        return try { values.getDrawable(0) } finally { values.recycle() }
    }

    /** a6.r/a6.o: wrap-content rows, 16×20 dp padding and 14/12 sp text. */
    private inner class RowHolder(toggle: Boolean) : RecyclerView.ViewHolder(RelativeLayout(context)) {
        private val root = itemView as RelativeLayout
        private val title = TextView(context).apply {
            id = generateViewId(); textSize = 14f; setTextColor(rowColor)
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        private val description = TextView(context).apply {
            textSize = 12f; setTextColor(themeColor(android.R.attr.textColorSecondary, rowColor))
            maxLines = 3; ellipsize = TextUtils.TruncateAt.END
        }
        private val checkBox = if (toggle) CheckBox(context).apply {
            id = generateViewId(); isClickable = false; isFocusable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        } else null

        init {
            root.layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            root.setPadding(context.dp(16f), context.dp(20f), context.dp(16f), context.dp(20f))
            root.background = selectableBackground()
            checkBox?.let { box -> root.addView(box, RelativeLayout.LayoutParams(WRAP_CONTENT, context.dp(20f)).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END); addRule(RelativeLayout.CENTER_VERTICAL); marginStart = context.dp(12f)
            }) }
            root.addView(title, RelativeLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                addRule(RelativeLayout.ALIGN_PARENT_START); checkBox?.let { addRule(RelativeLayout.START_OF, it.id) }
            })
            root.addView(description, RelativeLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                addRule(RelativeLayout.ALIGN_PARENT_START); addRule(RelativeLayout.BELOW, title.id)
                checkBox?.let { addRule(RelativeLayout.START_OF, it.id) }
            })
        }

        fun bind(row: Row) {
            title.text = row.label; title.typeface = state.typeface
            description.text = row.description; description.typeface = state.typeface
            description.visibility = if (row.description.isNullOrEmpty()) GONE else VISIBLE
            checkBox?.isChecked = row.checked == true
            root.setOnClickListener {
                val enabled = !(checkBox?.isChecked ?: false)
                checkBox?.isChecked = enabled
                row.action(enabled)
            }
        }
    }
}
