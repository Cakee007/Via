package dev.ujhhgtg.via.browser

import android.content.Context
import android.view.View
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.home.HomeDesign
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** u9.c.h and u9.d.c/e/f/g/h/j: original generated log.html / res.html documents. */
class ResourceDocument(private val context: Context, private val preferences: BrowserPreferences = BrowserPreferences(context)) {
    fun write(entries: List<BrowserResource>, mediaOnly: Boolean, dark: Boolean): String {
        val file = File(context.filesDir, if (mediaOnly) "res.html" else "log.html")
        file.bufferedWriter().use { it.write(html(entries, mediaOnly, dark)) }
        return "file://${file.path}"
    }

    /** Input retains d8.c insertion order. The 64-entry display window precedes media filtering. */
    fun html(entries: List<BrowserResource>, mediaOnly: Boolean, dark: Boolean): String {
        val clock = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val displayed = entries.takeLast(64).asReversed().filter { !mediaOnly || it.isMedia }
        val title = context.getString(if (mediaOnly) R.string.resource_sniffer else R.string.network_log)
        val hint = when {
            displayed.isNotEmpty() && mediaOnly -> context.getString(R.string.res_log_hint)
            displayed.isNotEmpty() -> context.getString(R.string.log_page_hint, customFilterFile(context).path)
            mediaOnly -> context.getString(R.string.no_resource) + context.getString(R.string.resource_sniffer_hint).replace("\n", "<br>")
            else -> context.getString(R.string.empty_hint)
        }
        val style = css(dark, preferences.backgroundInfo, preferences.urlBarColor,
            context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL)
        return buildString {
            append("<!DOCTYPE html><html><head><meta content=\"text/html; charset=utf-8\" http-equiv=\"Content-Type\"/><meta name=\"color-scheme\" content=\"light dark\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1, user-scalable=no, minimal-ui\">")
            append("<title>").append(title).append("</title></head>")
            append("<style>").append(style).append("</style>")
            append("<body><div class='frosted-glass' id='gesture-indicator'></div><div id=\"content\">")
            append("<div class='hint'>").append(hint.replace("\n", "<br>")).append("</div>")
            if (!mediaOnly && displayed.isNotEmpty()) append(FILTER_SELECTOR)
            displayed.forEach { append(row(it, clock.format(Date(it.timestamp)))) }
            append("<script type=\"text/javascript\">")
            append(context.assets.open("browser/resources/bold-domain.js").bufferedReader().use { it.readText() })
            if (!mediaOnly) append(context.assets.open("browser/resources/filter.js").bufferedReader().use { it.readText() })
            append("</script></div></body></html>")
        }
    }

    companion object {
        /** u9.d.d/m recognizes generated pages, not arbitrary user-selected file URLs. */
        fun isInternalPage(context: Context, url: String): Boolean {
            val prefix = "file://${context.filesDir.path}/"
            return url.startsWith(prefix) && url.substring(prefix.length).substringBefore('?').substringBefore('#') in setOf(
                "homepage.html", "homepage2.html", "bookmarks.html", "folder.html", "history.html", "catalog.html",
                "log.html", "res.html", "save.html", "about.html", "blank.html", "images.html")
        }

        /** r9.k's bundled wlr excludes media collection and both sniffing entry points. */
        fun customFilterFile(context: Context): File = File(
            (context.getExternalFilesDir("filters") ?: File(context.filesDir, "filters")).apply { mkdirs() }, "custom.txt")

        private const val FILTER_SELECTOR = "<div id=\"filter-box\"><select id=\"filter\" onchange=\"filterChange()\"><option value=\"\">All</option><option value=\"js\">JS</option><option value=\"css\">CSS</option><option value=\"ico|png|jpg|gif|jpeg|webp|svg|avif\">Image</option><option value=\"block\">Blocked</option><option value=\"~js|css|ico|png|jpg|gif|jpeg|webp|svg|avif|block\">Other</option></select></div>"

        /** u9.d.c does not HTML-encode these strings; links themselves handle ordinary navigation. */
        internal fun row(entry: BrowserResource, time: String): String = buildString {
            append("<div class=\"box").append(if (entry.blocked) " block" else "")
            append("\"><a href=\"").append(entry.url).append("\" title=\"").append(entry.url)
            append("\"></a><p class=\"title\">").append(time).append("<font class=\"tag\">")
            append(if (entry.blocked) "block" else "load").append("</font>")
            if (entry.extension.isNotEmpty()) append("<font class=\"res tag\">").append(entry.extension).append("</font>")
            append("</p><p class=\"url\">").append(entry.url).append("</p></div>")
        }

        /** The page-type-6 branch of u9.d.g, including background-theme overrides and RTL. */
        internal fun css(dark: Boolean, backgroundBits: Int, color: Int, rtl: Boolean): String {
            val lightBackground = if (backgroundBits and 128 != 0) backgroundBits and 256 != 0 else HomeDesign.isLight(color)
            val lightInk = dark || !lightBackground
            val primary = "color: ${if (lightInk) "#fafafa" else "#1b1b1b"};"
            val secondary = "color: ${if (lightInk) "#d5d5d5" else "#2b2b2b"};"
            val highlight = if (lightInk) "rgba(255, 255, 255, 0.1)" else "rgba(0, 0, 0, 0.1)"
            return buildString {
                append("* {padding:0;margin:0;box-sizing:border-box;}")
                append("html{height:100%;-webkit-tap-highlight-color:").append(highlight)
                append(";-webkit-focus-ring-color: rgba(0, 0, 0, 0);}")
                append("body{min-height:100%;max-width:100%; width: 600px;margin: auto;text-align: center;}")
                if (rtl) append("html{direction:rtl;}")
                append("body{background: transparent;}")
                append(".box {margin: 12px 0; text-align: left; vertical-align:middle;position:relative;display: block;padding-top:10px; padding-bottom:10px; padding-left:10px; padding-right:10px;}")
                append(".box a {width: 100%;height: 100%;position: absolute;left: 0;top: 0;}")
                append("span, .url, .box {word-break: break-all;}")
                append(".block{opacity:0.5;}.tag{background:#cd8282;padding:0 8px;margin:0 4px;color:white;font-size:12px;}.res{background:#5c91cb;}")
                append(".title {").append(secondary).append("font-size: 15px; padding: 4px 0px;}")
                append(".url {line-height: 1.2em; max-height: 4.8em;font-size: 15px;").append(primary)
                append(" white-space: normal; word-wrap: break-word; overflow: auto;text-overflow: ellipsis;}")
                append(".hint {line-height: 1.8em; ").append(secondary)
                append("font-size: 15px; white-space: normal; word-wrap: break-word; overflow: auto;text-overflow: ellipsis; padding: 50px 5px; text-align: center; margin: auto;}")
                append("#filter-box{padding:10px}#filter{border: 1px solid ").append(highlight)
                append(";border-radius:2px;-webkit-appearance:none;-moz-appearance:none;appearance:none;background:transparent;padding:5px;width:100%;font-size: 15px;")
                append(primary).append("}")
                if (rtl) append(".box{text-align:right;}")
            }
        }
    }
}
