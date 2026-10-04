package dev.ujhhgtg.via.skins

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/** mb.c: identity and display name are the extracted directory's name. */
class SkinEntry(val name: String, val title: String = name, val modified: Long = 0L)

/** lb.k / mb.b: ZIP imports and the original newest-directory-first skin list. */
class SkinRepository(context: Context) {
    val directory: File = directory(context)
    private val resolver = context.applicationContext.contentResolver

    fun list(): List<SkinEntry> = directory.listFiles().orEmpty()
        .filter(File::isDirectory)
        .map { SkinEntry(it.name, it.name, it.lastModified()) }
        .sortedByDescending(SkinEntry::modified)

    fun delete(name: String): Boolean = name.isNotEmpty() && deleteTree(File(directory, name))

    /** lb.k.f3 accepts a known size in [0, 12 MiB]; the extension is not a validator. */
    fun importSkin(uri: Uri): SkinEntry? {
        val name = displayName(uri) ?: return null
        val size = size(uri)
        if (size < 0L || size > MAX_BYTES) return null
        val dot = name.lastIndexOf('.')
        val stem = if (dot >= name.length - 5) name.substring(0, dot) else name
        val destination = File(directory, stem)
        val input = try {
            if (uri.scheme == ContentResolver.SCHEME_FILE) uri.path?.let { File(it).inputStream() }
            else resolver.openInputStream(uri)
        } catch (error: Exception) {
            android.util.Log.w("ViaSkins", "Unable to open skin", error)
            null
        }
        if (!extract(input, destination) || !destination.exists()) return null
        return SkinEntry(destination.name, destination.name, destination.lastModified())
    }

    private fun displayName(uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) return uri.lastPathSegment
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: uri.lastPathSegment
        } catch (error: Exception) {
            android.util.Log.w("ViaSkins", "Unable to read skin name", error)
            uri.lastPathSegment
        }
    }

    private fun size(uri: Uri): Long {
        if (uri.scheme == ContentResolver.SCHEME_FILE) return uri.path?.let { File(it).length() } ?: 0L
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst()) it.getLong(0) else -1L
            } ?: -1L
        } catch (error: Exception) {
            android.util.Log.w("ViaSkins", "Unable to read skin size", error)
            -1L
        }
    }

    /** mb.b.d stops at the first failed child; a missing target counts as deleted. */
    private fun deleteTree(file: File): Boolean {
        if (!file.exists()) return true
        if (file.isDirectory) for (child in file.listFiles().orEmpty()) if (!deleteTree(child)) return false
        return file.delete()
    }

    companion object {
        private const val MAX_BYTES = 12L * 1024L * 1024L

        fun directory(context: Context): File =
            (context.getExternalFilesDir("skins") ?: File(context.filesDir, "skins")).apply { if (!exists()) mkdirs() }

        /** z8.e4.a merges entries into an existing skin and preserves each file's ZIP timestamp. */
        internal fun extract(input: InputStream?, destination: File): Boolean {
            if (input == null) return false
            return try {
                ZipInputStream(input).use { zip ->
                    val root = destination.canonicalFile
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val target = File(root, entry.name)
                        val canonical = target.canonicalPath
                        if (canonical != root.path && !canonical.startsWith(root.path + File.separator)) continue
                        if (entry.isDirectory) {
                            target.mkdirs()
                            continue
                        }
                        if (!target.exists()) { target.parentFile?.mkdirs(); target.createNewFile() }
                        target.outputStream().use { output ->
                            val buffer = ByteArray(1024)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count == -1) break
                                output.write(buffer, 0, count)
                                output.flush()
                            }
                        }
                        if (entry.time > 0L) target.setLastModified(entry.time)
                    }
                }
                true
            } catch (error: Exception) {
                android.util.Log.w("ViaSkins", "Unable to extract skin", error)
                false
            }
        }
    }
}
