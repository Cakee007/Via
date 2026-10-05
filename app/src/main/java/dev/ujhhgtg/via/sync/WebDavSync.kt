package dev.ujhhgtg.via.sync

import android.content.Context
import dev.ujhhgtg.via.data.BrowserDatabase
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.io.File

/** pb.a/rb.* coordination. Runs on an IO dispatcher; no network occurs merely by opening settings. */
class WebDavSync(context: Context, database: BrowserDatabase) {
    enum class Operation { SYNC, PUSH, MERGE, PULL }
    data class Failure(val file: String, val code: Int, val message: String) {
        /** rb.f.a uses operation-specific error text, without adding an invented file prefix. */
        val description: String get() = when (code) {
            101 -> "Cannot merge: "
            201 -> "Cannot push: "
            301 -> "Cannot pull: "
            else -> ""
        } + message
    }
    private val store = SyncConfigurationStore(context)
    private val sections = SyncDataSections(context, database)
    private var lastRun = 0L
    private val timestamps = File(store.directory, "timestamp.txt")
    private var activeConfiguration: SyncConfiguration? = null
    private var states = linkedMapOf<String, Timestamp>()

    fun lastSynced(): Long {
        val config = configuration() ?: return 0L
        return SyncDataSections.names(config.sections).maxOfOrNull { states[config.remotePath + it]?.synced ?: 0L } ?: 0L
    }

    /** pb.a.j rebuilds the storage index on configuration changes, preserving the shared cooldown. */
    private fun configuration(): SyncConfiguration? {
        val current = store.read()
        if (current != activeConfiguration) {
            if (current?.id != activeConfiguration?.id) lastRun = 0L
            states = index()
            activeConfiguration = current
        }
        return current
    }

    suspend fun run(operation: Operation, forced: Boolean = true): List<Failure> {
        val config = configuration() ?: return emptyList()
        if (!config.isConfigured || config.sections == 0) return emptyList()
        if (!forced && (!config.autoSync || System.currentTimeMillis() - lastRun < 180_000)) return emptyList()
        val client = WebDavClient(config)
        val errors = mutableListOf<Failure>()
        for (name in SyncDataSections.names(config.sections)) {
            val path = config.remotePath + name
            val state = states.getOrPut(path) { Timestamp(path) }
            var action = 101
            try {
                val remote = if (operation == Operation.SYNC) {
                    try { client.modified(path).also { state.modified = it } }
                    catch (error: CancellationException) { throw error } catch (_: Exception) { 0L }
                } else state.modified
                if (operation == Operation.SYNC && remote > state.synced || operation == Operation.MERGE || operation == Operation.PULL) {
                    action = if (operation == Operation.PULL) 301 else 101
                    val content = client.get(path)
                    File(store.tempDirectory, CloudAccountClient.passwordHash(path) + "-1").writeBytes(content)
                    val strategy = if (operation == Operation.PULL) 1 else if (operation == Operation.SYNC && state.synced != 0L && name != SyncDataSections.SETTINGS) 2 else 0
                    sections.importSection(name, content, strategy, state.synced)
                }
                if (operation == Operation.SYNC || operation == Operation.PUSH) {
                    action = 201
                    val cache = File(store.tempDirectory, CloudAccountClient.passwordHash(path) + "-1")
                    // rb.b/c.e invalidate the cached remote copy even when there is no local data to export.
                    if (operation == Operation.SYNC && remote == 0L && name != SyncDataSections.BOOKMARKS) cache.delete()
                    val content = sections.export(name) ?: continue
                    File(store.tempDirectory, CloudAccountClient.passwordHash(path) + "-0").writeBytes(content)
                    val force = name == SyncDataSections.BOOKMARKS && operation == Operation.PUSH || operation == Operation.SYNC && remote == 0L
                    if (force || !cache.isFile || !cache.readBytes().contentEquals(content)) {
                        client.put(path, content, if (name == SyncDataSections.SETTINGS) "text/plain" else "text/html")
                        cache.writeBytes(content)
                        state.modified = client.modified(path)
                        state.synced = state.modified
                    }
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { errors += Failure(name, action, error.message ?: "No message") }
        }
        if (errors.isEmpty()) {
            timestamps.writeText(states.values.filter { it.modified != 0L && it.synced != 0L }.joinToString("\n", postfix = "\n") {
                JSONObject().put("name", it.name).put("modified", it.modified).put("synced", it.synced).toString()
            })
            lastRun = System.currentTimeMillis()
        }
        return errors
    }

    private data class Timestamp(val name: String, var modified: Long = 0, var synced: Long = 0)
    private fun index(): LinkedHashMap<String, Timestamp> = linkedMapOf<String, Timestamp>().apply {
        if (timestamps.isFile) timestamps.forEachLine { line -> runCatching { JSONObject(line) }.getOrNull()?.let {
            val name = it.optString("name"); if (name.isNotEmpty()) put(name, Timestamp(name, it.optLong("modified"), it.optLong("synced")))
        } }
    }
}
