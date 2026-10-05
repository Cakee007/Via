package dev.ujhhgtg.via.browser.filter

import dev.ujhhgtg.via.common.httpClient
import dev.ujhhgtg.via.data.BrowserPreferences
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.LineNumberReader
import java.io.StringReader
import java.util.UUID

/** b5.c.d/e/c: original HTTP policy, fallback hosts and first-20-line subscription metadata. */
object FilterSubscriptionUpdater {
    suspend fun update(store: FilterStore, record: FilterStore.Subscription): FilterStore.Subscription {
        val content = try { fetch(record.url) }
        catch (first: CancellationException) { throw first }
        catch (first: Exception) {
            val fallback = fallbackUrl(record.url) ?: throw first
            fetch(fallback)
        }
        val file = record.filePath?.let(::File) ?: File(store.root, UUID.randomUUID().toString() + ".txt")
        file.parentFile?.mkdirs()
        file.writeText(content)
        val metadata = mutableMapOf<String, String>()
        val size = LineNumberReader(StringReader(content)).use { reader ->
            for (index in 0 until 20) {
                val line = reader.readLine() ?: break
                if (line.startsWith('!')) for (key in listOf("Title", "Homepage", "License")) {
                    if (line.contains("$key:")) metadata[key] = line.substringAfter("$key:").trim()
                }
            }
            reader.skip(Long.MAX_VALUE)
            reader.lineNumber + 1
        }
        return record.copy(filePath = file.path, title = metadata["Title"] ?: record.title,
            homepage = metadata["Homepage"] ?: record.homepage, license = metadata["License"] ?: record.license, size = size)
    }

    /** sb.g: run on the app's update worker, with the same interval and per-file cutoff. */
    suspend fun updateDue(store: FilterStore, preferences: BrowserPreferences, now: Long = System.currentTimeMillis()): Int {
        val interval = preferences.getLong("updater_filter_subscriptions")
        val cutoff = now - interval
        if (interval < 3_600_000L || preferences.getLong("updated_filter_subscriptions") >= cutoff) return 0
        val records = store.readSubscriptions()
        if (records.isEmpty()) return 0
        var count = 0
        val updated = records.map { record ->
            if (!record.enabled || (record.filePath?.let { File(it).lastModified() } ?: 0L) >= cutoff) record
            else try { update(store, record).also { count++ } }
            catch (error: CancellationException) { throw error } catch (_: Exception) { record }
        }
        store.writeSubscriptions(updated)
        preferences.putLong("updated_filter_subscriptions", System.currentTimeMillis())
        return count
    }

    private suspend fun fetch(url: String): String {
        val response = httpClient.get(url) {
            timeout { connectTimeoutMillis = 5000 }
            header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.116 Safari/537.36")
        }
        val type = response.headers[HttpHeaders.ContentType]
        check(response.status.value == 200 && type?.trim()?.startsWith("text/plain") == true) { "HTTP ${response.status.value}: $type" }
        return response.bodyAsBytes().toString(Charsets.UTF_8)
    }

    private fun fallbackUrl(url: String): String? = when {
        url.startsWith("https://cdn.jsdelivr.net/") -> url.replaceFirst("https://cdn.jsdelivr.net/", "https://fastly.jsdelivr.net/")
        url.startsWith("https://raw.githubusercontent.com/") -> url.removePrefix("https://raw.githubusercontent.com/").split('/', limit = 4)
            .takeIf { it.size == 4 }?.let { "https://fastly.jsdelivr.net/gh/${it[0]}/${it[1]}@${it[2]}/${it[3]}" }
        url.startsWith("https://github.com/") -> "https://fastly.jsdelivr.net/gh/" + url.removePrefix("https://github.com/").replace("/blob/", "@")
        url.startsWith("https://unpkg.com/") -> "https://fastly.jsdelivr.net/npm/" + url.removePrefix("https://unpkg.com/")
        else -> null
    }
}
