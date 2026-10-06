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
    /** How a staged body ended: true when saved, false when it failed, null when it was discarded. */
    fun interface Outcome { fun done(saved: Boolean?, bytes: Long) }

    internal class Entry(val file: File, val input: InputStream, val ready: CompletableDeferred<Result<Unit>>,
        private val outcome: Outcome?) {
        @Volatile var closed = false
        private val reported = java.util.concurrent.atomic.AtomicBoolean(false)
        fun finish(saved: Boolean?, bytes: Long) { if (reported.compareAndSet(false, true)) outcome?.done(saved, bytes) }
        fun close() {
            closed = true
            finish(null, 0)
            runCatching { input.close() }
            file.delete()
        }
    }

    private val entries = ConcurrentHashMap<String, Entry>()
    private const val PREFIX = "via-download-"

    /** Bodies only live in memory-held entries, so files left by an earlier process are orphans. */
    fun cleanup(context: Context) {
        context.cacheDir.listFiles { file -> file.name.startsWith(PREFIX) }
            ?.filter { file -> entries.values.none { it.file == file } }?.forEach(File::delete)
    }

    /**
     * [suffix] names the spooled file, for consumers that look at it, such as extension installs.
     * [outcome] hears once whether the body was saved, failed, or was discarded. Any thread.
     */
    fun register(context: Context, input: InputStream, suffix: String = ".body", outcome: Outcome? = null): String {
        val file = File.createTempFile(PREFIX, suffix, context.cacheDir)
        val ready = CompletableDeferred<Result<Unit>>()
        val id = UUID.randomUUID().toString()
        val entry = Entry(file, input, ready, outcome)
        entries[id] = entry
        applicationIoScope.launch(Dispatchers.IO) {
            val result = if (entry.closed) {
                Result.failure(IOException("Download stream was cancelled"))
            } else runCatching {
                entry.input.use { source -> entry.file.outputStream().use { target -> source.copyTo(target, 64 * 1024) } }
            }
            ready.complete(result.map { Unit })
            if (result.isFailure) entry.finish(false, 0)
            if (entry.closed || result.isFailure) {
                entry.close()
                entries.remove(id, entry)
            }
        }
        return id
    }

    fun take(id: String): Source? = entries.remove(id)?.let { Source(it) }

    /** The spooled body once it is complete, leaving the entry registered; null when it failed or was closed. */
    suspend fun file(id: String): File? {
        val entry = entries[id] ?: return null
        return entry.file.takeIf { entry.ready.await().isSuccess && !entry.closed }
    }

    fun close(id: String) {
        entries.remove(id)?.close()
    }

    class Source internal constructor(private val entry: Entry) {
        suspend fun await(): File {
            entry.ready.await().getOrThrow()
            return entry.file
        }

        /** Reports the transfer's result before [close] releases the body. */
        fun finish(saved: Boolean, bytes: Long) = entry.finish(saved, bytes)
        fun close() = entry.close()
    }
}
