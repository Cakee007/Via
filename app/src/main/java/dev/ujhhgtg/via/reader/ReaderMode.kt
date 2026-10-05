package dev.ujhhgtg.via.reader

import android.content.Context
import dev.ujhhgtg.via.engine.EnginePage
import org.json.JSONObject
import java.util.Locale

/** Runs the original i6/w JavaScript; the reader remains an overlay on the current document. */
class ReaderMode(context: Context) {
    private val assets = context.applicationContext.assets
    private val scripts = mutableMapOf<String, String>()
    private fun asset(name: String): String = scripts.getOrPut(name) { assets.open("reader/$name").bufferedReader().use { it.readText() } }

    fun enter(webView: EnginePage, backgroundColor: Int = -1, textSize: Int = 20, customCss: String = "", callback: (Boolean) -> Unit = {}) {
        val url = webView.url.orEmpty()
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) { callback(false); return }
        val setup = asset("readability.js") + "\n" + styleScript("__via_reader__", asset("reader.css")) + "\n" +
            styleScript("__via_reader_custom__", style(backgroundColor, textSize, customCss)) + "\n" + asset("enter.js")
        evaluate(webView, asset("active.js"), setup) { callback(it?.optInt("value") == 3) }
    }

    fun exit(webView: EnginePage, callback: () -> Unit = {}) = evaluate(webView, "true", asset("exit.js")) { callback() }

    /** i6.c0.j/k: injects the source reader stylesheet, then runs w.a.j. */
    fun showFromMenu(webView: EnginePage, callback: (Int) -> Unit) {
        if (!webView.javaScriptEnabled) { callback(1); return }
        if (!dev.ujhhgtg.via.browser.UrlResolver.isHttpUrl(webView.url)) { callback(4); return }
        webView.evaluate(styleScript("__via_reader__", asset("reader.css")), null)
        // i6.e.d always supplies a null error as the callback's second argument;
        // c0.k tests that error, not the script's returned value.
        webView.evaluate(asset("enter.js")) { callback(0) }
    }

    fun applyStyle(webView: EnginePage, backgroundColor: Int, textSize: Int, customCss: String = "") =
        evaluate(webView, "true", styleScript("__via_reader_custom__", style(backgroundColor, textSize, customCss))) {}

    /** i6.c0.q: document-end prepares both source reader libraries before S7 checks the active page. */
    fun prepare(webView: EnginePage, callback: (Boolean) -> Unit) {
        val url = webView.url.orEmpty()
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) { callback(false); return }
        webView.evaluate(asset("readability.js"), null)
        webView.evaluate(asset("readerable.js")) { callback(true) }
    }
    /** i6.c0.g: checks existing reader state without resetting document scripts. */
    fun detectPrepared(webView: EnginePage, callback: (Int) -> Unit) {
        val url = webView.url.orEmpty()
        if (!webView.javaScriptEnabled || !url.startsWith("http://", true) && !url.startsWith("https://", true)) { callback(0); return }
        val path = dev.ujhhgtg.via.browser.UrlParser(url).path.orEmpty()
        val javascript = path.substringAfterLast('/', "").substringAfterLast('.', "").lowercase(Locale.ROOT) == "js"
        val excluded = !javascript && (
            url.contains(".baidu.com/") && url.contains("/s?") || url.contains(".bing.com/") && url.contains("/search?") ||
            url.contains(".sogou.com/") && url.contains("/sl?") || url.contains(".so.com/") && url.contains("/s?") ||
            url.contains(".google.com/") && url.contains("/search?") || url.contains(".metaso.cn/") && url.contains("/search/") ||
            url.contains("://tool.lu/") || url.endsWith(".user.js"))
        webView.evaluate(asset(if (excluded) "active.js" else "detect.js")) { result ->
            callback(result?.removeSurrounding("\"")?.trim()?.toIntOrNull() ?: 0)
        }
    }

    fun extractSentences(webView: EnginePage, callback: (List<String>) -> Unit) = evaluate(webView, asset("extract-sentences.js")) {
        callback(ReaderSentences.parse(it?.optString("value").orEmpty()))
    }

    private fun evaluate(webView: EnginePage, expression: String, setup: String = "", callback: (JSONObject?) -> Unit) {
        val js = "(function(){try{$setup\n;return {ok:true,value:(${expression.trim().removeSuffix(";")})};}catch(e){return {ok:false};}})();"
        webView.evaluate(js) { result ->
            val value = runCatching { JSONObject(result) }.getOrNull()
            callback(value?.takeIf { it.optBoolean("ok") })
        }
    }

    private fun styleScript(id: String, css: String): String = """
        (function(){var e=${JSONObject.quote(css)},t=document.getElementById('$id');
        if(t)t.innerText=e;else{t=document.createElement('style');t.type='text/css';
        t.charset='UTF-8';t.id='$id';t.appendChild(document.createTextNode(e));document.head.appendChild(t)}})();
    """.trimIndent()

    /** i6/c0.n and i6/g: dark scheme uses 30% of the chosen background over black. */
    private fun style(color: Int, textSize: Int, customCss: String): String {
        val background = if (color == 0) -1 else color
        val dark = (0xff000000L or (((background shr 16 and 255) * .3f).toLong() shl 16) or
            (((background shr 8 and 255) * .3f).toLong() shl 8) or ((background and 255) * .3f).toLong()).toInt()
        fun contrast(c: Int) = if ((c shr 16 and 255) * .299 + (c shr 8 and 255) * .587 + (c and 255) * .114 >= 192) 0 else 0xffffff
        fun hex(c: Int) = String.format(Locale.ROOT, "%06x", c and 0xffffff)
        return ".via-reader-body,.via-reader-body>div{background-color:#${hex(background)}!important;}" +
            ".via-reader-body{color:#${hex(contrast(background))}!important;font-size:${textSize.coerceIn(8, 84)}px!important;}" +
            "@media(prefers-color-scheme:dark){.via-reader-body,.via-reader-body>div{background-color:#${hex(dark)}!important;}" +
            ".via-reader-body{color:#${hex(contrast(dark))}!important;}}$customCss"
    }
}
