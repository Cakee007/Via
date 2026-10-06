package dev.ujhhgtg.via.extensions

import android.content.Context
import android.net.Uri
import java.io.File

/** Extension packages that pass through Via: link responses and picked files. */
object ExtensionFiles {
    const val MIME = "application/x-xpinstall"

    /** A link response is an extension package by its MIME type, or by a `.xpi` path from a web server. */
    fun isPackage(url: String, mime: String?, disposition: String?): Boolean {
        if (mime.equals(MIME, ignoreCase = true)) return true
        val name = disposition?.let(::dispositionName) ?: url.substringBefore('#').substringBefore('?').substringAfterLast('/')
        val web = url.startsWith("https://", true) || url.startsWith("http://", true)
        val generic = mime.isNullOrEmpty() || mime.equals("application/octet-stream", true) || mime.equals("application/zip", true)
        return web && generic && name.endsWith(".xpi", ignoreCase = true)
    }

    private fun dispositionName(disposition: String): String? =
        Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)", RegexOption.IGNORE_CASE).find(disposition)?.groupValues?.get(1)

    /** Copies a picked document into the cache, since Gecko installs from `file://` URIs. Worker thread. */
    fun copy(context: Context, uri: Uri): File {
        val file = File.createTempFile("via-xpi-", ".xpi", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                ?: throw java.io.IOException("Cannot read $uri")
        } catch (error: Throwable) { file.delete(); throw error }
        return file
    }

    /** Removes package copies left by an earlier process. */
    fun cleanup(context: Context) {
        context.cacheDir.listFiles { file -> file.name.startsWith("via-xpi-") }?.forEach(File::delete)
    }
}
