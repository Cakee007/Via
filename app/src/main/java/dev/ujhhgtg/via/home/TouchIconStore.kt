package dev.ujhhgtg.via.home

import android.content.Context
import dev.ujhhgtg.via.common.httpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.CancellationException
import java.io.File

/** e8.n0.x -> w3.b/g/c/s: touch icons seed the homepage icon directory, not favicon storage. */
internal object TouchIconStore {
    suspend fun download(context: Context, iconUrl: String, pageUrl: String) {
        if (iconUrl.isEmpty() || pageUrl.isEmpty()) return
        val directory = File(context.filesDir, "icon")
        if (directory.exists() && !directory.isDirectory && !directory.delete()) return
        if (!directory.exists() && !directory.mkdirs()) return
        val file = File(directory, HomeFavoriteIcons.key(pageUrl) + ".png")
        if (file.exists()) return
        runCatching { file.createNewFile() }
        val downloaded = try {
            httpClient.prepareGet(iconUrl).execute { response ->
                if (response.status != HttpStatusCode.OK) false
                else { file.outputStream().use { output -> response.bodyAsChannel().copyTo(output) }; true }
            }
        } catch (error: CancellationException) { file.delete(); throw error }
        catch (error: Exception) {
            android.util.Log.w("Via", "Cannot download touch icon", error)
            false
        }
        if (!downloaded) { file.delete(); return }
        // w3.b ignores c's result after a successful download; an undecodable response is retained.
        HomeFavoriteIcons.normalizeStoredFile(file)
    }
}
