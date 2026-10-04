package dev.ujhhgtg.via.tools

import android.app.Activity
import android.content.Context
import android.webkit.WebView
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import java.io.File
import java.util.Locale
import java.util.UUID

/** c8.ua.P1/X, c8.s6.k0/C4, z8.c1.n/L and z8.v1: MHTML or A4 PDF to app offline storage. */
object SavedPageTools {
    fun show(activity: Activity, view: WebView, title: String = view.title.orEmpty(), onViewSavedPages: (() -> Unit)? = null) {
        if (view.url.orEmpty().startsWith("file://", true)) { ViaToast.makeText(activity, R.string.cannot_work, ViaToast.LENGTH_SHORT).show(); return }
        val preferences = BrowserPreferences(activity)
        val initial = title.ifEmpty { UUID.randomUUID().toString().uppercase(Locale.ROOT) }
        ViaDialog(activity).title(R.string.action_save_web_page)
            .input(initial, activity.getString(R.string.hint_title), 1)
            .check(R.string.save_as_pdf, preferences.appFlags and 16384 != 0)
            .positive(android.R.string.ok) { _, result ->
                val name = result.edit?.firstOrNull()?.takeIf(String::isNotEmpty) ?: return@positive
                preferences.appFlags = if (result.checked) preferences.appFlags or 16384 else preferences.appFlags and 16384.inv()
                val target = uniqueFile(directory(activity), sanitize(name), if (result.checked) ".pdf" else ".mht")
                // ua.X returns true as soon as the WebView write is initiated. Z3's
                // View action opens Y9 (saved pages); it does not wait for a write callback.
                if (result.checked) PdfExporter.write(view, target) {}
                else view.saveWebArchive(target.absolutePath)
                ViaToast.show(activity, activity.getString(R.string.saved_page_successfully), ViaToast.LENGTH_SHORT,
                    activity.getString(R.string.view_downloads), action = onViewSavedPages)
            }.negative(android.R.string.cancel).show()
    }

    fun directory(context: Context): File = File(context.getExternalFilesDir(null) ?: context.filesDir, "offline").apply { mkdirs() }

    fun list(context: Context): List<File> {
        // Include the earlier restoration's internal directory, so its already saved pages remain usable.
        return listOf(directory(context), File(context.filesDir, "offline")).distinctBy { it.absolutePath }
            .flatMap { it.listFiles().orEmpty().asList() }.filter { it.isFile }.sortedByDescending { it.lastModified() }
    }

    fun sanitize(source: String): String {
        var name = source.trim().replace(Regex("[/\\\\:*?\"<>|]"), "-").take(200)
        if (name.toByteArray(Charsets.UTF_8).size > 200) {
            var low = 0; var high = name.length
            while (low < high) {
                val middle = (low + high + 1) / 2
                if (name.substring(0, middle).toByteArray(Charsets.UTF_8).size <= 200) low = middle else high = middle - 1
            }
            name = name.substring(0, low)
        }
        return name
    }

    fun uniqueFile(directory: File, title: String, extension: String): File {
        directory.mkdirs()
        var file = File(directory, title + extension)
        var suffix = 0
        while (file.exists()) { suffix++; file = File(directory, "$title ($suffix)$extension") }
        return file
    }

}
