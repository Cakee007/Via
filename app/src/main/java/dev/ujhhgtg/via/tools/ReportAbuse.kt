package dev.ujhhgtg.via.tools

import android.app.Activity
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.browser.PageTranslation
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import dev.ujhhgtg.via.common.applicationIoScope
import kotlinx.coroutines.launch
import dev.ujhhgtg.via.common.httpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.withCharset
import org.json.JSONObject

/** c8.s6.ma/T3 and z8.h2: report reasons are the original 1/2/4 bit values. */
class ReportAbuseClient(
    private val endpoint: String = "https://ra.viayoo.com/report_abuse",
    private val authorization: String = "Bearer tk_o4s4ghe1tqu60mj0ljy5im762ebrr",
) {
    suspend fun submit(url: String, title: String?, reason: Int, note: String?, locale: String = PageTranslation.systemLanguage()): Boolean {
        if (url.isEmpty()) return false
        val body = JSONObject().apply {
            put("url", url); put("reason", reason); if (!title.isNullOrEmpty()) put("title", title)
            if (!note.isNullOrEmpty()) put("note", note); put("locale", locale)
        }.toString().toByteArray(Charsets.UTF_8)
        return httpClient.post(endpoint) {
            header("Authorization", authorization)
            setBody(ByteArrayContent(body, ContentType.Application.Json.withCharset(Charsets.UTF_8)))
        }.status == HttpStatusCode.OK
    }
}

object ReportAbuse {
    fun show(activity: Activity, url: String?, title: String?, client: ReportAbuseClient = ReportAbuseClient()) {
        val page = url.orEmpty(); if (page.isEmpty() || !(page.startsWith("http://", true) || page.startsWith("https://", true))) return
        val choices = arrayOf(activity.getString(R.string.reason_distracting_content), activity.getString(R.string.reason_dangerous_website),
            activity.getString(R.string.reason_display_incorrectly))
        val values = intArrayOf(1, 2, 4)
        // s6.ma -> w5.k.a0(...,-1): no reason starts selected, and the note is a single-line input.
        ViaDialog(activity).title(R.string.report_abuse).singleChoice(choices, -1)
            .input("", activity.getString(R.string.remarks_optional), 1)
            .positive(android.R.string.ok) { _, result ->
                // T3 deliberately ignores option index zero; preserve the submitted note verbatim.
                val reason = (result.selected ?: intArrayOf()).filter { it > 0 }.sumOf { values[it] }
                val remarks = result.edit?.firstOrNull()
                if (reason == 0 && remarks.isNullOrEmpty()) return@positive
                applicationIoScope.launch {
                    try { client.submit(page, title, reason, remarks) }
                    catch (error: kotlinx.coroutines.CancellationException) { throw error }
                    catch (error: Exception) { android.util.Log.w("Via", "Cannot submit abuse report", error) }
                }
                ViaToast.makeText(activity, R.string.report_submitted, ViaToast.LENGTH_SHORT).show()
            }.negative(android.R.string.cancel).show()
    }
}
