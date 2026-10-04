package dev.ujhhgtg.via.sites

import android.os.Bundle
import android.view.View
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.browser.UrlPunycode
import dev.ujhhgtg.via.browser.UserAgentPolicy
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.SettingsData
import dev.ujhhgtg.via.data.SettingsDataRepository
import dev.ujhhgtg.via.data.SiteConfiguration
import dev.ujhhgtg.via.data.SiteConfigurationRepository
import dev.ujhhgtg.via.settings.SettingsHeadingRow
import dev.ujhhgtg.via.settings.SettingsListFragment
import dev.ujhhgtg.via.settings.SettingsRow
import dev.ujhhgtg.via.settings.SettingsToolbar
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import java.util.Locale

/** jb.v5/w5: the website UA policy page precedes hb.w7's global UA chooser. */
class SiteUserAgentSettingsFragment : SettingsListFragment() {
    private lateinit var database: BrowserDatabase
    private lateinit var sites: SiteConfigurationRepository
    private lateinit var preferences: BrowserPreferences
    private lateinit var rows: SiteSettingsRowsAdapter
    private var agents = emptyList<SettingsData>()
    private val exceptionSites = mutableMapOf<String, SiteConfiguration>()
    override fun configureToolbar(toolbar: SettingsToolbar) = toolbar.setTitle(R.string.agent)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        database = BrowserDatabase(requireContext()); sites = SiteConfigurationRepository(database)
        preferences = BrowserPreferences(requireContext())
        rows = SiteSettingsRowsAdapter { row ->
            when (row) {
                is AddSiteExceptionRow -> addException()
                is AgentExceptionRow -> choose(row.domain)
                else -> (requireActivity() as Shell).openPage("agent")
            }
        }
        list.adapter = rows
        bindRows(reload = true)
    }
    override fun onHiddenChanged(hidden: Boolean) { super.onHiddenChanged(hidden); if (!hidden && ::rows.isInitialized) bindRows(reload = true) }
    private fun bindRows(reload: Boolean = false) {
        // jb.v5.r3/w5.t reload on entry/return from the global UA chooser;
        // w5.l/u otherwise update their existing list rather than reading it back.
        if (reload) {
            val labels = intArrayOf(R.string.default_set, R.string.agent_android_phone, R.string.agent_android_tablet,
                R.string.agent_windows_chrome, R.string.agent_windows_ie11, R.string.agent_osx, R.string.agent_iphone, R.string.agent_ipad, R.string.agent_symbian)
            agents = labels.mapIndexed { index, label -> SettingsData(id = -index, title = getString(label), type = SettingsData.USER_AGENT,
                content = if (index == 0) null else UserAgentPolicy.resolve(-index, null, null, false, 0, null, false)) } + SettingsDataRepository(database).list(SettingsData.USER_AGENT)
            exceptionSites.clear()
            sites.all().forEach { stored ->
                sites.get(stored.domain)?.takeIf { it.isEnabled && it.userAgentChoice != -1000 }?.let { exceptionSites[it.domain] = it }
            }
        }
        val exceptions = exceptionSites.values.sortedBy { it.domain }.mapNotNull { site ->
            if (site.userAgentChoice == -999) AgentExceptionRow(site.domain, site.customUserAgent)
            else agents.firstOrNull { it.id == site.userAgentChoice }?.let { AgentExceptionRow(site.domain, it.title) }
        }
        rows.submit(buildList {
            add(SettingsRow(1, getString(R.string.agent), agents.firstOrNull { it.id == preferences.userAgentChoice }?.title))
            add(AddSiteExceptionRow(getString(R.string.add_site_exception)))
            if (exceptions.isNotEmpty()) { add(SettingsHeadingRow(getString(R.string.exception))); addAll(exceptions) }
        })
    }
    private fun addException() {
        ViaDialog(requireActivity()).title(R.string.add_site_exception).message(R.string.agent_add_site_exception)
            .input("", "www.example.com", 1).positive(android.R.string.ok) { _, result ->
                val domain = UrlPunycode.encodeHost(result.edit?.firstOrNull().orEmpty())
                if (domain.isNotEmpty()) choose(domain)
            }.negative(android.R.string.cancel).show()
    }
    private fun choose(domain: String) {
        val key = domain.trim().lowercase(Locale.ROOT)
        val site = sites.get(key)
        val selected = site?.userAgentChoice?.takeUnless { it == -1000 } ?: preferences.userAgentChoice
        val choices = agents + SettingsData(id = -999, title = getString(R.string.agent_custom), type = SettingsData.USER_AGENT)
        val decoded = runCatching { java.net.IDN.toUnicode(domain) }.getOrDefault(domain)
        val label = if (decoded != domain && decoded.isNotEmpty()) "$domain ($decoded)" else domain
        ViaDialog(requireActivity()).title(label).singleChoice(choices.map { it.title.orEmpty() }.toTypedArray(), choices.indexOfFirst { it.id == selected }) { index ->
            val agent = choices[index]
            if (agent.id == -999) editCustom(key) else save(key, agent.id, agent.content)
        }.positive(android.R.string.ok).neutral(R.string.action_delete) {
            if (site != null) {
                val updated = site.withUserAgent(-1000)
                if (updated.isEmpty) sites.remove(key) else sites.put(updated)
                exceptionSites.remove(key)
                changed()
            }
        }.show()
    }
    private fun editCustom(domain: String) {
        ViaDialog(requireActivity()).title(R.string.agent_custom).canceledOnTouchOutside(false)
            .input(sites.get(domain)?.customUserAgent, getString(R.string.agent), 3)
            .positive(android.R.string.ok) { _, result ->
                val content = result.edit?.firstOrNull().orEmpty()
                if (content.isNotEmpty()) save(domain, -999, content)
            }.negative(android.R.string.cancel).show()
    }
    private fun save(domain: String, choice: Int, content: String?) {
        val site = sites.get(domain) ?: SiteConfiguration(domain, null)
        val updated = site.withEnabled(true).withUserAgent(choice, content)
        sites.put(updated)
        exceptionSites[domain] = updated
        changed()
    }
    // jb.w5.l/u update the exception repository and this page's list only.
    private fun changed() { bindRows() }
    override fun onDestroyView() { if (::database.isInitialized) database.close(); super.onDestroyView() }
    private class AgentExceptionRow(val domain: String, summary: String?) : SettingsRow(domain.hashCode(), domain, summary)
}
