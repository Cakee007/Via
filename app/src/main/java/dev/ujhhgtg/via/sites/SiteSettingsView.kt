package dev.ujhhgtg.via.sites

import android.annotation.SuppressLint
import android.app.Activity
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.SettingsData
import dev.ujhhgtg.via.data.SettingsDataRepository
import dev.ujhhgtg.via.data.SiteConfiguration
import dev.ujhhgtg.via.data.SiteConfigurationRepository
import dev.ujhhgtg.via.settings.SettingsToolbar
import dev.ujhhgtg.via.settings.SettingsRow
import dev.ujhhgtg.via.settings.SettingsToggleRow
import dev.ujhhgtg.via.settings.SettingsHeadingRow
import dev.ujhhgtg.via.settings.SettingsRecyclerView
import dev.ujhhgtg.via.settings.TextZoomRepository
import dev.ujhhgtg.via.settings.TextZoomDialogs
import dev.ujhhgtg.via.settings.TextZoomSeekBar
import dev.ujhhgtg.via.browser.UrlPunycode
import androidx.recyclerview.widget.LinearLayoutManager
import dev.ujhhgtg.via.settings.settingsColor
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import dev.ujhhgtg.via.ui.dp

/** Saved-site list from hb/w6 and the original fifteen site rows from jb/u4.E3. */
@SuppressLint("ViewConstructor")
class SiteSettingsView(private val activity: Activity, private val toolbar: SettingsToolbar,
    private val onClose: () -> Unit, private val onOpenDomain: (String) -> Unit,
    private val onChanged: () -> Unit = {}, domain: String? = null, private val flags: Int = 0,
    private val onTextZoomRequested: (Int) -> Unit = {}) : FrameLayout(activity), AutoCloseable {
    private var closed = false
    private fun dp(value: Float) = context.dp(value)
    private fun finish() { persistCurrent(); onClose() }
    private val database: BrowserDatabase = BrowserDatabase(context)
    private val sites: SiteConfigurationRepository = SiteConfigurationRepository(database)
    private val preferences: BrowserPreferences = BrowserPreferences(context)
    val list = SettingsRecyclerView(context).apply { layoutManager = LinearLayoutManager(context); clipToPadding = false }
    private val empty = TextView(context).apply {
        text = "¯\\_(ツ)_/¯"; contentDescription = context.getString(R.string.empty_hint)
        gravity = Gravity.CENTER
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_empty_state_size).toFloat())
        setTextColor(settingsColor(context, R.attr.viaSecondaryTextColor, android.graphics.Color.GRAY))
        setPadding(0, dp(20f), 0, dp(20f)); visibility = GONE
    }
    private val modelRows = mutableListOf<SettingsRow>()
    private val rowClicks = mutableMapOf<Int, () -> Unit>()
    private val rows = SiteSettingsRowsAdapter { row -> rowClicks[row.id]?.invoke() }
    private var title: TextView
    private var action: View
    private var current: SiteConfiguration? = null
    private var original: SiteConfiguration? = null
    private var changedMask = flags and 1
    // z8.b4.b/e: zero is the system default UA; -1000 separately inherits the global choice.
    private val builtinAgents get() = listOf(R.string.agent_android_phone, R.string.agent_android_tablet, R.string.agent_windows_chrome, R.string.agent_windows_ie11, R.string.agent_osx, R.string.agent_iphone, R.string.agent_ipad, R.string.agent_symbian).map(::text)

    init {
        title = toolbar.titleView
        action = toolbar.addAction(R.drawable.plus, R.string.action_new) { domainDialog(null) }
        list.adapter = rows
        addView(list, LayoutParams(-1, -1))
        addView(empty, LayoutParams(-1, -1))
        if (domain != null) openDomain(domain, flags) else showList()
    }

    private fun text(name: Int) = context.getString(name)
    private fun default(value: String) = context.getString(R.string.default_description, value)
    private fun onOff(value: Boolean) = text(if (value) R.string.on else R.string.off)
    private fun allowed(value: Boolean) = text(if (value) R.string.allowed else R.string.blocked)
    private fun permissionLabel(mode: Int) = if (mode == 3) text(R.string.ask_first) else allowed(mode == 1)
    private fun clearRows() { modelRows.clear(); rowClicks.clear(); empty.visibility = GONE }
    private fun section(label: String) { modelRows += SettingsHeadingRow(label) }
    private fun row(label: String, summary: String? = null, enabled: Boolean = true, click: () -> Unit) {
        val id = modelRows.size + 1
        modelRows += SettingsRow(id, label, summary, disabled = !enabled)
        rowClicks[id] = click
    }
    private fun showList() {
        persistCurrent()
        current = null; original = null; clearRows(); title.text = text(R.string.all_sites)
        toolbar.removeView(action)
        action = toolbar.addAction(R.drawable.plus, R.string.action_new) { domainDialog(null) }
        val saved = sites.all().sortedWith { a, b -> TextZoomRepository.domainOrder.compare(a.domain, b.domain) }
        saved.forEach { site ->
            modelRows += SavedSiteRow(site.domain)
            rowClicks[site.domain.hashCode()] = { onOpenDomain(site.domain) }
        }
        rows.onLongClick = { anchor, row ->
            (row as? SavedSiteRow)?.let { site ->
                ViaDialog(activity).items(arrayOf(text(R.string.action_edit), text(R.string.action_delete)), onClick = { which ->
                    if (which == 0) domainDialog(site.domain) else { sites.remove(site.domain); onChanged(); showList() }
                }).showAnchored(anchor)
            }
            true
        }
        rows.submit(modelRows.toList())
        empty.visibility = if (saved.isEmpty()) VISIBLE else GONE
    }

    private fun domainDialog(previous: String?) {
        ViaDialog(activity).title(if (previous == null) R.string.action_new else R.string.action_edit)
            .input(previous.orEmpty(), "www.example.com", 1).canceledOnTouchOutside(false)
            .positive(android.R.string.ok) { _, result ->
                val value = result.edit?.firstOrNull().orEmpty().trim()
                val domain = UrlPunycode.encodeHost(if (value.contains("://")) dev.ujhhgtg.via.browser.DocumentPolicy.authority(value) else value)
                if (domain.isEmpty()) return@positive
                if (previous != null && domain != previous && !sites.rename(previous, domain)) {
                    dev.ujhhgtg.via.ui.ViaToast.makeText(context, context.getString(R.string.site_conf_exists, domain), dev.ujhhgtg.via.ui.ViaToast.LENGTH_LONG).show()
                    return@positive
                }
                onChanged()
                if (previous == null) onOpenDomain(domain) else showList()
            }.negative(android.R.string.cancel).show()
    }

    private fun openDomain(domain: String, flags: Int = 0) {
        original = sites.get(domain, true)
        current = sites.get(domain) ?: SiteConfiguration(domain, "{\"flags\":0}")
        if (current!!.isEmpty && flags and 4 != 0) current = current!!.withEnabled(true)
        render()
    }
    private fun update(config: SiteConfiguration) {
        current = config
        render()
    }
    /** jb.u4.V3/B1 writes the edited record on leaving, and reports only actual changes. */
    private fun persistCurrent() {
        val site = current ?: return
        val previous = original
        val changed = if (previous == null) !site.isEmpty else
            previous.userAgentChoice != site.userAgentChoice || previous.customUserAgent != site.customUserAgent ||
                previous.enabledFlags != site.enabledFlags || previous.flags != site.flags || previous.textZoomOverride != site.textZoomOverride
        if (changed) {
            if (site.isEmpty) sites.remove(site.domain) else sites.put(site)
            original = site.takeUnless { it.isEmpty }
            changedMask = changedMask or 1
            onChanged()
        }
    }

    private fun render() {
        val site = current ?: return
        val flags = preferences.webFlags
        clearRows(); title.text = text(R.string.site_conf)
        rows.onLongClick = null
        toolbar.removeView(action)
        action = toolbar.addAction(null, R.string.action_reset) {
            ViaDialog(activity).title(R.string.title_reset_site_conf)
                .message(context.getString(R.string.message_reset_site_conf, site.domain))
                .positive(R.string.action_reset) { _, _ ->
                    // jb.u4.h3 also removes WebView's cached location decisions.
                    android.webkit.GeolocationPermissions.getInstance().clearAll()
                    update(SiteConfiguration(site.domain, "{\"flags\":0}"))
                }
                .negative(android.R.string.cancel).show()
        }
        modelRows += SettingsToggleRow(1, context.getString(R.string.enable_site_conf, site.domain), checked = site.isEnabled)
        rowClicks[1] = { update(site.withEnabled(!site.isEnabled)) }
        section(text(R.string.category_content))
        val zoom = site.textZoomOverride.takeIf { it > 0 } ?: preferences.textSize
        row(text(R.string.size), if (site.textZoomOverride > 0) "$zoom%" else default("$zoom%"), site.isEnabled) { textSizePicker() }
        val agent = site.userAgentChoice.takeIf { it != -1000 } ?: preferences.userAgentChoice
        row(text(R.string.agent), (if (site.userAgentChoice == -1000) default(agentLabel(agent)) else if (agent == -999) site.customUserAgent else agentLabel(agent)), site.isEnabled) { agentPicker() }
        booleanRow(R.string.action_pcview, 8, flags and 2048 != 0)
        booleanRow(R.string.images, 4, flags and 16 != 0, permission = true,
            defaultSummary = if (flags and 32 != 0) text(R.string.images_allowed_via_wifi_description_short) else null)
        booleanRow(R.string.javascript, 2, flags and 8 != 0, permission = true)
        section(text(R.string.category_basics))
        booleanRow(R.string.block_ads, 16, flags and 1 != 0)
        booleanRow(R.string.action_incognito, 32, flags and 64 != 0)
        section(text(R.string.category_permissions))
        permissionRow(R.string.microphone, 2048, 4096, if (flags and 16777216 != 0) 3 else 2)
        permissionRow(R.string.camera, 8192, 16384, if (flags and 8388608 != 0) 3 else 2)
        permissionRow(R.string.clipboard, 64, 128, when { flags and 262144 != 0 -> 3; flags and 131072 != 0 -> 2; else -> 1 })
        permissionRow(R.string.open_app, 256, 512, when { flags and 67108864 != 0 -> 3; flags and 33554432 != 0 -> 2; else -> 1 })
        booleanRow(R.string.page_redirection, 1024, flags and 134217728 == 0, permission = true, inverted = true,
            offLabel = text(R.string.page_redirection_ask_first_description_short))
        permissionRow(R.string.location1, 65536, 32768, if (flags and 2 != 0) 3 else 2)
        section(text(R.string.settings_advanced))
        booleanRow(R.string.quick_back, 131072, flags and 512 != 0)
        rows.submit(modelRows.toList())
    }

    private fun booleanRow(key: Int, bit: Int, global: Boolean, permission: Boolean = false, inverted: Boolean = false, defaultSummary: String? = null, offLabel: String? = null) {
        val site = current ?: return
        val raw = site.booleanOverride(bit)
        val value = raw?.let { if (inverted) !it else it } ?: global
        fun label(on: Boolean) = if (!on && offLabel != null) offLabel else if (permission) allowed(on) else onOff(on)
        val summary = if (raw == null) default(defaultSummary ?: label(value)) else label(value)
        row(text(key), summary, site.isEnabled) {
            val options = arrayOf(default(defaultSummary ?: label(global)), if (permission) text(R.string.allow) else label(true), if (permission && offLabel == null) text(R.string.block) else label(false))
            ViaDialog(activity).title(text(key)).singleChoice(options, if (raw == null) 0 else if (value) 1 else 2) { index ->
                update(site.withBoolean(bit, if (index == 0) null else if (inverted) index != 1 else index == 1))
            }.positive(android.R.string.ok).show()
        }
    }

    private fun permissionRow(key: Int, decisionBit: Int, askBit: Int, global: Int) {
        val site = current ?: return
        val mode = site.permissionMode(decisionBit, askBit)
        row(text(key), if (mode == 0) default(permissionLabel(global)) else permissionLabel(mode), site.isEnabled) {
            val labels = arrayOf(default(permissionLabel(global)), text(R.string.allow), text(R.string.block), text(R.string.ask_first))
            ViaDialog(activity).title(text(key)).singleChoice(labels, mode) { value ->
                // jb.u4.r3 applies this to every location choice, including Default.
                if (key == R.string.location1) android.webkit.GeolocationPermissions.getInstance().clearAll()
                update(site.withPermission(decisionBit, askBit, value))
            }.positive(android.R.string.ok).show()
        }
    }

    private fun agentLabel(choice: Int): String = if (choice == 0) text(R.string.default_set) else if (choice in -8..-1) builtinAgents[-choice - 1]
        else SettingsDataRepository(database).find(choice)?.title ?: text(R.string.agent_custom)
    private fun agentPicker() {
        val site = current ?: return
        val custom = SettingsDataRepository(database).list(SettingsData.USER_AGENT)
        val ids = listOf(-1000, 0) + builtinAgents.indices.map { -it - 1 } + custom.map { it.id } + listOf(-999)
        val names = listOf(default(agentLabel(preferences.userAgentChoice)), text(R.string.default_set)) + builtinAgents + custom.map { it.title.orEmpty() } + listOf(text(R.string.agent_custom))
        ViaDialog(activity).title(R.string.agent).singleChoice(names.toTypedArray(), ids.indexOf(site.userAgentChoice)) { selected ->
            if (ids[selected] == -999) {
                val value = EditText(context).apply { setText(site.customUserAgent.orEmpty()); hint = text(R.string.agent); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI }
                ViaDialog(activity).title(R.string.agent_custom).customView(value)
                    .positive(android.R.string.ok) { _, _ -> update(site.withUserAgent(-999, value.text.toString())) }
                    .negative(android.R.string.cancel).show()
            } else update(site.withUserAgent(ids[selected]))
        }.show()
    }

    private fun textSizePicker() {
        val site = current ?: return
        // jb.u4.b4: the page opened from browser info delegates to the live site-only slider.
        if (flags and 2 != 0) { persistCurrent(); onTextZoomRequested(changedMask or 2); finish(); return }
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val sample = TextView(context).apply {
            text = TextZoomDialogs.sample(context); gravity = Gravity.CENTER; minHeight = dp(76f)
            typeface = preferences.selectedTypeface()
            setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, android.graphics.Color.BLACK))
        }
        layout.addView(sample, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(12f), dp(16f), dp(12f), dp(16f)) })
        val slider = TextZoomSeekBar(context).apply {
            highlightProgress = (preferences.textSize - 50) / 5
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, user: Boolean) {
                    val base = (14f * resources.displayMetrics.scaledDensity + .5f).toInt()
                    sample.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, (base * (value * 5 + 50) / 100).toFloat())
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
            progress = ((site.textZoomOverride.takeIf { it > 0 } ?: preferences.textSize) - 50) / 5
        }
        layout.addView(slider, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16f) })
        ViaDialog(activity).customView(layout)
            .positive(android.R.string.ok) { _, _ -> update(site.withTextZoom(slider.progress * 5 + 50)) }
            .negative(android.R.string.cancel).apply {
                if (site.textZoomOverride > 0) neutral(R.string.action_reset) { update(site.withTextZoom(0)) }
            }.show()
    }

    internal data class EditorState(val original: SiteConfiguration?, val current: SiteConfiguration?, val changedMask: Int)
    internal fun editorState() = EditorState(original, current, changedMask)
    internal fun restoreState(state: EditorState?) {
        if (state?.current == null) return
        original = state.original; current = state.current; changedMask = state.changedMask
        render()
    }
    fun refreshList() { if (current == null) showList() }
    fun onBack(): Boolean { finish(); return true }
    override fun close() { if (!closed) { closed = true; database.close() } }

    companion object {
        /** jb.u4.B1/V3 saves on Fragment destruction; theme detach/attach keeps its draft. */
        internal fun saveState(context: android.content.Context, state: EditorState?): Int {
            val site = state?.current ?: return 0
            val previous = state.original
            val changed = if (previous == null) !site.isEmpty else
                previous.userAgentChoice != site.userAgentChoice || previous.customUserAgent != site.customUserAgent ||
                    previous.enabledFlags != site.enabledFlags || previous.flags != site.flags || previous.textZoomOverride != site.textZoomOverride
            if (changed) {
                val sites = SiteConfigurationRepository(BrowserDatabase.shared(context))
                if (site.isEmpty) sites.remove(site.domain) else sites.put(site)
            }
            return state.changedMask or if (changed) 1 else 0
        }
    }
}
