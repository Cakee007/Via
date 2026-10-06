package dev.ujhhgtg.via.browser

import android.content.Context
import dev.ujhhgtg.via.R
import org.json.JSONObject

/** Literal JavaScript bodies from i6.r and c8.rc/ya/za/a/ab/mb, kept as app assets. */
internal class DocumentScripts(private val context: Context) {
    private val values = HashMap<String, String>()
    fun source(name: String): String = values.getOrPut(name) {
        context.assets.open("browser/injection/$name.js").bufferedReader().use { it.readText() }
    }
    fun bootstrap(secret: String, viewId: Int) = source("bootstrap")
        .replace("__SECRET__", JSONObject.quote(secret)).replace("__VIEW__", viewId.toString())
    fun desktopViewport() = source("viewport-desktop").replace("__WIDTH__", "1280")
    fun clipboard(mode: Int) = when (mode) {
        2 -> source("clipboard-block")
        3 -> source("clipboard-ask").replace("%CONFIRM_MESSAGE%", context.getString(R.string.ask_to_allow_webpage_to_copy_text))
        else -> source("clipboard-patch")
    }
    fun downloadLinks(secret: String) = source("download-links").replace("\"__SECRET__\"", JSONObject.quote(secret))
    fun passwordCapture(secret: String) = context.assets.open("passwords/capture.js").bufferedReader().use { it.readText() }
        .replace("\"__SECRET__\"", JSONObject.quote(secret))
    fun marker(secret: String) = context.assets.open("tools/ad-marker.js").bufferedReader().use { it.readText() }
        .replace("\"__VIA_MARKER_SECRET__\"", JSONObject.quote(secret))
    fun font(name: String) = source("font").replace("__PATH__", "/$name")
    fun fontUri(uri: String) = source("font").replace("__PATH__", uri)
    fun blockerLink(host: String) = "(function(){if(!document.getElementById('via_inject_css_blocker')){var css=document.createElement('link');css.id='via_inject_css_blocker';css.type='text/css';css.rel=\"stylesheet\";css.href='https://$host/via_inject_blocker.css';var o=document.getElementsByTagName('head');if(o.length>0&&o[0].appendChild(css)){}}})();"
    fun blockerStyle(css: String?): String {
        if (css.isNullOrEmpty()) return ""
        return "javascript:(function(){function updateStyle(style){var css=document.getElementById('__via_blocker_css__');if(css){css.innerText+=style;return}css=document.createElement('style');css.type='text/css';css.charset='UTF-8';css.id='__via_blocker_css__';css.appendChild(document.createTextNode(style));document.head.appendChild(css)}updateStyle(" +
            jsString(css) + ")})();"
    }

    companion object {
        /**
         * A double-quoted JavaScript string literal for [value]. m8.b.a escaped only quotes and
         * backslash-digit pairs, so CSS with line breaks became an unterminated literal.
         */
        fun jsString(value: String): String = buildString(value.length + 16) {
            append('"')
            for (character in value) when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\u2028' -> append("\\u2028")
                '\u2029' -> append("\\u2029")
                else -> append(character)
            }
            append('"')
        }
    }
}

/** r9.k's bundled defaults; the excluded Firebase remote configuration is not consulted. */
internal object DocumentPolicy {
    fun authority(url: String): String {
        val separator = url.indexOf("://")
        if (separator < 0) return ""
        return url.substring(separator + 3).substringBefore('/')
    }
    fun host(url: String) = authority(url).substringBefore(':')
    /** i6.i0.b/f/o: original base-domain comparison for page redirection. */
    fun navigationDomain(url: String): String {
        val host = host(url)
        if (host.isEmpty() || host.startsWith('[') || ':' in host ||
            host.count { it == '.' } == 3 && host.all { it in '0'..'9' || it == '.' }) return host
        val last = host.lastIndexOf('.')
        if (last < 0) return host
        val previous = host.lastIndexOf('.', last - 1)
        if (previous < 0) return host
        val suffix = host.substring(last + 1)
        if (suffix.lowercase(java.util.Locale.ROOT) in listOf("com", "net", "org", "gov", "co", "edu"))
            return host.substring(previous + 1)
        val third = host.lastIndexOf('.', previous - 1)
        return if (third < 0) host else host.substring(third + 1)
    }
}
