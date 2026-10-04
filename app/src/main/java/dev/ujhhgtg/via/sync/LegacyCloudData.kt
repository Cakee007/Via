package dev.ujhhgtg.via.sync

import dev.ujhhgtg.via.common.GeneratedDocumentState

import android.content.Context
import android.util.Base64
import dev.ujhhgtg.via.browser.filter.FilterStore
import dev.ujhhgtg.via.browser.script.ScriptStore
import dev.ujhhgtg.via.browser.script.UserScript
import dev.ujhhgtg.via.data.*
import dev.ujhhgtg.via.settings.SettingsRecordsCodec
import java.net.URLEncoder
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** l9.*: legacy server sections are Base64 JSON/JSON-lines, then URL-encoded for upload. */
class LegacyCloudData(private val context: Context, private val database: BrowserDatabase) {
    private val bookmarks = BookmarkRepository(database)
    private val preferences = BrowserPreferences(context)

    fun uploadSections(): Map<String, String> {
        val folders = bookmarks.listFolders().associateBy { it.id }
        fun folderPath(id: String): String {
            val names = mutableListOf<String>()
            var next = id
            val seen = mutableSetOf<String>()
            while (next.isNotEmpty() && seen.add(next)) {
                val folder = folders[next] ?: break
                names.add(0, folder.title.orEmpty().replace("/", "\\/"))
                next = folder.parentFolderId
            }
            return names.joinToString("/")
        }
        val bookmarkJson = bookmarks.listItems().sortedWith(compareBy<BookmarkItem, String>(String.CASE_INSENSITIVE_ORDER) { it.folderId }.thenBy { it.ordering }).joinToString("\n") {
            JSONObject().put("title", it.title).put("url", it.url).put("folder", folderPath(it.folderId))
                .put("order", it.ordering).put("updated_at", it.lastUpdatedAt).put("created_at", it.createdAt).toString()
        }
        val settings = preferences.exportSettings().apply {
            keys().asSequence().toList().forEach { name -> if (opt(name) is String && optString(name).length > 4096) remove(name) }
        }
        val filterStore = FilterStore(context)
        val adrules = JSONObject().apply {
            val custom = filterStore.readCustom()
            if (custom.isNotEmpty() && custom.length <= 30_720) put("custom", custom)
            val subscriptions = filterStore.readSubscriptions()
            if (subscriptions.isNotEmpty()) put("subscribed", subscriptions.joinToString("\n", postfix = "\n") { JSONObject().put("url", it.url).toString() })
        }
        fun encoded(value: String): String = if (value.isEmpty()) "" else URLEncoder.encode(Base64.encodeToString(value.trim().toByteArray(Charsets.UTF_8), Base64.DEFAULT), "UTF-8")
        return linkedMapOf("adrules" to if (adrules.length() == 0) "" else encoded(adrules.toString()),
            "bookmark" to encoded(bookmarkJson), "settings" to encoded(settings.toString()), "other" to "")
    }

    fun restore(source: String): Boolean {
        val outer = JSONObject(source)
        var restored = false
        fun decoded(key: String): String? = outer.optString(key).takeIf { it.isNotEmpty() && !it.equals("null", true) }
            ?.let { Base64.decode(it, Base64.DEFAULT).toString(Charsets.UTF_8) }
        decoded("bookmark")?.takeIf(String::isNotEmpty)?.let { restored = importBookmarks(it) || restored }
        decoded("settings")?.takeIf(String::isNotEmpty)?.let { preferences.importSettings(JSONObject(it)); restored = true }
        decoded("favorite")?.takeIf(String::isNotEmpty)?.let { data ->
            val values = lines(data).mapNotNull { json -> json.optString("url").takeIf(String::isNotEmpty)?.let {
                Favorite(url = it, title = json.optString("title"), order = json.optInt("order"))
            } }
            FavoritesRepository(database).importEntries(values)
            restored = true
        }
        decoded("kvdata")?.takeIf(String::isNotEmpty)?.let { data ->
            val array = JSONArray(data)
            val records = (0 until array.length()).mapNotNull { array.optJSONObject(it) }.joinToString("\n") { JSONObject().put("settingsData", it).toString() }
            if (records.isNotEmpty()) restored = SettingsRecordsCodec(context, database).importRecords(records) || restored
        }
        decoded("conf")?.takeIf(String::isNotEmpty)?.let { data ->
            val records = JSONObject().put("siteConf", JSONObject(data)).toString()
            restored = SettingsRecordsCodec(context, database).importRecords(records) || restored
        }
        decoded("adrules")?.takeIf(String::isNotEmpty)?.let { data ->
            val json = JSONObject(data)
            val filters = FilterStore(context)
            if (json.has("custom")) filters.writeCustom((json.optString("custom").lineSequence() + filters.readCustom().lineSequence()).filter(String::isNotEmpty).distinct().joinToString("\n", postfix = "\n"))
            lines(json.optString("subscribed")).forEach { item ->
                val url = item.optString("url")
                if (url.isNotEmpty() && filters.readSubscriptions().none { it.url == url }) filters.upsertSubscription(FilterStore.Subscription(url))
            }
            restored = true
        }
        decoded("other")?.takeIf(String::isNotEmpty)?.let { data ->
            ScriptStore(context).use { scripts -> lines(data).forEach { json ->
                val code = json.optString("codeV2")
                if (code.isNotEmpty()) UserScript.parse(code, json.optString("url").ifEmpty { null })?.let { parsed ->
                    scripts.save(parsed.copy(scriptId = json.optString("scriptId").ifEmpty { parsed.scriptId }, enabled = json.optBoolean("enabled")))
                    restored = true
                }
            } }
        }
        return restored
    }

    private fun importBookmarks(source: String): Boolean {
        val folders = mutableListOf<BookmarkFolder>()
        val items = mutableListOf<BookmarkItem>()
        val paths = mutableMapOf("" to "")
        var timestamp = System.currentTimeMillis() / 1000 - 30
        for (json in lines(source)) {
            val url = json.optString("url")
            if (url.isEmpty()) continue
            val path = json.optString("folder")
            var parent = ""
            var joined = ""
            for (name in folderNames(path)) {
                joined += "/$name"
                parent = paths.getOrPut(joined) {
                    val id = UUID.randomUUID().toString()
                    folders += BookmarkFolder(id, name, parent, folders.size + 1, timestamp, timestamp)
                    id
                }
            }
            items += BookmarkItem(UUID.randomUUID().toString(), url, json.optString("title"), parent, json.optInt("order"),
                json.optLong("updated_at").takeIf { it != 0L } ?: timestamp, json.optLong("created_at").takeIf { it != 0L } ?: timestamp)
            timestamp++
        }
        if (bookmarks.importCollection(BookmarkCollection(folders, items)) > 0) GeneratedDocumentState.mark(GeneratedDocumentState.BOOKMARKS)
        return true
    }

    private fun lines(value: String): List<JSONObject> = value.lineSequence().takeWhile(String::isNotEmpty).mapNotNull { runCatching { JSONObject(it) }.getOrNull() }.toList()

    companion object {
        fun folderNames(value: String): List<String> {
            if (value.isEmpty()) return emptyList()
            val names = mutableListOf<String>()
            val current = StringBuilder()
            var index = 0
            while (index < value.length) {
                if (value[index] == '\\' && index + 1 < value.length && value[index + 1] == '/') { current.append('/'); index += 2 }
                else if (value[index] == '/') { names += current.toString(); current.setLength(0); index++ }
                else current.append(value[index++])
            }
            names += current.toString()
            return names
        }
    }
}
