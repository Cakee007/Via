package dev.ujhhgtg.via.tools

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.browser.UrlParser
import dev.ujhhgtg.via.browser.UrlResolver
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import java.net.URLEncoder

/** s6.j3 and i6.i0.a: camera scans navigate the current tab; plain text becomes an escaped document. */
object QrResults {
    fun scanTarget(value: String, searchTemplate: String, title: String): String {
        if (UrlParser(value).isValid) {
            val resolved = UrlResolver.resolveInput(value, searchTemplate)
            if (resolved != null && (resolved.startsWith("http://", true) || resolved.startsWith("https://", true) ||
                    resolved.startsWith("file://", true) || resolved.startsWith("data:", true))) return resolved
        }
        return textDocument(value, title)
    }

    fun textDocument(text: String, title: String): String {
        val html = "<head><title>" + escape(title.ifEmpty { "Untitled" }) + "</title><meta charset=\"utf-8\" name=\"viewport\" content=\"width=device-width, initial-scale=1, user-scalable=no, minimal-ui\"><style>body{font-family:-apple-system;text-align:center;margin-top:120px;color:black}@media(prefers-color-scheme:dark){body{color:white}}</style></head><body>" + escape(text) + "</body>"
        return "data:text/html," + URLEncoder.encode(html, "UTF-8").replace("+", "%20")
    }
    private fun escape(text: String) = buildString {
        text.forEach { append(when (it) { '"' -> "&quot;"; '<' -> "&lt;"; '>' -> "&gt;"; '&' -> "&amp;"; '\'' -> "&#039;"; else -> it.toString() }) }
    }

    /** s6.F3: image scans offer Copy and either Open in new tab or Search, selected by URL validity. */
    fun showImageResult(activity: Activity, text: String?, openOrSearch: (String) -> Unit) {
        if (text.isNullOrEmpty()) { ViaToast.show(activity, R.string.qr_code_not_detected); return }
        ViaDialog(activity).title(R.string.qr_code_scan_result).message(text).negative(android.R.string.cancel)
            .neutral(android.R.string.copy) {
                (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(null, text))
                ViaToast.show(activity, R.string.toast_copy_text_successful)
            }.positive(if (UrlParser(text).isValid) R.string.action_open_in_new else R.string.search_hint) { _, _ -> openOrSearch(text) }.show()
    }
}
