package dev.ujhhgtg.via.downloads

import android.content.Context
import android.os.SystemClock
import android.webkit.CookieManager
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.request
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.io.IOException
import java.io.InterruptedIOException
import java.net.MalformedURLException
import java.net.URL
import java.net.UnknownHostException
import kotlin.math.abs

/** m5.e/d and i5.b: probe first, persisted range chunks, bounded retries and one-connection fallback. */
internal class DownloadTransfer(
    private val context: Context,
    initial: DownloadRecord,
    private val repository: DownloadRepository,
    private val control: DownloadControl,
    private val update: (DownloadRecord, Long) -> Unit,
    private val observeProgress: Boolean = true,
) {
    private var record = initial
    private var chunks = emptyList<DownloadChunk>()
    private var progressTime = SystemClock.elapsedRealtime()
    private var progressBytes = 0L
    private var speed = 0L
    init {
        control.pauseAction = {
            control.paused = true
            // m5.e.a reports pause immediately while probe/output preparation has no chunk workers.
            if (!control.hasChunkWorkers && !record.isComplete && !record.isFailed) status(DownloadState.PAUSED)
        }
    }

    suspend fun run(): DownloadRecord {
        try {
            if (record.isComplete) return record
            checkStopped()
            if (record.flags and 2 == 0) { status(DownloadState.CONNECTING); probe() }
            status(DownloadState.DOWNLOADING)
            var failure = transferChunks()
            if (failure != null && failure.code != 1 && failure.code != 32) {
                val nonResumable = failure.code == 24 || failure.code == 416
                val fallback = nonResumable && !(record.flags and 1 != 0 && record.chunks == 1) ||
                    failure.code in 400..499 && record.chunks != 1
                if (fallback) {
                    record = record.copy(chunks = 1, flags = if (nonResumable) record.flags and 1.inv() else record.flags)
                    repository.clearChunks(record.id)
                    failure = transferChunks()
                }
            }
            if (failure != null) throw failure
            status(DownloadState.COMPLETE)
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            val failure = if (error is DownloadFailure) error else DownloadFailure(if (control.paused) 1 else 20, error)
            val state = when (failure.code) { 1 -> DownloadState.PAUSED; 32 -> DownloadState.WAITING_NETWORK; else -> DownloadState.FAILED }
            val previousState = record.state
            record = record.copy(state = state, errorMessage = if (state == DownloadState.FAILED) failure.message else record.errorMessage)
            if (!control.deleted && state != previousState) update(record, speed)
        }
        return record
    }

    private fun status(value: Int) { if (record.state == value) return; record = record.copy(state = value); if (!control.deleted) update(record, speed) }
    private fun checkStopped() { if (control.paused || control.deleted || Thread.currentThread().isInterrupted) throw DownloadFailure(1) }
    private fun checkNetwork() { if (!DownloadNetwork.isAvailable(context)) throw DownloadFailure(32) }

    /** m5.e.j advances Referer/User-Agent choices only after receiving an HTTP response. */
    private suspend fun probe() {
        checkNetwork()
        val original = record.url ?: throw DownloadFailure(21)
        val referer = record.headers["Referer"] ?: record.headers["referer"]
        val agent = record.headers["User-Agent"]
        val referers = if (!referer.isNullOrEmpty()) if (referer == original) listOf(null, referer) else listOf(null, original, referer)
            else listOf(original, null)
        val agents = if (agent == null) listOf(null) else listOf(null, agent)
        var referenceIndex = referers.lastIndex
        var agentIndex = agents.lastIndex
        var error: DownloadFailure? = null
        while (referenceIndex >= 0 && agentIndex >= 0) {
            checkStopped()
            val ref = referers[referenceIndex]
            val ua = agents[agentIndex]
            record = record.copy(url = original)
            try {
                val accepted = connection(original, headers = { http ->
                    error = null
                    record.headers.forEach { (name, value) -> if (!name.equals("Referer", true) && !name.equals("User-Agent", true)) http.header(name, value) }
                    ref?.let { http.headers[HEADER_REFERER] = it }; ua?.let { http.headers[HEADER_USER_AGENT] = it }
                }, redirected = { record = record.copy(url = it) }) { http ->
                    checkStopped()
                    referenceIndex--
                    if (referenceIndex < 0) { agentIndex--; if (agentIndex >= 0) referenceIndex = referers.lastIndex }
                    if (http.status.value != 200 && http.status.value != 206) throw DownloadFailure.http(http)
                    acceptMetadata(http, ref, ua)
                }
                if (accepted) return
            } catch (failure: DownloadFailure) {
                error = failure
                if (control.paused || control.deleted || failure.code == 1 || failure.code == 21 || failure.code == 22) throw failure
            }
        }
        error?.let { throw it }
    }

    private fun acceptMetadata(http: HttpResponse, referer: String?, userAgent: String?): Boolean {
        var mime = http.headers["Content-Type"]?.let { if (';' in it) it.substringBefore(';').trim().lowercase(java.util.Locale.ROOT) else it }
        var extension = DownloadMimeTypes.extension(mime)
        if (mime == null || mime == "application/octet-stream") {
            val name = DownloadFiles.name(http.request.url.toString(), http.headers["Content-Disposition"], null)
            if ('.' in name) { extension = name.substringAfterLast('.'); mime = DownloadMimeTypes.mime(extension, mime) }
        }
        val html = mime == "text/html" || extension == "html" || extension == "htm"
        if (html != (record.mimeType == "text/html")) return false
        var name = record.name
        if (record.flags and 4 != 0 && mime != null && !mime.equals(record.mimeType, true)) {
            if (!extension.isNullOrEmpty() && extension != "bin") name = name.substringBeforeLast('.', name) + ".$extension"
            record = record.copy(name = name, mimeType = mime)
        }
        var total = if (http.headers["Transfer-Encoding"] == null) http.headers["Content-Length"]?.toLongOrNull() ?: -1 else -1
        if (total == -1L) total = DownloadNetwork.contentRangeLength(http.headers["Content-Range"])
        var resumable = http.headers["Accept-Ranges"].equals("bytes", true) || http.headers["Content-Range"] != null
        if (html) { total = -1; resumable = false }
        val headers = record.headers.toMutableMap()
        http.headers["ETag"]?.takeIf(String::isNotEmpty)?.let { headers["ETag"] = it }
        if (referer == null) headers.remove("Referer") else headers["Referer"] = referer
        if (userAgent == null) headers.remove("User-Agent") else headers["User-Agent"] = userAgent
        val count = if (total > 0 && resumable) minOf(maxOf(1, record.chunks), maxOf(1, kotlin.math.ceil(total.toFloat() / 1_048_576f).toInt())) else 1
        if (total <= 0) resumable = false
        record = record.copy(totalSize = total, headers = headers, chunks = count,
            flags = ((record.flags or 2) and 1.inv()) or if (resumable) 1 else 0)
        update(record, speed)
        return true
    }

    private suspend fun transferChunks(): DownloadFailure? {
        checkStopped()
        chunks = repository.chunks(record.id).ifEmpty {
            val ranges = if (record.totalSize > 0 && record.flags and 1 != 0) {
                val width = record.totalSize / record.chunks
                List(record.chunks) { index -> val start = index * width
                    DownloadChunk(taskId = record.id, start = start, length = if (index == record.chunks - 1) record.totalSize - start else width) }
            } else listOf(DownloadChunk(taskId = record.id, start = 0, length = 0))
            repository.createChunks(ranges)
        }
        try {
            DownloadOutput.open(context, record).use { sink ->
                val expected = if (record.flags and 1 != 0) record.totalSize else 0
                if (sink.length() != expected) {
                    if (sink.length() <= 0) repository.resetChunks(record.id)
                    sink.resize(expected); sink.sync()
                }
            }
        } catch (failure: Exception) { return DownloadFailure(12, failure) }
        control.hasChunkWorkers = true
        var remaining = chunks.toMutableList()
        var attempts = remaining.size * 2
        var connections = remaining.size
        var failure: DownloadFailure? = null
        progressTime = SystemClock.elapsedRealtime(); progressBytes = record.downloadedSize
        while (remaining.isNotEmpty() && attempts > 0) {
            checkStopped()
            val waiting = remaining.drop(connections).toMutableList()
            val tasks = remaining.take(connections)
            remaining.clear()
            var terminal = false
            failure = null
            coroutineScope {
                val workers = tasks.map { chunk ->
                    async(Dispatchers.IO) {
                        // The original treated an unexpected worker exception like a finished chunk.
                        try { transferChunk(chunk) } catch (error: CancellationException) { throw error } catch (_: Exception) { null }
                    }
                }
                workers.forEachIndexed { index, worker ->
                    if (terminal) { worker.cancel(); return@forEachIndexed }
                    val result = worker.await()
                    if (result != null) {
                        attempts--; connections = maxOf(1, connections - 1); failure = result
                        if (result.code in setOf(503, 500, 20, 31)) remaining += tasks[index] else terminal = true
                    }
                }
            }
            if (terminal) return failure
            remaining += waiting
            if (remaining.isNotEmpty() && attempts > 0) {
                delay(300)
                if (failure == null) failure = DownloadFailure(30)
            }
        }
        // Successful retry consumes every chunk; the previous error must not survive the finished round.
        return if (remaining.isEmpty()) null else failure
    }

    private suspend fun transferChunk(chunk: DownloadChunk): DownloadFailure? {
        if (chunk.length > 0 && chunk.downloaded == chunk.length) return null
        if (record.flags and 1 == 0) chunk.downloaded = 0
        val resumed = chunk.downloaded > 0
        return try {
            checkNetwork()
            connection(record.url ?: throw DownloadFailure(21), headers = { http ->
                var etag: String? = null
                record.headers.forEach { (key, value) ->
                    when {
                        key.equals("ETag", true) -> etag = value
                        key.equals("Referer", true) -> http.headers[HEADER_REFERER] = value
                        else -> http.header(key, value)
                    }
                }
                http.headers["Accept-Encoding"] = "identity"; http.headers["Connection"] = "close"
                if (resumed && etag != null) http.header("If-Match", etag)
                if (chunk.length > 0) http.header("Range", "bytes=${chunk.begin}-${chunk.end}")
            }) { http ->
                if (http.status.value != 200 && http.status.value != 206) throw DownloadFailure.http(http)
                if (http.status.value == 200 && resumed) throw DownloadFailure(24)
                readChunk(http, chunk)
            }
            null
        } catch (failure: DownloadFailure) { failure }
    }

    /** m5.d.j is verified against smali: stream acquisition, reading and output errors differ. */
    private suspend fun readChunk(http: HttpResponse, chunk: DownloadChunk) {
        checkStopped()
        val requiresLength = chunk.length <= 0 && !http.headers["Connection"].equals("close", true) && !http.headers["Transfer-Encoding"].equals("chunked", true)
        val length = http.headers["Content-Length"]?.toLongOrNull()
        if (length != null && chunk.length > 0 && length > chunk.length) throw DownloadFailure(24)
        if (requiresLength) { if (length == null || length <= 0 || chunk.start != 0L) throw DownloadFailure(24); chunk.length = length }
        val input = http.bodyAsChannel()
        var sink: DownloadOutput? = null
        try {
            val output = try { DownloadOutput.open(context, record) }
                catch (failure: IOException) { throw DownloadFailure(12, failure) }
                catch (failure: IllegalArgumentException) { throw DownloadFailure(12, failure) }
            sink = output
            try { output.seek(chunk.begin) } catch (failure: IOException) { throw DownloadFailure(12, failure) }
            val buffer = ByteArray(4096)
            var last = SystemClock.elapsedRealtime()
            var flushedBytes = chunk.downloaded
            fun report(forced: Boolean) {
                val now = SystemClock.elapsedRealtime()
                if (forced || now - last > 1000) {
                    try { if (chunk.downloaded != flushedBytes) output.sync() }
                    catch (failure: IOException) { throw DownloadFailure(12, failure) }
                    flushedBytes = chunk.downloaded; last = now
                    publishProgress(chunk)
                }
            }
            while (true) {
                checkStopped()
                val count = try { input.readAvailable(buffer) } catch (failure: IOException) { throw DownloadFailure(20, failure) }
                if (count == -1) break
                try { output.write(buffer, 0, count) } catch (failure: IOException) { throw DownloadFailure(12, failure) }
                chunk.downloaded += count
                report(false)
                if (chunk.length > 0 && chunk.downloaded >= chunk.length) break
            }
            // On pause/read failure the original closes the stream without a forced checkpoint.
            report(true)
            if (chunk.length > 0 && abs(chunk.downloaded - chunk.length) > 1) throw DownloadFailure(31)
        } finally {
            try { sink?.close() } catch (_: IOException) { }
        }
    }

    /** h5.a writes the notifying chunk only after the task-level one-second/completion gate. */
    @Synchronized private fun publishProgress(chunk: DownloadChunk) {
        if (!observeProgress) return // m5.f.b uses h5.b: no task/chunk persistence callbacks.
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - progressTime
        if (elapsed <= 1000 && (chunk.downloaded != chunk.length || record.downloadedSize == record.totalSize)) return
        progressTime = now
        val downloaded = chunks.sumOf { it.downloaded }
        val instant = (if (downloaded < progressBytes) downloaded else downloaded - progressBytes) * 1000 / maxOf(100, elapsed)
        speed = if (record.totalSize > 0 && downloaded >= record.totalSize) -1 else if (speed == 0L) instant else (speed * 3 + instant) / 4
        record = record.copy(downloadedSize = downloaded)
        progressBytes = downloaded
        repository.saveChunk(chunk)
        if (!control.deleted) update(record, speed)
    }

    /** i5.b applies fresh cookies per redirect and caps the original redirect traversal at five requests. */
    private suspend fun <T> connection(initial: String, headers: (HttpRequestBuilder) -> Unit, redirected: (String) -> Unit = {},
        block: suspend (HttpResponse) -> T): T {
        var url = initial
        repeat(5) {
            val target = try { URL(url) } catch (failure: MalformedURLException) { throw DownloadFailure(21, failure) }
            // Assigned only when the hop is a redirect; otherwise the block's result is returned below.
            var next: String? = null
            try {
                val result = DownloadNetwork.client.prepareGet(url) {
                    this.headers["Accept-Encoding"] = "identity"
                    runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()?.let { this.headers["Cookie"] = it }
                    headers(this)
                }.execute { http ->
                    when (http.status.value) {
                        301, 302, 303, 307, 308 -> {
                            next = try { URL(target, http.headers["Location"]).toString() }
                                catch (failure: MalformedURLException) { throw DownloadFailure(21, failure) }
                            null
                        }
                        else -> Box(block(http))
                    }
                }
                if (result != null) return result.value
            } catch (failure: DownloadFailure) { throw failure }
            catch (failure: IOException) { throw connectionFailure(failure) }
            url = next ?: throw DownloadFailure(21)
            redirected(url)
        }
        throw DownloadFailure(25)
    }

    private class Box<T>(val value: T)
    private companion object {
        const val HEADER_REFERER = "Referer"
        const val HEADER_USER_AGENT = "User-Agent"
    }

    private fun connectionFailure(failure: IOException) = DownloadFailure(when (failure) {
        is InterruptedIOException -> 1
        is UnknownHostException -> 21
        else -> 20
    }, failure)

}

internal class DownloadControl(val pausable: Boolean = true) {
    @Volatile var paused = false
    @Volatile var deleted = false
    @Volatile var hasChunkWorkers = false
    var pauseAction: (() -> Unit)? = null
}
internal class DownloadFailure(val code: Int, cause: Throwable? = null, message: String? = null) : IOException(message ?: when (code) {
    1 -> "User Cancel"; 2 -> "Unsupported Protocol"; 11 -> "Can Not Create File"; 12 -> "Can Not Write File"
    20 -> "Http Data Error"; 21 -> "Http Bad Request"; 22 -> "Certificate Error"; 23 -> "Network Timeout"
    24 -> "Non Resumable"; 25 -> "Too Many Redirects"; 30 -> "Task Submit Failed"; 31 -> "Chunk Verify Failed"
    32 -> "No Network Connection"; 40 -> "Decode Error"; else -> null
}, cause) {
    companion object { fun http(http: HttpResponse) = DownloadFailure(http.status.value, message = "HTTP Error: ${http.status.value} ${http.status.description}") }
}
