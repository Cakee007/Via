package dev.ujhhgtg.via.fonts

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException

/** s9.a uses object identity for RecyclerView diffs, including after a file is renamed. */
class FontEntry(val path: String) {
    val name: String = if (path.isEmpty()) "" else File(path).name
}

/** s9.d's file store and hb.j2.g3's import result codes. */
class FontRepository(private val directory: File) {
    data class ImportResult(val error: Int, val font: FontEntry? = null)

    fun list(): List<FontEntry> = buildList {
        add(FontEntry(""))
        // s9.d.a deliberately uses case-sensitive suffixes; import accepts either case.
        val files = directory.listFiles { _, name -> EXTENSIONS.any { name.endsWith(".$it") } }.orEmpty()
        files.sortWith { left, right -> left.name.compareTo(right.name, ignoreCase = true) }
        files.forEach { add(FontEntry(it.absolutePath)) }
    }

    fun delete(name: String): Boolean = name.isNotEmpty() && File(directory, name).let { it.exists() && it.delete() }

    fun rename(oldName: String, newName: String): FontEntry? {
        if (oldName.isEmpty() || newName.isEmpty() || oldName == newName) return null
        val source = File(directory, oldName)
        if (!source.isFile) return null
        val target = File(directory, availableName(cleanName(newName)))
        return if (source.renameTo(target)) FontEntry(target.absolutePath) else null
    }

    fun importFont(resolver: ContentResolver, uri: Uri): ImportResult {
        val name = displayName(resolver, uri).orEmpty()
        val extension = name.substringAfterLast('.', "")
        if (EXTENSIONS.none { extension.equals(it, ignoreCase = true) }) return ImportResult(1)
        if (size(resolver, uri) > MAX_BYTES) return ImportResult(2)
        val target = File(directory, name)
        if (target.exists() && !target.delete()) return ImportResult(3)
        if (!copy(resolver, uri, target)) return ImportResult(4)
        // s9.d.c only checks storage, not whether Android can construct a Typeface.
        if (!target.exists()) return ImportResult(5)
        return ImportResult(0, FontEntry(target.absolutePath))
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) return uri.lastPathSegment
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: uri.lastPathSegment
        } catch (error: Exception) {
            android.util.Log.w("ViaFonts", "Unable to read font name", error)
            uri.lastPathSegment
        }
    }

    private fun size(resolver: ContentResolver, uri: Uri): Long {
        if (uri.scheme == ContentResolver.SCHEME_FILE) return uri.path?.let { File(it).length() } ?: 0L
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst()) it.getLong(0) else -1L
            } ?: -1L
        } catch (error: Exception) {
            android.util.Log.w("ViaFonts", "Unable to read font size", error)
            -1L
        }
    }

    /** z8.c1.b/e: copy errors preserve any partial file and return error 4. */
    private fun copy(resolver: ContentResolver, uri: Uri, target: File): Boolean {
        return uri.authority != null && try {
            resolver.openInputStream(uri)?.use { input ->
                val parent = target.parentFile ?: return false
                if (!parent.exists() && !parent.mkdirs()) return false
                if (target.exists() && !target.delete()) return false
                target.outputStream().use { output -> input.copyTo(output, 8192); output.flush() }
                true
            } ?: false
        } catch (error: IOException) {
            android.util.Log.w("ViaFonts", "Unable to copy font", error)
            false
        }
    }

    /** z8.c1.K/L: collisions append a counter before the last extension. */
    private fun availableName(name: String): String {
        val dot = name.lastIndexOf('.')
        val stem = if (dot < 0) name else name.substring(0, dot)
        val suffix = if (dot < 0) "" else name.substring(dot)
        var candidate = name
        var index = 0
        while (File(directory, candidate).exists()) candidate = "$stem (${++index})$suffix"
        return candidate
    }

    companion object {
        private const val MAX_BYTES = 50L * 1024L * 1024L
        private val EXTENSIONS = arrayOf("ttf", "otf", "woff", "woff2")

        /** z8.c1.n: replace reserved characters and cap the full UTF-8 filename at 200 bytes. */
        internal fun cleanName(name: String): String {
            val safe = name.trim { it <= ' ' }.replace(Regex("[/\\\\:*?\"<>|]"), "-").take(200)
            if (safe.toByteArray(Charsets.UTF_8).size <= 200) return safe
            var low = 0
            var high = safe.length
            while (low < high) {
                val middle = (low + high + 1) / 2
                if (safe.substring(0, middle).toByteArray(Charsets.UTF_8).size <= 200) low = middle
                else high = middle - 1
            }
            return safe.substring(0, low)
        }
    }
}
