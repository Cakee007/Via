package dev.ujhhgtg.via.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File

/** x5.e, z8.b/c1 and w9.c, shared by the two original customization image pickers. */
internal object HomeCustomizationMedia {
    fun pickerIntent(context: Context): Intent = if (Build.VERSION.SDK_INT >= 36) {
        ActivityResultContracts.PickVisualMedia().createIntent(context,
            PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly).build())
    } else Intent(Intent.ACTION_PICK).setType("image/*")

    /** x5.e.e delegates API 36 results to the contract, including its ClipData fallback. */
    fun selectedImage(result: Int, data: Intent?): Uri? = if (Build.VERSION.SDK_INT >= 36) {
        ActivityResultContracts.PickVisualMedia().parseResult(result, data)
    } else data?.data?.takeIf { result == Activity.RESULT_OK }

    fun importImage(context: Context, uri: Uri, basename: String): String? {
        val path = originalPath(context, uri)?.takeIf(String::isNotEmpty) ?: return null
        val dot = path.lastIndexOf('.')
        val suffix = if (dot >= 0) path.substring(dot) else ".png"
        val directory = context.getExternalFilesDir("content") ?: File(context.filesDir, "content")
        if (!directory.exists() && !directory.mkdirs()) return null
        val target = File(directory, basename + suffix)
        if (uri.authority == null) return null
        context.contentResolver.openInputStream(uri)?.use { input ->
            val parent = target.parentFile ?: return null
            if (!parent.exists() && !parent.mkdirs()) return null
            if (target.exists() && !target.delete()) return null
            target.outputStream().use { output -> input.copyTo(output, 8192); output.flush() }
        } ?: return null
        return target.absolutePath
    }

    private fun originalPath(context: Context, uri: Uri): String? {
        val value = uri.toString()
        if (value.startsWith("file://")) return value.substring(7)
        return try {
            val document = DocumentsContract.isDocumentUri(context, uri)
            val target = if (document) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else uri
            val id = if (document) DocumentsContract.getDocumentId(uri).split(':').getOrNull(1) ?: return null else null
            context.contentResolver.query(target, arrayOf("_data"), if (document) "_id=?" else null,
                id?.let { arrayOf(it) }, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow("_data")) else null
            }
        } catch (error: Exception) {
            android.util.Log.w("ViaCustomization", "Unable to resolve selected image", error)
            null
        }
    }

    private fun cacheFile(context: Context, key: String): File =
        File(context.getExternalFilesDir("settings") ?: File(context.filesDir, "settings"), HomeDesign.md5(key))

    fun readLogoCache(context: Context, key: String): String = runCatching {
        cacheFile(context, key).bufferedReader(Charsets.UTF_8).useLines { lines -> buildString { lines.forEach { append(it).append('\n') } } }
    }.getOrDefault("")

    fun writeLogoCache(context: Context, key: String, value: String) {
        val file = cacheFile(context, key)
        file.parentFile?.mkdirs()
        file.writeText(value, Charsets.UTF_8)
    }
}
