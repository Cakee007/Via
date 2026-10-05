package dev.ujhhgtg.via.browser

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.github.promeg.pinyinhelper.Pinyin
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.common.GeneratedDocumentState
import dev.ujhhgtg.via.data.BookmarkRepository
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.HistoryRepository
import java.io.File
import java.io.FileWriter
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/** u9.c.b/c/d/e/f/i/j: the original cached internal files, rather than native-page substitutes. */
object InternalDocuments {
    enum class Kind { BOOKMARKS, FOLDER, HISTORY, CATALOG, ABOUT, BLANK, SAVED_PAGES }

    fun write(context: Context, preferences: BrowserPreferences, database: BrowserDatabase,
        kind: Kind, folderId: String? = null): String {
        GeneratedDocumentState.initialize(preferences)
        return when (kind) {
            Kind.BOOKMARKS, Kind.FOLDER -> bookmarks(context, preferences, database, folderId)
            Kind.HISTORY -> HistoryDocument.write(context, preferences, HistoryRepository(database))
            Kind.CATALOG -> cached(context, "catalog.html", GeneratedDocumentState.CATALOG) { catalog(context, preferences) }
            Kind.ABOUT -> cached(context, "about.html", GeneratedDocumentState.ABOUT) { about(context, preferences) }
            Kind.BLANK -> cached(context, "blank.html", GeneratedDocumentState.BLANK_AND_PAGE_STATE) {
                InternalDocumentHtml.head(context.getString(R.string.action_blank)) +
                    InternalDocumentHtml.style("body{background:transparent;}") + "<body></body></html>"
            }
            Kind.SAVED_PAGES -> savedPages(context, preferences)
        }
    }

    private data class BookmarkRow(val title: String?, val order: Int, val url: String,
        val folder: Boolean, val immutable: Boolean = false)

    /** Root is cached; folder.html is regenerated for every nonempty folder request. */
    private fun bookmarks(context: Context, preferences: BrowserPreferences, database: BrowserDatabase, requestedFolder: String?): String {
        val repo = BookmarkRepository(database)
        val rootRequest = requestedFolder.isNullOrEmpty()
        val file = File(context.filesDir, if (rootRequest) "bookmarks.html" else "folder.html")
        var resolvedFolder = requestedFolder.orEmpty()
        if (!rootRequest || GeneratedDocumentState.has(GeneratedDocumentState.BOOKMARKS) || !file.exists()) {
            try {
                val current = requestedFolder?.let(repo::findFolder)
                // u9.c.d falls back to o9.a.r (the immutable root) for an unknown folder ID.
                resolvedFolder = current?.id.orEmpty()
                val rows = buildList {
                    if (resolvedFolder.isNotEmpty()) {
                        val parent = current?.parentFolderId?.let(repo::findFolder)
                        add(BookmarkRow("..", parent?.ordering ?: 0, bookmarkFolderUrl(parent?.id.orEmpty()),
                            folder = true,
                            immutable = true
                        ))
                    }
                    database.readableDatabase.query("bookmark_folders", arrayOf("_id", "title", "ordering"),
                        "parent_folder_id = ?", arrayOf(resolvedFolder), null, null, null).use { cursor ->
                        while (cursor.moveToNext()) add(BookmarkRow(normalizeTitle(cursor.getString(1)), cursor.getInt(2),
                            bookmarkFolderUrl(cursor.getString(0)), true))
                    }
                    database.readableDatabase.query("bookmark_items", arrayOf("url", "title", "ordering"),
                        "folder_id = ?", arrayOf(resolvedFolder), null, null, null).use { cursor ->
                        while (cursor.moveToNext()) add(BookmarkRow(normalizeTitle(cursor.getString(1)), cursor.getInt(2),
                            bookmarkTarget(cursor.getString(0)), false))
                    }
                }.sortedWith { first, second ->
                    when {
                        first.immutable != second.immutable -> if (first.immutable) -1 else 1
                        first.folder != second.folder -> if (first.folder) -1 else 1
                        preferences.bookmarkOrder == 1 -> second.order - first.order
                        preferences.bookmarkOrder == 2 -> compareTitles(first.title, second.title)
                        else -> first.order - second.order
                    }
                }
                FileWriter(file).buffered().use { writer ->
                    val title = if (!rootRequest && !current?.title.isNullOrEmpty()) normalizeTitle(current.title) else context.getString(R.string.action_bookmarks)
                    writer.write(InternalDocumentHtml.head(title))
                    writer.write(InternalDocumentHtml.style(InternalDocumentHtml.css(context, preferences)))
                    writer.write(InternalDocumentHtml.BODY)
                    var foldersOpened = false
                    var bookmarksOpened = false
                    for ((title, _, url, folder) in rows) {
                        if (folder) {
                            if (!foldersOpened) { writer.write("<div id=\"bookmark_folders\">"); foldersOpened = true }
                        } else if (!bookmarksOpened) {
                            if (foldersOpened) writer.write("</div>")
                            writer.write("<div id=\"bookmark_tags\">")
                            bookmarksOpened = true
                        }
                        writer.write(InternalDocumentHtml.row(url, title.orEmpty(), if (folder) "tag" else "bookmark"))
                    }
                    // The source closes the bookmark group here, but leaves an all-folder group to HTML parsing.
                    if (bookmarksOpened) writer.write("</div>")
                    if (rows.isEmpty() && rootRequest) writer.write(InternalDocumentHtml.hint(context.getString(R.string.empty_hint)))
                    writer.write(InternalDocumentHtml.END)
                }
                if (rootRequest) GeneratedDocumentState.clear(GeneratedDocumentState.BOOKMARKS)
            } catch (error: Exception) { error.printStackTrace() }
        }
        return "file://${context.filesDir.path}/" + if (resolvedFolder.isEmpty()) "bookmarks.html" else "folder.html?folder=$resolvedFolder"
    }

