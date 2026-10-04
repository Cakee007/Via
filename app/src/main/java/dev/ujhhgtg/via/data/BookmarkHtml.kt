package dev.ujhhgtg.via.data

import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.io.Writer
import java.util.UUID

data class BookmarkCollection(val folders: List<BookmarkFolder>, val items: List<BookmarkItem>)

/** Netscape bookmark interchange, recovered from z8/p.java and z8/e1.java. */
object BookmarkHtml {
    private val attributes = Regex("([A-Za-z_]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
    private val decimalEntity = Regex("&#(\\d+);")

    fun read(input: InputStream): BookmarkCollection = read(input.reader(Charsets.UTF_8))

    fun read(reader: Reader): BookmarkCollection {
        val folders = mutableListOf<BookmarkFolder>()
        val items = mutableListOf<BookmarkItem>()
        val stack = mutableListOf<BookmarkFolder>()
        var item: BookmarkItem? = null
        var title = ""
        var ordering = 0
        val now = System.currentTimeMillis() / 1000
        tokenize(reader) { token ->
            when {
                token.startsWith("<A ", true) -> {
                    val attrs = attributes.findAll(token).associate {
                        it.groupValues[1].lowercase() to it.groupValues.drop(2).firstOrNull(String::isNotEmpty).orEmpty()
                    }
                    val url = attrs["href"].orEmpty().replace("&quot;", "\"").trim()
                    item = if (url.isNotEmpty() && url.length <= 1048576) {
                        val created = attrs["add_date"]?.toLongOrNull()?.takeIf { it != 0L } ?: now
                        BookmarkItem(UUID.randomUUID().toString(), url, null, stack.lastOrNull()?.id.orEmpty(),
                            ++ordering, attrs["last_modified"]?.toLongOrNull()?.takeIf { it != 0L } ?: created, created)
                    } else null
                    title = ""
                }
                token.equals("</A>", true) -> {
                    item?.let { items.add(it.copy(title = decodeTitle(title))) }
                    item = null
                }
                token.startsWith("<H3", true) -> {
                    val attrs = attributes.findAll(token).associate {
                        it.groupValues[1].lowercase() to it.groupValues.drop(2).firstOrNull(String::isNotEmpty).orEmpty()
                    }
                    val created = attrs["add_date"]?.toLongOrNull()?.takeIf { it != 0L } ?: now
                    stack.add(BookmarkFolder(UUID.randomUUID().toString(), null, stack.lastOrNull()?.id.orEmpty(),
                        folders.size + 1, created, attrs["last_modified"]?.toLongOrNull()?.takeIf { it != 0L } ?: created))
                    title = ""
                }
                token.equals("</H3>", true) -> if (stack.isNotEmpty()) {
                    val folder = stack.last().copy(title = decodeTitle(title))
                    stack[stack.lastIndex] = folder
                    folders.add(folder)
                }
                token.equals("</DL>", true) -> {
                    if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                    ordering = 0
                }
                !token.startsWith('<') && (item != null || stack.isNotEmpty()) -> title = token
            }
        }
        return BookmarkCollection(folders, items)
    }

    fun write(output: OutputStream, bookmarks: BookmarkCollection): Int {
        val writer = output.writer(Charsets.UTF_8)
        val count = write(writer, bookmarks)
        writer.flush()
        return count
    }

    fun write(writer: Writer, bookmarks: BookmarkCollection): Int {
        if (bookmarks.folders.isEmpty() && bookmarks.items.isEmpty()) return 0
        writer.write("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<!-- This is an automatically generated file.\n     It will be read and overwritten.\n     DO NOT EDIT! -->\n<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>\n")
        val folders = bookmarks.folders.sortedBy { it.ordering }.groupBy { it.parentFolderId }
        val items = bookmarks.items.sortedBy { it.ordering }.groupBy { it.folderId }
        fun writeFolder(id: String, level: Int) {
            val indent = "  ".repeat(level)
            for ((id, title, _, _, createdAt, lastUpdatedAt) in folders[id].orEmpty()) {
                writer.write("$indent<DT><H3 ADD_DATE=\"$createdAt\"${modified(
                    createdAt,
                    lastUpdatedAt
                )}>${escapeTitle(title)}</H3>\n")
                writer.write("$indent<DL><p>\n")
                writeFolder(id, level + 1)
                writer.write("$indent</DL><p>\n")
            }
            for ((_, url, title, _, _, lastUpdatedAt, createdAt) in items[id].orEmpty()) {
                val url = if (url.length < 2) url else url.replace("\"", "&quot;")
                writer.write("$indent<DT><A HREF=\"$url\" ADD_DATE=\"$createdAt\"${modified(
                    createdAt,
                    lastUpdatedAt
                )}>${escapeTitle(title)}</A>\n")
            }
        }
        writeFolder("", 1)
        writer.write("</DL><p>\n")
        return bookmarks.items.size
    }

    private fun modified(created: Long, updated: Long) = if (created == updated) "" else " LAST_MODIFIED=\"$updated\""
    private fun escapeTitle(value: String?): String {
        if (value == null) return "null"
        if (value.length < 2) return value
        return value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
    }
    private fun decodeTitle(value: String): String {
        val decoded = value.replace("&apos;", "'").replace("&quot;", "\"").replace("&gt;", ">")
            .replace("&lt;", "<").replace("&amp;", "&")
        return decimalEntity.replace(decoded) { match -> match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value }
    }

    // Reading a token at a time keeps large bookmark files out of a single in-memory HTML string.
    private fun tokenize(reader: Reader, accept: (String) -> Unit) {
        val input = reader.buffered()
        val token = StringBuilder()
        var inTag = false
        var quote: Char? = null
        var previous = '\u0000'
        while (true) {
            val value = input.read()
            if (value == -1) break
            val character = value.toChar()
            if (character == '<' && !inTag) {
                if (token.isNotBlank()) accept(token.toString().trim())
                token.setLength(0)
                inTag = true
            }
            token.append(character)
            if (inTag) {
                if ((character == '\"' || character == '\'') && previous != '\\') {
                    quote = if (quote == character) null else quote ?: character
                }
                if (character == '>' && quote == null) {
                    accept(token.toString().trim())
                    token.setLength(0)
                    inTag = false
                }
            }
            previous = character
        }
        if (token.isNotBlank()) accept(token.toString().trim())
    }
}
