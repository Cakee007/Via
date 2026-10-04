package dev.ujhhgtg.via.browser

import android.content.Intent
import android.net.Uri
import android.os.Build
import dev.ujhhgtg.via.common.ViaIntents

/** z8.b0.A/H/L and c8.ua.l: extraction precedes URL detection, sharing and search. */
class BrowserIntentRouter(private val searchTemplate: () -> String) {
    enum class Kind { PAGE, COMMAND, MULTIPLE_URLS, EXTERNAL }

    data class Route(
        val target: String,
        val kind: Kind = Kind.PAGE,
        val sourceAction: String? = null,
        val fromShare: Boolean = false,
        val originalText: String = target,
        val candidates: List<String> = emptyList(),
        val returnToCaller: Boolean = false,
    )

    fun resolve(intent: Intent?, referrer: Uri? = null, ownPackage: String = ViaIntents.PACKAGE): Route? {
        if (intent == null) return null
        val raw = extract(intent) ?: return null
        val template = searchTemplate()
        val parsed = UrlParser(raw)
        val urls = if (!parsed.isValid && raw.isNotEmpty()) UrlResolver.extractUrls(raw) else emptyList()
        val shared = intent.action.equals(Intent.ACTION_SEND, true) || intent.hasExtra(Intent.EXTRA_PROCESS_TEXT)
        if (urls.size > 1) return Route(raw, Kind.MULTIPLE_URLS, intent.action, shared, raw, urls)
        val target = when {
            parsed.isValid && raw.trim().startsWith("javascript:", true) -> UrlResolver.search(template, raw)
            parsed.isValid -> UrlResolver.normalizeInput(raw, template)
            urls.size == 1 -> urls[0]
            else -> UrlResolver.normalizeInput(raw, template)
        }?.takeIf(String::isNotEmpty) ?: return null
        val command = if (target.startsWith("via://", true)) "v://" + target.substring(6) else target
        if (command.startsWith("v://", true)) return Route(command, Kind.COMMAND, intent.action, shared, raw)
        // A pass-through search template can leave JavaScript unchanged; ua.l discards it here.
        if (target.startsWith("javascript:", true)) return null
        if (!UrlResolver.isLoadable(target)) return Route(target, Kind.EXTERNAL, intent.action, shared, raw)
        val caller = suppliedReferrer(intent) ?: referrer
        val external = caller?.host != ownPackage && shouldOpenExternally(intent)
        return Route(target, Kind.PAGE, intent.action, shared, raw, returnToCaller = external)
    }

    /** b0.A(intent, null): present empty extras do not fall through to lower-priority fields. */
    private fun extract(intent: Intent): String? {
        val action = intent.action
        val shortcut = when {
            action.equals(ViaIntents.ACTION_BOOKMARK, true) -> UrlResolver.BOOKMARKS
            action.equals(ViaIntents.ACTION_HISTORY, true) -> UrlResolver.HISTORY
            action.equals(ViaIntents.ACTION_SCAN, true) -> UrlResolver.SCANNER
            action.equals(ViaIntents.ACTION_DOWNLOADER, true) -> UrlResolver.DOWNLOADER
            action.equals(ViaIntents.ACTION_READ_ALOUD, true) -> UrlResolver.READ_ALOUD
            action.equals(ViaIntents.ACTION_SEARCH, true) -> UrlResolver.SEARCH
            else -> null
        }
        if (shortcut != null) return shortcut
        if (action.equals(Intent.ACTION_SEND, true)) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf(String::isNotEmpty)?.let {
                // Passing no search template leaves prose intact so ua.l can extract its links.
                return UrlResolver.normalizeInput(it, null)
            }
        }
        if (intent.hasExtra(ViaIntents.EXTRA_OPEN)) return intent.getStringExtra(ViaIntents.EXTRA_OPEN)
        if (intent.hasExtra(ViaIntents.EXTRA_QUERY)) return intent.getStringExtra(ViaIntents.EXTRA_QUERY)
        if (intent.hasExtra(Intent.EXTRA_PROCESS_TEXT)) {
            val selected = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
            return if (action == ViaIntents.ACTION_TRANSLATE) {
                selected?.let(UrlResolver::translate)
            } else selected
        }
        return intent.dataString
    }

    /** b0.H decides whether s6.a0 pops the Shell back stack, independently of the route kind. */
    fun belongsToBrowser(intent: Intent?): Boolean {
        if (intent == null) return false
        if (isBrowserMarker(intent.getStringExtra(ViaIntents.EXTRA_INTENT))) return true
        val action = intent.action
        return action == Intent.ACTION_SEND && intent.hasExtra(Intent.EXTRA_TEXT)
                || isViaPrefix(action)
                || intent.hasExtra(ViaIntents.EXTRA_OPEN)
                || intent.hasExtra(ViaIntents.EXTRA_QUERY)
                || intent.hasExtra(Intent.EXTRA_PROCESS_TEXT)
                || intent.dataString != null
    }

    /** b0.L classifies a launch for returning to its caller; it does not launch another app. */
    fun shouldOpenExternally(intent: Intent?): Boolean {
        if (intent == null) return true
        val action = intent.action
        var browserRequest = isViaPrefix(action) || action.equals(Intent.ACTION_WEB_SEARCH, true) ||
            // The original literal is EXTRA_PROCESS_TEXT, not ACTION_PROCESS_TEXT.
            action.equals(Intent.EXTRA_PROCESS_TEXT, true)
        if (intent.hasExtra(ViaIntents.EXTRA_INTENT)) browserRequest = isBrowserMarker(intent.getStringExtra(ViaIntents.EXTRA_INTENT))
        return !browserRequest
    }

    private fun suppliedReferrer(intent: Intent): Uri? = runCatching {
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(ViaIntents.EXTRA_REFERER, Uri::class.java)
        else @Suppress("DEPRECATION") (intent.getParcelableExtra(ViaIntents.EXTRA_REFERER))
    }.getOrNull()

    private fun isBrowserMarker(value: String?) = value.equals(ViaIntents.MARKER_BROWSER, true)
    private fun isViaPrefix(action: String?) = action?.startsWith(ViaIntents.PREFIX) == true
}