    /** u9.c.e: catalog order is independent of persisted bookmarks and menu customization. */
    private fun catalog(context: Context, preferences: BrowserPreferences): String = buildString {
        append(InternalDocumentHtml.head(context.getString(R.string.app_name)))
        append(InternalDocumentHtml.style(InternalDocumentHtml.css(context, preferences)))
        append(InternalDocumentHtml.BODY)
        for ((title, url) in listOf(R.string.action_bookmarks to "v://bookmarks", R.string.action_history to "v://history",
            R.string.network_log to "v://log", R.string.action_saved_pages to "v://offline", R.string.home to "v://home",
            R.string.settings_about to "v://about")) append(InternalDocumentHtml.row(url, context.getString(title), "tag"))
        append(InternalDocumentHtml.END)
    }

    /** u9.d.a: CN locale and the APK's verified `viayoo` channel enable the original donation paragraph. */
    private fun about(context: Context, preferences: BrowserPreferences): String = buildString {
        val chinese = context.resources.configuration.locales[0].country == "CN"
        val region = if (preferences.cloudServer == 1) "zh-cn" else "en"
        append(InternalDocumentHtml.head(context.getString(R.string.settings_about)))
        append(InternalDocumentHtml.style(InternalDocumentHtml.css(context, preferences, about = true)))
        append(InternalDocumentHtml.BODY)
        append("<h1>").append(context.getString(R.string.glean)).append("</h1><p>")
        append(if (Locale.getDefault().country.equals("CN", true)) "莫忧世事兼身事，须著人间比梦间。" else "Less is more.")
        append("</p><h1>").append(context.getString(R.string.contact_us)).append("</h1>")
        append("<p><a href=\"http://viayoo.com/\">").append(context.getString(R.string.official_website)).append("</a></p>")
        if (chinese) append("<p><a href=\"http://viayoo.com/contact/qqgroup/\">官方鹅群</a></p>")
        append("<p><a href=\"https://t.me/viatg\">Telegram</a></p><p><a href=\"https://github.com/tuyafeng/Via\">")
        append(context.getString(R.string.help_us_translate)).append("</a></p><p><a href=\"https://viayoo.com/$region/docs/terms-of-use.html\">")
        append(context.getString(R.string.terms_of_use)).append("</a></p><p><a href=\"https://viayoo.com/$region/docs/privacy-policy.html\">")
        append(context.getString(R.string.privacy_policy)).append("</a></p>")
        if (chinese) {
            append("<h1>").append(context.getString(R.string.donate)).append("</h1><p>")
            append("我们利用有限的业余时间设计了 Via，虽然它并不那么美好，但正努力前行。\n如果你喜欢我们的作品，可以捐赠来支持我们。\n\n所有的捐赠都将用来：提升我们的环境配置以及积极性。\n\n支付宝: 2376688759@qq.com\nPayPal: wiar1824@gmail.com".replace("\n", "</p><p>"))
            append("</p>")
        }
        append("<br><br><br>").append(InternalDocumentHtml.END)
    }

