package dev.ujhhgtg.via.ui

import android.app.Activity
import dev.ujhhgtg.via.ui.ViaToast
import androidx.core.net.toUri
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.browser.UserAgentPolicy
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.SettingsData
import dev.ujhhgtg.via.data.SettingsDataRepository
import dev.ujhhgtg.via.settings.ExternalDownloadManagers
import dev.ujhhgtg.via.ui.dialog.ViaDialog

/** c8.s6.Pa/O3 and D9/y4 quick choosers; these are distinct from the settings fragments. */
object BrowserMenuChoices {
    /** C9(17)/s5: allow, block, or Wi-Fi-only; update policy without reloading the document. */
    fun images(activity: Activity, changed: () -> Unit) {
        val preferences = BrowserPreferences(activity)
        val selected = if (preferences.webFlags and 32 != 0) 2 else if (preferences.imagesEnabled()) 0 else 1
        val choices = arrayOf(activity.getString(R.string.image_on), activity.getString(R.string.image_off), activity.getString(R.string.image_auto))
        ViaDialog(activity).title(R.string.images).singleChoice(choices, selected) { index ->
            if (index != selected) {
                preferences.webFlags = (preferences.webFlags and 48.inv()) or (if (index == 1) 0 else 16) or (if (index == 2) 32 else 0)
                changed()
                ViaToast.makeText(activity, choices[index], ViaToast.LENGTH_SHORT).show()
            }
        }.show()
    }
    fun userAgent(activity: Activity, pageUrl: String?, changed: (reload: Boolean) -> Unit) {
        val preferences = BrowserPreferences(activity)
        val desktop = preferences.webFlags and 2048 != 0
        val labels = intArrayOf(R.string.default_set, R.string.agent_android_phone, R.string.agent_android_tablet,
            R.string.agent_windows_chrome, R.string.agent_windows_ie11, R.string.agent_osx, R.string.agent_iphone, R.string.agent_ipad, R.string.agent_symbian)
        val choices = labels.mapIndexed { index, resource -> SettingsData(id = -index, title = activity.getString(resource),
            content = UserAgentPolicy.resolve(-index, null, null, false, 0, null, false), type = SettingsData.USER_AGENT) }.toMutableList()
        BrowserDatabase(activity).use { choices += SettingsDataRepository(it).list(SettingsData.USER_AGENT) }
        if (desktop) choices.removeAll { it.id < 0 && it.id !in setOf(-3, -4, -5) }
        val selected = if (desktop) preferences.duaChoice else preferences.userAgentChoice
        val index = choices.indexOfFirst { it.id == selected }.coerceAtLeast(0)
        ViaDialog(activity).title(if (desktop) R.string.user_agent_in_desktop_mode else R.string.agent)
            .singleChoice(choices.map { it.title.orEmpty() }.toTypedArray(), index) { which ->
                val entry = choices[which]
                if (entry.id != selected) {
                    val custom = entry.id > 0 || entry.id <= -999
                    if (desktop) { preferences.duaChoice = entry.id; if (custom) preferences.duaString = entry.content }
                    else { preferences.userAgentChoice = entry.id; if (custom) preferences.userAgent = entry.content.orEmpty() }
                    val domain = pageUrl?.toUri()?.host
                    val reload = !domain.isNullOrEmpty() && domain.lowercase() !in setOf("pan.baidu.com", "yun.baidu.com", "eyun.baidu.com")
                    changed(reload)
                    ViaToast.makeText(activity, activity.getString(R.string.key_is_set_as_value, activity.getString(R.string.agent), entry.title.orEmpty()), ViaToast.LENGTH_SHORT).show()
                }
            }.show()
    }

    fun downloadManager(activity: Activity) {
        val preferences = BrowserPreferences(activity)
        val choices = ExternalDownloadManagers.choices(activity)
        val selected = choices.indexOfFirst { it.id == preferences.downloadManager }.coerceAtLeast(0)
        ViaDialog(activity).title(R.string.addon_download).singleChoice(choices.map { it.label }.toTypedArray(), selected) { index ->
            val choice = choices[index]
            preferences.downloadManager = choice.id
            ViaToast.makeText(activity, activity.getString(R.string.key_is_set_as_value, activity.getString(R.string.addon_download), choice.label), ViaToast.LENGTH_SHORT).show()
        }.show()
    }

    /** z8.f1.f excludes this browser from the resolved BROWSABLE intent choices. */
    fun external(activity: Activity, url: String?) {
        dev.ujhhgtg.via.tools.ExternalPageOpener.open(activity, url,
            internalDocument = url != null && dev.ujhhgtg.via.browser.ResourceDocument.isInternalPage(activity, url))
    }
}
