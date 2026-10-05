package dev.ujhhgtg.via.sites

import android.os.Bundle
import android.view.View
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.common.GeneratedDocumentState
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.SiteConfiguration
import dev.ujhhgtg.via.data.SiteConfigurationRepository
import dev.ujhhgtg.via.settings.SettingsHeadingRow
import dev.ujhhgtg.via.settings.SettingsListFragment
import dev.ujhhgtg.via.settings.SettingsRow
import dev.ujhhgtg.via.settings.SettingsToggleRow
import dev.ujhhgtg.via.settings.SettingsToolbar
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import dev.ujhhgtg.via.browser.UrlPunycode
import dev.ujhhgtg.via.settings.TextZoomRepository
import java.util.Locale

/** The jb policy pages share the same a6 rows and ba.a override storage.
 * IDs are the navigation IDs in jb.y4.m3; masks follow w9.p and ba.b, not UI labels. */
class SitePolicySettingsFragment : SettingsListFragment() {
    private val policy: Policy get() = Policy.entries.first { it.id == requireArguments().getInt("policy") }
    private lateinit var preferences: BrowserPreferences
    private lateinit var database: BrowserDatabase
    private lateinit var sites: SiteConfigurationRepository
    private lateinit var rows: SiteSettingsRowsAdapter
    // jb's ViewModels keep their kb.d exception lists for this page's lifetime.
    private val exceptionSites = mutableMapOf<String, SiteConfiguration>()
    private val flags get() = preferences.webFlags