    /** u9.c.i/j scans the offline directory and the configured legacy external-download directory. */
    @Suppress("DEPRECATION")
    private fun savedPages(context: Context, preferences: BrowserPreferences): String {
        val file = File(context.filesDir, "save.html")
        try {
            val offline = (context.getExternalFilesDir("offline") ?: File(context.filesDir, "offline")).apply { mkdirs() }
            val legacyDownloads = File(Environment.getExternalStorageDirectory(), preferences.downloadDirectory)
            val files = (offline.listFiles().orEmpty().asList() + legacyDownloads.listFiles().orEmpty().asList())
                .sortedWith { first, second ->
                    val difference = first.lastModified() - second.lastModified()
                    if (difference == 0L) 0 else if (difference > 0L) -1 else 1
                }.filter { it.name.endsWith(".mht") }
            FileWriter(file).buffered().use { writer ->
                writer.write(InternalDocumentHtml.head(context.getString(R.string.action_saved_pages)))
                writer.write(InternalDocumentHtml.style(InternalDocumentHtml.css(context, preferences)))
                writer.write(InternalDocumentHtml.BODY)
                writer.write(InternalDocumentHtml.hint(context.getString(if (files.isEmpty()) R.string.empty_hint else R.string.offline_page_hint)))
                for (entry in files) {
                    val title = decode(entry.name.substring(0, entry.name.length - 4))
                    writer.write(InternalDocumentHtml.row("file://${entry.parentFile}/${Uri.encode(entry.name)}", title, "clock"))
                }
                writer.write(InternalDocumentHtml.END)
            }
        } catch (_: Exception) { }
        return "file://${file.path}"
    }

    private fun cached(context: Context, name: String, dirty: Int, content: () -> String): String {
        val file = File(context.filesDir, name)
        if (GeneratedDocumentState.has(dirty) || !file.exists()) {
            try { file.writeText(content(), Charsets.UTF_8); GeneratedDocumentState.clear(dirty) }
            catch (error: Exception) { error.printStackTrace() }
        }
        return "file://${file.path}"
    }

    private fun bookmarkFolderUrl(id: String) = if (id.isEmpty()) "v://bookmarks" else "v://bookmarks/?folder=$id"
    private fun bookmarkTarget(url: String): String = if (url.startsWith("http://", true) || url.startsWith("https://", true) ||
        url.startsWith("file://", true) || url.startsWith("data:", true))
        "v://error/jump?url=" + URLEncoder.encode(url, "UTF-8").replace("+", "%20") else url
    private fun decode(value: String): String = try { URLDecoder.decode(value, "UTF-8") } catch (_: Exception) { value }

    /** o9.a.p/o9.b.l call g6.p.l while materializing bookmark titles. */
    private fun normalizeTitle(value: String?): String? = value?.let { source -> buildString {
        source.forEach { character ->
            when {
                Character.isWhitespace(character) -> if (character != '\r') append(' ')
                character == '\u00a0' -> append(' ')
                else -> append(character)
            }
        }
    } }

    /** cb.b/a2: pinned parent first, folders before bookmarks, and character-wise TinyPinyin order. */
    private fun compareTitles(first: String?, second: String?): Int {
        if (first == null) return if (second == null) 0 else 1
        if (second == null) return -1
        for (index in 0 until minOf(first.length, second.length)) {
            val order = Pinyin.toPinyin(first[index]).uppercase(Locale.ROOT).compareTo(Pinyin.toPinyin(second[index]).uppercase(Locale.ROOT))
            if (order != 0) return order
        }
        return first.length - second.length
    }
}
