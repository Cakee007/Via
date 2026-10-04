package dev.ujhhgtg.via.browser

import android.content.Context
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import java.net.URLEncoder

/** u9.a.a: the main-document filter warning and its w2.c continue-loading link. */
object BlockedPageDocument {
    fun html(context: Context, url: String?, rule: String?): String {
        if (url.isNullOrEmpty()) return ""
        val dark = BrowserPreferences(context).isNightMode
        val background = if (dark) "#000" else "#fff"
        val codeBackground = if (dark) "#303030" else "#e0e0e6"
        val foreground = if (dark) "#fff" else "#000"
        return buildString {
            append("<html>")
            append("<head>")
            append("<meta name=\"color-scheme\" content=\"light dark\">")
            append("<meta charset=\"utf-8\">")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, user-scalable=true, minimal-ui\">")
            append("<title>")
            append(DocumentPolicy.host(url))
            append("</title>")
            append("<style>")
            append("body{")
            append("background:")
            append(background)
            append(";")
            append("color:")
            append(foreground)
            append(";text-align:center;margin:auto;padding:40px;box-sizing:border-box}")
            append(".icon{width:100px;height:100px;text-align:center;margin:auto;fill:#dd4747}")
            append(".btn{background-color:#5a5a5a;color:#fff;padding:8px 12px;border:none;border-radius:8px;stroke:none;size:15px;text-decoration:none;margin-top:36px;max-width:120px;font-size:15px}")
            append(".text{font-size:15px;margin:16px 0 8px}")
            append(".code{background-color:")
            append(codeBackground)
            append(";font-family:monospace;font-size:13px;white-space:normal;word-break:break-all;padding:4px;margin:0 0 16px;line-height:1.5}")
            append(".op{margin-top:32px}")
            append("</style>")
            append("</head>")
            append("<body>")
            append("<div class=\"icon\">")
            append("<svg viewBox=\"0 0 1794 1664\"><path d=\"m 1025.0139,1375 0,-190 q 0,-14 -9.5,-23.5 -9.5,-9.5 -22.5,-9.5 l -192,0 q -13,0 -22.5,9.5 -9.5,9.5 -9.5,23.5 l 0,190 q 0,14 9.5,23.5 9.5,9.5 22.5,9.5 l 192,0 q 13,0 22.5,-9.5 9.5,-9.5 9.5,-23.5 z m -2,-374 18,-459 q 0,-12 -10,-19 -13,-11 -24,-11 l -220,0 q -11,0 -24,11 -10,7 -10,21 l 17,457 q 0,10 10,16.5 10,6.5 24,6.5 l 185,0 q 14,0 23.5,-6.5 9.5,-6.5 10.5,-16.5 z m -14,-934 768,1408 q 35,63 -2,126 -17,29 -46.5,46 -29.5,17 -63.5,17 l -1536,0 q -34,0 -63.5,-17 -29.5,-17 -46.5,-46 -37,-63 -2,-126 L 785.01389,67 q 17,-31 47,-49 30,-18 65,-18 35,0 65,18 30,18 47,49 z\"></path></svg>")
            append("</div>")
            append("<p class=\"text\">")
            append(context.getString(R.string.stop_loading_description))
            append("</p>")
            append("<p class=\"code\">")
            // The original inserts the URL and serialized filter directly, without HTML escaping.
            append(url)
            append("</p>")
            append("<p class=\"text\">")
            append(context.getString(R.string.stop_loading_reason))
            append("</p>")
            append("<p class=\"code\">")
            append(rule)
            append("</p>")
            append("<div class=\"op\">")
            append("<a class=\"btn\" href=\"")
            append(continueUrl(url))
            append("\">")
            append(context.getString(R.string.continue_loading))
            append("</a>")
            append("</div>")
            append("</body>")
            append("</html>")
        }
    }

    private fun continueUrl(url: String): String {
        val encoded = try {
            URLEncoder.encode(url, "UTF-8").replace("+", "%20")
        } catch (error: Exception) {
            error.printStackTrace()
            url
        }
        return "v://blocker/jump?url=" + encoded
    }
}
