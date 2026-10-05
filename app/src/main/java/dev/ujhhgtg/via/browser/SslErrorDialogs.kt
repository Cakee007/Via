package dev.ujhhgtg.via.browser

import android.app.Activity
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.engine.SslErrorRequest
import dev.ujhhgtg.via.engine.SslErrorRequest.Kind
import dev.ujhhgtg.via.reader.PageSecurityDialogs
import dev.ujhhgtg.via.settings.settingsColor
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import java.util.Locale

/** e8.h0 and process-wide d8.f: error-mask policy and a single decision per host/mask. */
internal object SslErrorDialogs {
    private val choices = HashMap<String, Boolean>()
    private val pending = HashMap<String, MutableList<SslErrorRequest>>()
    private val errors = listOf(
        Kind.UNTRUSTED to R.string.ssl_warning_certificate_untrusted,
        Kind.DATE_INVALID to R.string.ssl_warning_certificate_date_invalid,
        Kind.EXPIRED to R.string.ssl_warning_certificate_expired,
        Kind.ID_MISMATCH to R.string.ssl_warning_certificate_domain_mismatch,
        Kind.NOT_YET_VALID to R.string.ssl_warning_certificate_not_yet_valid,
        Kind.INVALID to R.string.ssl_warning_certificate_invalid,
    )

    fun show(activity: Activity, pageUrl: String?, preferences: BrowserPreferences, handler: SslErrorRequest) {
        val error = handler
        // Without a way to proceed there is nothing to ask; the engine shows its own error page.
        if (!dev.ujhhgtg.via.engine.Engines.backend.capabilities.sslProceed) { handler.cancel(); return }
        val mask = errors.foldIndexed(0) { index, flags, (kind, _) -> if (kind in error.errors) flags or (1 shl index) else flags }
        val ignored = preferences.ignoredSslWarning
        if (ignored != 0 && ignored and mask == mask) { handler.proceed(); return }
        val errorHost = DocumentPolicy.host(error.url)
        val pageHost = DocumentPolicy.host(pageUrl.orEmpty())
        val host = errorHost.ifEmpty { pageHost }
        choices[host]?.let { allow -> if (allow) handler.proceed() else handler.cancel(); return }
        if (errorHost.isNotEmpty() && pageHost.isNotEmpty() && errorHost != pageHost && error.primaryError != Kind.UNTRUSTED) {
            handler.cancel(); return
        }
        val key = "$host:$mask"
        pending[key]?.let { it += handler; return }
        pending[key] = mutableListOf(handler)

        val descriptions = errors.filter { (kind, _) -> kind in error.errors }
            .joinToString("") { (_, label) -> "- ${activity.getString(label)}\n" }
        val message = activity.getString(R.string.ssl_warning_insecure_connection_message,
            host.ifEmpty { activity.getString(R.string.the_site).lowercase(Locale.ROOT) }, descriptions)
        val body: CharSequence = if (error.certificate == null) message else SpannableString(message).apply {
            val details = PageSecurityDialogs.certificateText(activity, error.certificate)
            val start = message.indexOf(descriptions)
            val end = start + descriptions.length
            setSpan(object : ClickableSpan() {
                override fun onClick(view: View) {
                    (view.parent as? ScrollView)?.let { scroll ->
                        val metrics = activity.resources.displayMetrics
                        scroll.layoutParams = scroll.layoutParams.apply {
                            height = maxOf(view.measuredHeight, minOf(metrics.widthPixels, metrics.heightPixels) * 2 / 5)
                        }
                    }
                    (view as? TextView)?.text = details
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(settingsColor(activity, R.attr.viaAccentColor, 0xff6f8de1.toInt())),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        fun complete(allow: Boolean, remember: Boolean) {
            if (remember && host.isNotEmpty()) choices[host] = allow
            val waiting = pending.remove(key) ?: listOf(handler)
            waiting.forEach { if (allow) it.proceed() else it.cancel() }
        }
        ViaDialog(activity).title(R.string.title_warning).message(body)
            .check(R.string.ssl_warning_dont_ask_again, false).cancelable(false).canceledOnTouchOutside(false)
            .positive(android.R.string.ok) { _, result -> complete(true, result.checked) }
            .negativeResult(android.R.string.cancel) { _, result -> complete(false, result.checked) }
            .show()
    }
}
