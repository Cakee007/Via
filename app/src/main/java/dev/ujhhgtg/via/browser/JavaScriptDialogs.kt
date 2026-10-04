package dev.ujhhgtg.via.browser

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.ui.dialog.ViaDialog

/** e8.b0.j/k/l/m and process-wide d8.a/b: Via's JavaScript dialog handlers. */
internal object JavaScriptDialogs {
    private class Counter(var score: Int = 0, var time: Long = 0L)
    private class Record(val alert: Counter = Counter(), val confirm: Counter = Counter())
    private val records = HashMap<String, Record>()

    fun alert(view: WebView, url: String, message: String, result: JsResult): Boolean =
        showMessage(view, url, message, result, confirmation = false)

    fun confirm(view: WebView, url: String, message: String, result: JsResult): Boolean =
        showMessage(view, url, message, result, confirmation = true)

    private fun showMessage(view: WebView, url: String, message: String, result: JsResult, confirmation: Boolean): Boolean {
        if (!view.isShown) { result.cancel(); return true }
        val activity = activity(view.context) ?: run { result.cancel(); return true }
        val domain = DocumentPolicy.host(url)
        val key = domain.ifEmpty { "default" }
        val state = state(key, confirmation)
        if (state == 2) { result.cancel(); return true }
        val offerIgnore = state == 1
        ViaDialog(activity).title(title(activity, domain)).message(message)
            .cancelable(true).canceledOnTouchOutside(false)
            .onCancel { result.cancel() }
            .positive(android.R.string.ok) { _, response ->
                result.confirm()
                answered(key, confirmation, offerIgnore && response.checked)
            }
            .apply {
                // e8.b0.j has no cancel button; Back cancels its JsResult without
                // recording an answer. Confirm's cancel button records its own score.
                if (confirmation) negativeResult(android.R.string.cancel) { _, response ->
                    result.cancel()
                    answered(key, true, offerIgnore && response.checked)
                }
                if (offerIgnore) check(R.string.ignore_site_message_for_a_minute, false)
            }
            .show()
        return true
    }

    fun beforeUnload(view: WebView, message: String, result: JsResult): Boolean {
        // e8.b0.k allows an invisible document to leave, unlike alert/confirm/prompt.
        if (!view.isShown) { result.confirm(); return true }
        val activity = activity(view.context) ?: run { result.cancel(); return true }
        ViaDialog(activity).title(R.string.confirm_to_leave).message(message)
            .cancelable(true).canceledOnTouchOutside(false)
            .onCancel { result.cancel() }
            .positive(R.string.leave) { _, _ -> result.confirm() }
            .negative(R.string.stay) { result.cancel() }
            .show()
        return true
    }

    fun prompt(view: WebView, url: String, message: String?, defaultValue: String?, result: JsPromptResult): Boolean {
        if (!view.isShown) { result.cancel(); return true }
        // The original handles Baidu's bridge probe without displaying an input.
        if (message?.startsWith("BdboxApp:{\"obj\":\"") == true) { result.confirm(""); return true }
        val activity = activity(view.context) ?: run { result.cancel(); return true }
        ViaDialog(activity).title(title(activity, DocumentPolicy.host(url)))
            .input(defaultValue, defaultValue, 1)
            .cancelable(true).canceledOnTouchOutside(false)
            .onCancel { result.cancel() }
            .positive(android.R.string.ok) { _, response -> result.confirm(response.edit?.firstOrNull().orEmpty()) }
            .negative(android.R.string.cancel) { result.cancel() }
            .apply { if (!message.isNullOrEmpty()) this.message(message) }
            .show()
        return true
    }

    private fun title(context: Context, domain: String) = if (domain.isEmpty()) context.getString(R.string.dialog_message)
        else context.getString(R.string.dialog_message_from, domain)

    private fun activity(context: Context): Activity? {
        var current = context
        while (current is ContextWrapper && current !is Activity) current = current.baseContext
        return current as? Activity
    }

    private fun counter(domain: String, confirmation: Boolean): Counter = records.getOrPut(domain) { Record() }
        .let { if (confirmation) it.confirm else it.alert }

    /** d8.a.b: score 30 offers mute, 31 suppresses until a minute after the answer. */
    private fun state(domain: String, confirmation: Boolean): Int {
        val record = counter(domain, confirmation)
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - record.time
        record.score = if (elapsed >= 60_000L) 0 else if (record.score < 30)
            minOf(30, record.score + when { elapsed <= 5_000L -> 15; elapsed <= 10_000L -> 10; elapsed <= 30_000L -> 2; else -> 1 })
            else record.score
        if (record.score <= 30) record.time = now
        return when { record.score < 30 -> 0; record.score > 30 -> 2; else -> 1 }
    }

    private fun answered(domain: String, confirmation: Boolean, ignore: Boolean) {
        val record = counter(domain, confirmation)
        if (record.score <= 30) {
            if (ignore) record.score = 31
            record.time = SystemClock.elapsedRealtime()
        }
    }
}
