package dev.ujhhgtg.via.downloads

import android.content.Context
import dev.ujhhgtg.via.common.applicationIoScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Gecko hands the app a one-shot response stream. Consume it immediately while Gecko still owns the
 * response, then keep the spooled body alive until the user confirms or cancels the download.
 */
internal object DownloadStreamRegistry {
    internal class Entry(val file: File, val input: InputStream, val ready: CompletableDeferred<Result<Unit>>) {
        @Volatile var closed = false
        fun close() {
            closed = true
            runCatching { input.close() }
            file.delete()
        }
    }

    private val entries = ConcurrentHashMap<String, Entry>()
    private const val PREFIX = "via-download-"

    /** Bodies only live in memory-held entries, so files left by an earlier process are orphans. */
    fun cleanup(context: Context) {
        context.cacheDir.listFiles { file -> file.name.startsWith(PREFIX) && file.name.endsWith(".body") }
            ?.filter { file -> entries.values.none { it.file == file } }?.forEach(File::delete)
    }

    fun register(context: Context, input: InputStream): String {
        val file = File.createTempFile(PREFIX, ".body", context.cacheDir)
        val ready = CompletableDeferred<Result<Unit>>()
        val id = UUID.randomUUID().toString()
        val entry = Entry(file, input, ready)
        entries[id] = entry
        applicationIoScope.launch(Dispatchers.IO) {
            val result = if (entry.closed) {
                Result.failure(IOException("Download stream was cancelled"))
            } else runCatching {
                entry.input.use { source -> entry.file.outputStream().use { target -> source.copyTo(target, 64 * 1024) } }
            }
            ready.complete(result.map { Unit })
            if (entry.closed || result.isFailure) {
                entry.close()
                entries.remove(id, entry)
            }
        }
        return id
    }

    fun take(id: String): Source? = entries.remove(id)?.let { Source(it) }

    fun close(id: String) {
        entries.remove(id)?.close()
    }

    class Source internal constructor(private val entry: Entry) {
        suspend fun await(): File {
            entry.ready.await().getOrThrow()
            return entry.file
        }

        fun close() = entry.close()
    }
}
