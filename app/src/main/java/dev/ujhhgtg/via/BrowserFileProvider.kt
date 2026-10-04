package dev.ujhhgtg.via

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File

/** Read-only grants for downloaded/private files, corresponding to the original FileProvider. */
class BrowserFileProvider : ContentProvider() {
    override fun onCreate() = true
    private fun file(uri: Uri): File {
        val path = uri.getQueryParameter("path") ?: throw IllegalArgumentException("Missing path")
        val file = File(path).canonicalFile
        val context = requireNotNull(context)
        // Original res/xml/c.xml includes external-path for the legacy/user-chosen download directory.
        val roots = listOfNotNull(context.filesDir, context.cacheDir, context.getExternalFilesDir(null), android.os.Environment.getExternalStorageDirectory()).map { it.canonicalFile.path + File.separator }
        require(roots.any { file.path.startsWith(it) }) { "File outside browser storage" }
        return file
    }
    override fun getType(uri: Uri) = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file(uri).extension.lowercase()) ?: "application/octet-stream"
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor { require(mode == "r"); return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY) }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): MatrixCursor {
        val file = file(uri); val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply { addRow(columns.map { when (it) { OpenableColumns.DISPLAY_NAME -> file.name; OpenableColumns.SIZE -> file.length(); else -> null } }) }
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
    companion object { fun uri(context: Context, file: File): Uri = Uri.Builder().scheme("content").authority(context.packageName + ".files").appendPath(file.name).appendQueryParameter("path", file.canonicalPath).build() }
}
