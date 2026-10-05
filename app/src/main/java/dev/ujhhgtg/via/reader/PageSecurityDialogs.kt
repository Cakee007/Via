package dev.ujhhgtg.via.reader

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.http.SslCertificate
import android.text.Html
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dev.ujhhgtg.via.engine.EnginePage
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.MessageDigest
import java.text.DateFormat
import kotlinx.coroutines.launch

/** c8.s6.lb/Ua/B5 and z8.b0.t/p/q/r/u: certificate and current-site cookie actions. */
object PageSecurityDialogs {
    fun certificate(activity: Activity, webView: EnginePage) {
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
    fun cookies(activity: ComponentActivity, url: String) {
        val parsed = url.toHttpUrlOrNull() ?: return
        val host = parsed.host
        if (host.isEmpty()) return
        activity.lifecycleScope.launch { showCookies(activity, url, host) }
    }

    private suspend fun showCookies(activity: ComponentActivity, url: String, host: String) {
        val cookies = dev.ujhhgtg.via.engine.Engines.backend.cookies.get(url)
        val dialog = ViaDialog(activity).title(activity.getString(R.string.title_cookies_for_site, host))
        if (cookies.isNullOrEmpty()) dialog.message(R.string.message_no_cookies).positive(android.R.string.ok)
        else dialog.message(cookies).positive(android.R.string.copy) { _, _ ->
            (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("", cookies))
            ViaToast.makeText(activity, R.string.toast_copy_text_successful, ViaToast.LENGTH_SHORT).show()
        }.negative(android.R.string.cancel).neutral(R.string.action_delete) {
            activity.lifecycleScope.launch {
                if (dev.ujhhgtg.via.engine.Engines.backend.cookies.clearForSite(activity, url)) ViaToast.makeText(activity, activity.getString(R.string.toast_cookies_for_site_cleared, host), ViaToast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }
}
