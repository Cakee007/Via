package dev.ujhhgtg.via.extensions

import android.content.Context
import dev.ujhhgtg.via.R

/** Readable permission and data-collection lines, worded as Firefox words them. */
object ExtensionPermissions {
    internal val permissions = mapOf(
        "bookmarks" to R.string.extension_permission_bookmarks,
        "browserSettings" to R.string.extension_permission_browser_settings,
        "browsingData" to R.string.extension_permission_browsing_data,
        "clipboardRead" to R.string.extension_permission_clipboard_read,
        "clipboardWrite" to R.string.extension_permission_clipboard_write,
        "declarativeNetRequest" to R.string.extension_permission_declarative_net_request,
        "declarativeNetRequestFeedback" to R.string.extension_permission_declarative_net_request_feedback,
        "devtools" to R.string.extension_permission_devtools,
        "downloads" to R.string.extension_permission_downloads,
        "downloads.open" to R.string.extension_permission_downloads_open,
        "find" to R.string.extension_permission_find,
        "geolocation" to R.string.extension_permission_geolocation,
        "history" to R.string.extension_permission_history,
        "management" to R.string.extension_permission_management,
        "nativeMessaging" to R.string.extension_permission_native_messaging,
        "notifications" to R.string.extension_permission_notifications,
        "pkcs11" to R.string.extension_permission_pkcs11,
        "privacy" to R.string.extension_permission_privacy,
        "proxy" to R.string.extension_permission_proxy,
        "sessions" to R.string.extension_permission_sessions,
        "tabs" to R.string.extension_permission_tabs,
        "tabHide" to R.string.extension_permission_tab_hide,
        "topSites" to R.string.extension_permission_top_sites,
        "userScripts" to R.string.extension_permission_user_scripts,
        "webNavigation" to R.string.extension_permission_web_navigation,
    )
    internal val dataCollection = mapOf(
        "authenticationInfo" to R.string.extension_data_authentication,
        "bookmarksInfo" to R.string.extension_data_bookmarks,
        "browsingActivity" to R.string.extension_data_browsing_activity,
        "financialAndPaymentInfo" to R.string.extension_data_financial,
        "healthInfo" to R.string.extension_data_health,
        "locationInfo" to R.string.extension_data_location,
        "personalCommunications" to R.string.extension_data_communications,
        "personallyIdentifyingInfo" to R.string.extension_data_personal,
        "searchTerms" to R.string.extension_data_search_terms,
        "technicalAndInteraction" to R.string.extension_data_technical,
        "websiteActivity" to R.string.extension_data_website_activity,
        "websiteContent" to R.string.extension_data_website_content,
    )
    private val allSites = setOf("<all_urls>", "*://*/*", "http://*/*", "https://*/*")

    /** One line per permission and site. Permissions without a Firefox description are not shown, as in Firefox. */
    fun lines(context: Context, permissions: List<String>, origins: List<String>): List<String> = buildList {
        permissions.forEach { name -> this@ExtensionPermissions.permissions[name]?.let { add(context.getString(it)) } }
        val hosts = origins.map(::host)
        if (hosts.any { it == null }) add(context.getString(R.string.extension_all_sites))
        else hosts.filterNotNull().distinct().forEach { add(context.getString(R.string.extension_site_access, it)) }
    }.distinct()

    fun dataLines(context: Context, names: List<String>): List<String> =
        names.filter { it != "none" }.map { name -> dataCollection[name]?.let(context::getString) ?: name }

    /** The site of a match pattern, or null when it covers every site. */
    internal fun host(origin: String): String? {
        if (origin in allSites) return null
        val host = origin.substringAfter("://", origin).substringBefore('/').substringBefore(':')
        if (host.isEmpty() || host == "*") return null
        return host.removePrefix("*.")
    }
}