    override fun configureToolbar(toolbar: SettingsToolbar) = toolbar.setTitle(policy.title)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // jb.j2.V1 -> z8.b0.U clears Chromium's remembered geolocation decisions.
        if (policy == Policy.LOCATION) dev.ujhhgtg.via.engine.Engines.backend.clearLocationPermissions()
        preferences = BrowserPreferences(requireContext())
        GeneratedDocumentState.initialize(preferences)
        database = BrowserDatabase(requireContext())
        sites = SiteConfigurationRepository(database)
        exceptionSites.clear()
        if (policy.siteBit != 0) sites.all().forEach { saved ->
            sites.get(saved.domain)?.takeIf { it.isEnabled && exceptionMode(it) != 0 }?.let {
                exceptionSites[it.domain] = it
            }
        }
        rows = SiteSettingsRowsAdapter(::click)
        list.adapter = rows
        bindRows()
    }

    private fun mode(): Int = when (policy) {
        Policy.CLIPBOARD -> when { flags and 262144 != 0 -> 3; flags and 131072 != 0 -> 2; else -> 1 }
        Policy.OPEN_APP -> when { flags and 67108864 != 0 -> 3; flags and 33554432 != 0 -> 2; else -> 1 }
        Policy.LOCATION, Policy.CAMERA, Policy.MICROPHONE -> if (flags and policy.globalBit != 0) 3 else 2
        else -> if (enabled()) 1 else 2
    }
    private fun enabled() = (flags and policy.globalBit != 0) xor policy.inverted
    private fun setEnabled(value: Boolean) {
        val set = value xor policy.inverted
        preferences.webFlags = if (set) flags or policy.globalBit else flags and policy.globalBit.inv()
    }
    private fun changed(global: Boolean = true) {
        // jb.s0/o1/z1/j and i0 set w9.n.y; the other policy setters only
        // save webflag. s6.R1 consumes this on return, without reloading HTML.
        if (global && policy in listOf(Policy.DESKTOP, Policy.IMAGES, Policy.JAVASCRIPT, Policy.ADBLOCK, Policy.COOKIES)) {
            GeneratedDocumentState.mark(GeneratedDocumentState.PAGE_SETTINGS)
        }
        bindRows()
    }

    private fun bindRows() {
        val enabled = enabled()
        val currentMode = mode()
        val exceptions = exceptionSites.values.sortedWith { a, b -> TextZoomRepository.domainOrder.compare(a.domain, b.domain) }
        rows.submit(buildList {
            val summary = summary()
            if (policy == Policy.IMAGES || policy == Policy.CLIPBOARD || policy == Policy.OPEN_APP)
                add(SettingsRow(1, getString(policy.title), summary))
            else add(SettingsToggleRow(1, getString(policy.title), summary, if (policy.permission) currentMode == 3 else enabled))
            if (policy == Policy.COOKIES) {
                add(SettingsToggleRow(2, getString(R.string.block_3rd_party_cookies), checked = flags and 16384 == 0, disabled = !enabled))
            }
            if (policy.siteBit == 0) return@buildList
            add(AddSiteExceptionRow(getString(R.string.add_site_exception)))
            val blocked = exceptions.filter { exceptionMode(it) == 2 }
            if (blocked.isNotEmpty()) {
                add(SettingsHeadingRow(getString(when (policy) {
                    Policy.DESKTOP, Policy.ADBLOCK, Policy.QUICK_BACK -> R.string.off
                    Policy.REDIRECTION -> R.string.ask_first
                    else -> R.string.blocked
                })))
                blocked.forEach { add(ExceptionRow(it.domain)) }
            }
            val allowed = exceptions.filter { exceptionMode(it) == 1 }
            val ask = exceptions.filter { exceptionMode(it) == 3 }
            if (allowed.isNotEmpty() || ask.isNotEmpty()) {
                val globalPermits = if (policy.permission) currentMode != 2 else enabled || policy == Policy.IMAGES && flags and 32 != 0
                add(SettingsHeadingRow(getString(if (!globalPermits) R.string.exception else when (policy) {
                    Policy.DESKTOP, Policy.ADBLOCK, Policy.QUICK_BACK -> R.string.on
                    else -> R.string.allowed
                })))
                ask.forEach { add(ExceptionRow(it.domain, getString(R.string.ask_first))) }
                allowed.forEach { add(ExceptionRow(it.domain)) }
            }
        })
    }

    private fun summary(): String? = when (policy) {
        Policy.DESKTOP, Policy.ADBLOCK, Policy.QUICK_BACK -> null
        Policy.IMAGES -> getString(if (flags and 32 != 0) R.string.images_allowed_via_wifi_description else if (enabled()) R.string.images_allowed_description else R.string.blocked)
        Policy.JAVASCRIPT -> getString(if (enabled()) R.string.javascript_allowed_description else R.string.blocked)
        Policy.COOKIES -> getString(if (enabled()) R.string.cookies_allowed_description else R.string.blocked)
        Policy.POPUPS -> getString(if (enabled()) R.string.allowed else R.string.popups_ask_first_description)
        Policy.VIBRATE -> getString(if (enabled()) R.string.vibrate_allowed_description else R.string.disabled)
        Policy.REDIRECTION -> getString(if (enabled()) R.string.page_redirection_allowed_description else R.string.page_redirection_ask_first_description)
        Policy.CLIPBOARD -> getString(when (mode()) { 1 -> R.string.clipboard_allowed_description; 2 -> R.string.clipboard_blocked_description; else -> R.string.clipboard_ask_first_description })
        Policy.OPEN_APP -> getString(when (mode()) { 1 -> R.string.open_app_allowed_description; 2 -> R.string.open_app_blocked_description; else -> R.string.open_app_ask_first_description })
        Policy.LOCATION -> getString(if (mode() == 3) R.string.location_ask_first_description else R.string.blocked)
        Policy.CAMERA -> getString(if (mode() == 3) R.string.camera_ask_first_description else R.string.disabled)
        Policy.MICROPHONE -> getString(if (mode() == 3) R.string.microphone_ask_first_description else R.string.disabled)
    }

    private fun click(row: SettingsRow) {
        when (row) {
            is ExceptionRow -> editException(row.domain)
            is AddSiteExceptionRow -> addException()
            else -> when {
                policy == Policy.COOKIES && row.id == 2 -> { preferences.webFlags = flags xor 16384; changed() }
                policy == Policy.IMAGES -> ViaDialog(requireActivity()).title(policy.title).singleChoice(
                    arrayOf(getString(R.string.image_on), getString(R.string.image_off), getString(R.string.image_auto)),
                    if (flags and 32 != 0) 2 else if (enabled()) 0 else 1
                ) { selected ->
                    preferences.webFlags = (flags and 48.inv()) or (if (selected == 1) 0 else 16) or (if (selected == 2) 32 else 0)
                    changed()
                }.show()
                policy == Policy.CLIPBOARD || policy == Policy.OPEN_APP -> ViaDialog(requireActivity()).title(policy.title)
                    .singleChoice(permissionChoices(), mode() - 1) { selected ->
                        val decision = if (policy == Policy.CLIPBOARD) 131072 else 33554432
                        val ask = if (policy == Policy.CLIPBOARD) 262144 else 67108864
                        preferences.webFlags = flags and (decision or ask).inv() or when (selected) { 1 -> decision; 2 -> ask; else -> 0 }
                        changed()
                    }.show()
                else -> { setEnabled(!enabled()); changed() }
            }
        }
    }

    private fun exceptionMode(site: SiteConfiguration): Int = if (policy.permission) site.permissionMode(policy.siteBit, policy.askBit)
        else site.booleanOverride(policy.siteBit)?.let { if (it xor policy.inverted) 1 else 2 } ?: 0

    private fun put(domain: String, mode: Int) {
        val previous = sites.get(domain) ?: SiteConfiguration(domain, "{\"flags\":0}")
        // jb.u/v2/g3.s deliberately calls ba.a.O(0), also confirmed in smali.
        // Keep the original transient list removal and persisted clipboard reset.
        val originalClipboardReset = mode == 0 && policy in listOf(Policy.CAMERA, Policy.MICROPHONE, Policy.OPEN_APP)
        val updated = if (originalClipboardReset) previous.withPermission(64, 128, 0)
            else if (policy.permission) previous.withPermission(policy.siteBit, policy.askBit, mode)
            else previous.withBoolean(policy.siteBit, if (mode == 0) null else (mode == 1) xor policy.inverted)
        val result = if (mode == 0) updated else updated.withEnabled(true)
        if (result.isEmpty) sites.remove(domain) else sites.put(result)
        if (mode == 0) exceptionSites.remove(domain) else exceptionSites[domain] = result
        changed(global = false)
    }

    private fun addException() {
        val blocked = if (policy.permission) mode() == 2 else !enabled()
        val message = when (policy) {
            Policy.DESKTOP -> if (blocked) R.string.desktop_mode_add_site_exception_on else R.string.desktop_mode_add_site_exception_off
            Policy.IMAGES -> if (blocked) R.string.images_add_site_exception_allow else R.string.images_add_site_exception_block
            Policy.JAVASCRIPT -> if (blocked) R.string.javascript_add_site_exception_allow else R.string.javascript_add_site_exception_block
            Policy.ADBLOCK -> if (blocked) R.string.adblock_add_site_exception_on else R.string.adblock_add_site_exception_off
            Policy.QUICK_BACK -> if (blocked) R.string.quick_back_add_site_exception_on else R.string.quick_back_add_site_exception_off
            Policy.REDIRECTION -> if (blocked) R.string.page_redirection_add_site_exception_allow else R.string.page_redirection_add_site_exception_ask_first
            Policy.CLIPBOARD -> if (blocked) R.string.clipboard_add_site_exception_allow else R.string.clipboard_add_site_exception_block
            Policy.OPEN_APP -> if (blocked) R.string.open_app_add_site_exception_allow else R.string.open_app_add_site_exception_block
            Policy.LOCATION -> if (blocked) R.string.location_add_site_exception_allow else R.string.location_add_site_exception_block
            Policy.CAMERA -> if (blocked) R.string.camera_add_site_exception_allow else R.string.camera_add_site_exception_block
            Policy.MICROPHONE -> if (blocked) R.string.microphone_add_site_exception_allow else R.string.microphone_add_site_exception_block
            else -> return
        }
        ViaDialog(requireActivity()).title(R.string.add_site_exception).message(message).input("", "www.example.com", 1)
            .apply { if (blocked && policy.permission) check(R.string.ask_first, false) }
            .positive(android.R.string.ok) { _, result ->
                // ba.c.f then kb.c.a: encode the entered host, trim and lowercase; do not extract a URL host.
                val domain = encodeException(result.edit?.firstOrNull().orEmpty())
                if (domain.isNotEmpty()) put(domain, if (blocked) { if (policy.permission && result.checked) 3 else 1 } else 2)
            }.negative(android.R.string.cancel).show()
    }

    private fun editException(domain: String) {
        val site = exceptionSites[domain] ?: return
        val choices = when {
            policy.permission -> permissionChoices()
            policy == Policy.DESKTOP || policy == Policy.ADBLOCK || policy == Policy.QUICK_BACK -> arrayOf(getString(R.string.on), getString(R.string.off))
            policy == Policy.REDIRECTION -> arrayOf(getString(R.string.allow), getString(R.string.page_redirection_ask_first_description_short))
            else -> arrayOf(getString(R.string.allow), getString(R.string.block))
        }
        ViaDialog(requireActivity()).title(domainLabel(domain))
            .singleChoice(choices, exceptionMode(site) - 1) { selected -> put(domain, selected + 1) }
            .positive(android.R.string.ok).neutral(R.string.action_delete) { put(domain, 0) }.show()
    }
    private fun domainLabel(domain: String): String {
        val decoded = runCatching { java.net.IDN.toUnicode(domain) }.getOrDefault(domain)
        return if (decoded.isNotEmpty() && decoded != domain) "$domain ($decoded)" else domain
    }
    private fun permissionChoices() = arrayOf(getString(R.string.allow), getString(R.string.block), getString(R.string.ask_first))

    override fun onDestroyView() { if (::database.isInitialized) database.close(); super.onDestroyView() }
    private class ExceptionRow(val domain: String, summary: String? = null) : SettingsRow(domain.hashCode(), domain, summary)

    private enum class Policy(val id: Int, val title: Int, val globalBit: Int, val siteBit: Int = 0,
        val askBit: Int = 0, val inverted: Boolean = false, val permission: Boolean = false) {
        IMAGES(2, R.string.images, 16, 4),
        CLIPBOARD(4, R.string.clipboard, 0, 64, 128, permission = true),
        LOCATION(5, R.string.location1, 2, 65536, 32768, permission = true),
        CAMERA(6, R.string.camera, 8388608, 8192, 16384, permission = true),
        MICROPHONE(7, R.string.microphone, 16777216, 2048, 4096, permission = true),
        JAVASCRIPT(8, R.string.javascript, 8, 2),
        COOKIES(9, R.string.cookies, 8192),
        POPUPS(10, R.string.popups, 32768, inverted = true),
        OPEN_APP(11, R.string.open_app, 0, 256, 512, permission = true),
        ADBLOCK(12, R.string.block_ads, 1, 16),
        REDIRECTION(13, R.string.page_redirection, 134217728, 1024, inverted = true),
        VIBRATE(15, R.string.vibrate, 1073741824),
        QUICK_BACK(16, R.string.quick_back, 512, 131072),
        DESKTOP(17, R.string.action_pcview, 2048, 8),
    }
    companion object {
        fun create(id: Int) = SitePolicySettingsFragment().apply { arguments = Bundle().apply { putInt("policy", id) } }
        private fun encodeException(value: String): String {
            return UrlPunycode.encodeHost(value).trim().lowercase(Locale.ROOT)
        }
    }
}
