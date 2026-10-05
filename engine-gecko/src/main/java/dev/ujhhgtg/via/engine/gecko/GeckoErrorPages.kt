package dev.ujhhgtg.via.engine.gecko

import android.content.Context
import android.text.Html
import android.util.Base64
import org.mozilla.geckoview.WebRequestError

/**
 * The page Gecko shows for a failed load. It must not be http(s); a data: URI gets Gecko's error-page
 * helpers, so a certificate error can be accepted from the page itself.
 */
internal object GeckoErrorPages {
    fun page(context: Context, url: String, error: WebRequestError): String {
        val certificate = error.category == WebRequestError.ERROR_CATEGORY_SECURITY
        fun text(id: Int) = Html.escapeHtml(context.getString(id))
        val detail = if (certificate) text(R.string.gecko_error_certificate) else Html.escapeHtml(errorName(error.code))
        val accept = if (!certificate) "" else
            """<button onclick="document.addCertException(true).then(()=>location.reload())">${text(R.string.gecko_error_accept_risk)}</button>"""
        val html = """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            |<title>${text(R.string.gecko_error_title)}</title><style>
            |body{font-family:sans-serif;margin:0;padding:24px;color:#222;background:#fff}
            |@media(prefers-color-scheme:dark){body{color:#ddd;background:#121212}}
            |h1{font-size:20px;font-weight:500}p{font-size:14px;line-height:1.5;word-break:break-all;opacity:.8}
            |button{display:block;width:100%;margin-top:12px;padding:12px;font-size:15px;border:0;border-radius:8px;background:#4c7bd9;color:#fff}
            |button.secondary{background:transparent;color:inherit;border:1px solid #8884}
            |</style></head><body><h1>${text(R.string.gecko_error_title)}</h1><p>${Html.escapeHtml(url)}</p><p>$detail</p>
            |<button onclick="location.reload()">${text(R.string.gecko_error_retry)}</button>
            |${accept.replace("<button", "<button class=\"secondary\"")}</body></html>""".trimMargin()
        return "data:text/html;charset=utf-8;base64," + Base64.encodeToString(html.toByteArray(), Base64.NO_WRAP)
    }

    private fun errorName(code: Int): String = WebRequestError::class.java.fields
        .firstOrNull { it.name.startsWith("ERROR_") && !it.name.startsWith("ERROR_CATEGORY_") && it.getInt(null) == code }
        ?.name ?: "ERROR_UNKNOWN"
}
