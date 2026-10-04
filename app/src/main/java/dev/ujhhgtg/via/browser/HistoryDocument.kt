package dev.ujhhgtg.via.browser

import android.content.Context
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.common.GeneratedDocumentState
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.HistoryRepository
import java.io.File
import java.io.FileWriter

/** u9.c.g/l: cached history.html, newest 256 rows and global-incognito hint. */
object HistoryDocument {
    fun write(context: Context, preferences: BrowserPreferences, history: HistoryRepository): String {
        GeneratedDocumentState.initialize(preferences)
        val file = File(context.filesDir, "history.html")
        if (GeneratedDocumentState.has(GeneratedDocumentState.HISTORY) || !file.exists()) {
            try {
                FileWriter(file).buffered().use { writer ->
                    writer.write(InternalDocumentHtml.head(context.getString(R.string.action_history)))
                    writer.write(InternalDocumentHtml.style(InternalDocumentHtml.css(context, preferences)))
                    writer.write(InternalDocumentHtml.BODY)
                    val entries = try { history.list(256) } catch (error: Exception) { error.printStackTrace(); emptyList() }
                    if (entries.isEmpty()) writer.write(InternalDocumentHtml.hint(context.getString(R.string.empty_hint)))
                    else if (preferences.webFlags and 64 != 0) writer.write(InternalDocumentHtml.hint(context.getString(R.string.history_page_hint_when_incognito)))
                    for (entry in entries) writer.write(InternalDocumentHtml.row(entry.url.orEmpty(), entry.title.orEmpty(), "clock"))
                    writer.write(InternalDocumentHtml.END)
                }
                GeneratedDocumentState.clear(GeneratedDocumentState.HISTORY)
            } catch (_: Exception) { }
        }
        return "file://${file.path}"
    }
}
