package dev.ujhhgtg.via.reader

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.http.SslCertificate
import android.text.Html
import android.text.TextUtils
import android.webkit.CookieManager
import android.webkit.WebView
import dev.ujhhgtg.via.ui.ViaToast
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewFeature
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.security.MessageDigest
import java.text.DateFormat

/** c8.s6.lb/Ua/B5 and z8.b0.t/p/q/r/u: certificate and current-site cookie actions. */
object PageSecurityDialogs {
    fun certificate(activity: Activity, webView: WebView) {
        ViaDialog(activity).title(R.string.certificate_info).message(certificateText(activity, webView.certificate)).positive(android.R.string.ok).show()
    }
    fun certificateText(context: Context, certificate: SslCertificate?): CharSequence {
        if (certificate == null) return context.getString(R.string.no_certificate)
        fun value(text: String?) = text?.takeIf(String::isNotEmpty) ?: " - "
        val dates = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT)
        val content = Html.fromHtml(context.getString(R.string.certificate_detail,
            value(certificate.issuedTo.cName), value(certificate.issuedTo.oName), value(certificate.issuedTo.uName),
            value(certificate.issuedBy.cName), value(certificate.issuedBy.oName), dates.format(certificate.validNotBeforeDate), dates.format(certificate.validNotAfterDate)), 0)
        val x509 = certificate.x509Certificate ?: return content
        return runCatching { TextUtils.concat(content, "\n", Html.fromHtml(context.getString(R.string.certificate_detail_fingerprints,
            value(sha256(x509.encoded)), value(sha256(x509.publicKey.encoded))), 0)) }.getOrDefault(content)
    }
    private fun sha256(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02X".format(it) }
    fun cookies(activity: Activity, url: String) {
        val parsed = url.toHttpUrlOrNull() ?: return
        val host = parsed.host
        if (host.isEmpty()) return
        val cookies = CookieManager.getInstance().getCookie(url)
        val dialog = ViaDialog(activity).title(activity.getString(R.string.title_cookies_for_site, host))
        if (cookies.isNullOrEmpty()) dialog.message(R.string.message_no_cookies).positive(android.R.string.ok)
        else dialog.message(cookies).positive(android.R.string.copy) { _, _ ->
            (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("", cookies))
            ViaToast.makeText(activity, R.string.toast_copy_text_successful, ViaToast.LENGTH_SHORT).show()
        }.negative(android.R.string.cancel).neutral(R.string.action_delete) {
            if (clearCookies(activity, url)) ViaToast.makeText(activity, activity.getString(R.string.toast_cookies_for_site_cleared, host), ViaToast.LENGTH_SHORT).show()
        }
        dialog.show()
    }
    private fun clearCookies(context: Context, url: String): Boolean {
        val success = clearCookieDatabase(context, url) || expireCookies(url)
        if (success) CookieManager.getInstance().flush()
        return success
    }
    private fun clearCookieDatabase(context: Context, url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        val domains = ArrayList<String>()
        var host = parsed.host
        val root = parsed.topPrivateDomain()
        while (true) {
            domains += host; domains += ".$host"
            if (host == root) break
            val dot = host.indexOf('.')
            if (root == null || dot < 0) break
            host = host.substring(dot + 1)
        }
        val file = File(context.dataDir, "app_webview/Default/Cookies")
        return file.isFile && runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
                database.beginTransaction()
                try { domains.forEach { database.delete("cookies", "host_key = ?", arrayOf(it)) }; database.setTransactionSuccessful() } finally { database.endTransaction() }
            }
            true
        }.getOrDefault(false)
    }
    private fun expireCookies(url: String): Boolean = runCatching {
        val parsed = url.toHttpUrlOrNull() ?: return false
        val paths = ArrayList<String>().apply {
            add(""); var path = ""
            parsed.encodedPathSegments.forEach { path += "/$it"; add(path) }
        }
        val domains = ArrayList<String>().apply {
            var host = parsed.host; add(host)
            parsed.topPrivateDomain()?.let { root -> while (host != root) { host = host.substring(host.indexOf('.') + 1); add(host) } }
            add("")
        }
        val manager = CookieManager.getInstance()
        val infos = if (WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)) CookieManagerCompat.getCookieInfo(manager, url)
            else manager.getCookie(url)?.takeIf(String::isNotEmpty)?.split(';').orEmpty()
        infos.forEach { info ->
            val parts = info.split(';').map { it.split('=', limit = 2).let { pair -> pair[0].trim() to pair.getOrNull(1)?.trim() } }
            val name = parts.first().first
            val path = parts.firstOrNull { it.first.equals("path", true) }
            val domain = parts.firstOrNull { it.first.equals("domain", true) }
            if (path == null || domain == null) domains.forEach { host -> paths.forEach { route -> manager.setCookie(url, "$name=;Domain=$host;Path=$route;Max-Age=0", null) } }
            else {
                manager.setCookie(url, "$name=;Path=${path.second};Max-Age=0", null)
                manager.setCookie(url, "$name=;Domain=${domain.second};Path=${path.second};Max-Age=0", null)
            }
        }
        true
    }.getOrDefault(false)
}
