package dev.ujhhgtg.via.tools

import android.app.Activity
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import dev.ujhhgtg.via.engine.EnginePage
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.browser.DocumentPolicy
import dev.ujhhgtg.via.ui.ViaToast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** c8.s6.ia/X7: shared by menu Print and the webpage window.print bridge. */
object BrowserPrinting {
    fun print(activity: Activity, view: EnginePage?) {
        if (view == null) return
        val manager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (manager == null) { failed(activity); return }
        val title = view.title?.takeIf(String::isNotEmpty) ?: DocumentPolicy.host(view.url.orEmpty()).takeIf(String::isNotEmpty)
            ?: run {
                val locale = Locale.getDefault()
                val format = when (locale.country) { "US" -> "MMddyyyy"; "UK" -> "ddMMyyyy"; else -> "yyyyMMdd" }
                String.format(locale, "%s - %s", activity.getString(R.string.untitled), SimpleDateFormat(format, locale).format(Date()))
            }
        val adapter = view.createPrintAdapter(title) ?: run { failed(activity); return }
        try { manager.print(title, adapter, PrintAttributes.Builder().build()) }
        catch (_: Exception) { failed(activity) }
    }
    private fun failed(activity: Activity) = ViaToast.makeText(activity, R.string.toast_operation_failed, ViaToast.LENGTH_SHORT).show()
}
