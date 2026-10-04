package dev.ujhhgtg.via.home

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** e8.n0.x -> w3.b/g/c/s: touch icons seed the homepage icon directory, not favicon storage. */
internal object TouchIconStore {
    fun download(context: Context, iconUrl: String, pageUrl: String) {
        if (iconUrl.isEmpty() || pageUrl.isEmpty()) return
        val directory = File(context.filesDir, "icon")
        if (directory.exists() && !directory.isDirectory && !directory.delete()) return
        if (!directory.exists() && !directory.mkdirs()) return
        val file = File(directory, HomeFavoriteIcons.key(pageUrl) + ".png")
        if (file.exists()) return
        runCatching { file.createNewFile() }
        var connection: HttpURLConnection? = null
        val downloaded = try {
            connection = URL(iconUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            if (connection.responseCode != HttpURLConnection.HTTP_OK) false
            else {
                connection.inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output, 1024) } }
                true
            }
        } catch (error: Exception) {
            android.util.Log.w("Via", "Cannot download touch icon", error)
            false
        } finally { connection?.disconnect() }
        if (!downloaded) { file.delete(); return }
        // w3.b ignores c's result after a successful download; an undecodable response is retained.
        HomeFavoriteIcons.normalizeStoredFile(file)
    }
}
