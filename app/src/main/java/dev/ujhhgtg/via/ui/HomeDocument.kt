package dev.ujhhgtg.via.ui

import android.content.Context
import android.net.Uri
import android.view.View
import dev.ujhhgtg.via.engine.EnginePage
import androidx.core.text.htmlEncode
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.browser.UrlResolver
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.Favorite
import dev.ujhhgtg.via.home.HomeDesign
import dev.ujhhgtg.via.home.HomeStyle
import java.io.File
import java.net.URLEncoder

/** Original r8/d and i6/o homepage writer, with the i9 packed design settings. */
class HomeDocument(private val context: Context, private val preferences: BrowserPreferences) {
    fun design(dark: Boolean) = HomeDesign(preferences.logoInfo, preferences.favoritesInfo, preferences.searchInfo,
        preferences.customInfo, preferences.backgroundInfo, preferences.urlBarColor, preferences.backgroundHome,
        dark, context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL, preferences.cssTheme.orEmpty())

    fun write(favorites: List<Favorite>, dark: Boolean, nativeGestureAllowed: Boolean = false): String {
        val config = design(dark)
        val css = File(context.filesDir, "homepage.css").apply { writeText(HomeStyle.css(config)) }
        val logo = preferences.homeTag?.takeIf { it.isNotEmpty() } ?: context.assets.open("home/default-logo.html").bufferedReader().use { it.readText() }
        val title = escape(context.getString(R.string.home))
        val bookmarkTitle = escape(context.getString(R.string.action_bookmarks))
        val searchTitle = escape(context.getString(R.string.search_hint))
        val html = buildString {
            append("<!DOCTYPE html><html><head><meta content=\"text/html; charset=utf-8\" http-equiv=\"Content-Type\"/><meta name=\"color-scheme\" content=\"light dark\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1, user-scalable=no, minimal-ui\"><title>$title</title><link rel=\"stylesheet\" href=\"${css.absolutePath}\"></head>")
            append("<body><div class='frosted-glass' id='gesture-indicator'></div><div id=\"content\"><div class=\"search_part\"><a class=\"logo\" href=\"\" onclick=\"javascript:window.via.cmd(257);\" title=\"$bookmarkTitle\">$logo</a>")
            append("<div class=\"search_bar_wrapper\"><form onsubmit=\"return search()\" class=\"search_bar\" title=\"$searchTitle\"><button onclick=\"search()\" id=\"search_submit\" value=\"\" aria-label=\"$searchTitle\"><div class=\"search icon\"></div></button><span><input class=\"search\" onfocus=\"showButton()\" onblur=\"hideButton()\" type=\"text\" value=\"\" autocomplete=\"off\" id=\"search_input\" title=\"$searchTitle\"></span></form><button type=\"button\" id=\"search_bar_trigger\" aria-label=\"$searchTitle\"></button></div></div>")
            if (favorites.isNotEmpty()) {
                append("<div id=\"bookmark_part\"><div id=\"box_container\">")
                favorites.forEach { append(favorite(it, config)) }
                append("</div></div>")
            }
            append("<script>window.__via_home_gesture_allowed__=$nativeGestureAllowed;window.via=window.via||{cmd:function(c){if(c===515)return window.__via_home_gesture_allowed__?1:0;if(c===257)location.href='v://bookmarks';else if(c===514)location.href='v://search';return 0;},postMessage:function(){}};</script>")
            for (name in listOf("homepage.js", "suggestions.js", "bind-suggestions.js")) {
                append("<script type=\"text/javascript\">")
                // The original generated page resolves its native-call token at runtime. Keep the
                // placeholder as a window property so the per-EnginePage ViaBridge secret is used;
                // replacing it with a literal null silently disabled suggestions and page events.
                append(context.assets.open("home/$name").bufferedReader().use { it.readText() }.replace("__VIA_SECRET__", "window.__VIA_SECRET__"))
                append("</script>")
            }
            append("</div></body></html>")
        }
        val file = File(context.filesDir, "homepage2.html").apply { writeText(html) }
        return Uri.fromFile(file).toString()
    }

    private fun favorite(item: Favorite, config: HomeDesign): String {
        val title = item.title.orEmpty().trim().replace(Regex("<.*?>"), "")
        val first = if (title.isEmpty()) "" else String(Character.toChars(title.codePointAt(0)))
        // i6.o.c via r8.d.h: favorites anchor to the w2.g jump proxy and the
        // browser unwraps it (w2.f) when the element menu opens.
        // w2.g/i6.i0: error-jump is only used for HTTP(S), file:// and data:
        // URLs.  Content providers remain direct targets, while data URLs must
        // go through the browser's unwrapping route as in the original.
        val target = if (UrlResolver.isHttpUrl(item.url) || item.url.startsWith("file://", true) || item.url.startsWith("data:", true)) "v://error/jump?url=" + URLEncoder.encode(item.url, "UTF-8").replace("+", "%20") else item.url
        val color = if (config.favoriteColorDisabled) "" else " style=\"background:${HomeDesign.favoriteColor(item.url)};\""
        val icon = if (config.favoriteIconDisabled) "" else " style=\"background:url('./icon/${escape(HomeDesign.iconName(item.url))}.png') no-repeat;background-size:cover;background-position:center center;\""
        return "<div class=\"box\"><p class=\"title\" aria-hidden=\"true\"$color>${escape(first)}</p><div class=\"overlay\"$icon></div><p class=\"url\" aria-hidden=\"true\">${escape(title)}</p><a href=\"${escape(target)}\" title=\"${escape(title)}\"></a></div>"
    }
    private fun escape(value: String) = value.htmlEncode()

    companion object {
        /** cmd515 is runtime toolbar-shown OR floating-button-shown, not a stored preference. */
        fun updateGestureAvailability(webView: EnginePage, allowed: Boolean) { webView.evaluate("window.__via_home_gesture_allowed__=$allowed;", null) }
    }
}
