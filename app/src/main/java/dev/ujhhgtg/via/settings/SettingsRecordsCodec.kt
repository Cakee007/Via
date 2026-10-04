package dev.ujhhgtg.via.settings

import android.content.Context
import android.util.Base64
import dev.ujhhgtg.via.browser.filter.FilterStore
import dev.ujhhgtg.via.browser.script.ScriptResources
import dev.ujhhgtg.via.browser.script.ScriptStore
import dev.ujhhgtg.via.browser.script.UserScript
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.SettingsData
import dev.ujhhgtg.via.data.SettingsDataRepository
import dev.ujhhgtg.via.data.SiteConfiguration
import dev.ujhhgtg.via.data.SiteConfigurationRepository
import dev.ujhhgtg.via.home.HomeBackground
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.json.JSONObject

/** m9.l NDJSON codec shared by archive and sync. Methods perform no network requests. */
class SettingsRecordsCodec(context: Context, private val database: BrowserDatabase) {
    private val context = context.applicationContext
    private val preferences = BrowserPreferences(this.context)

    fun export(sync: Boolean = true): String = encode(sync).first
    fun exportArchiveEntries(): Map<String, ByteArray> = encode(false).let { (records, entries) ->
        entries + ("settings.txt" to records.toByteArray(Charsets.UTF_8))
    }

    private fun encode(sync: Boolean): Pair<String, Map<String, ByteArray>> {
        val entries = linkedMapOf<String, ByteArray>()
        val settings = preferences.exportSettings()
        fun imageMember(key: String, file: File?) {
            file?.takeIf(File::isFile)?.let {
                settings.put(key, it.name)
                if (!sync) entries[it.name] = it.readBytes()
            }
        }
        imageMember("backgroundPath", preferences.backgroundHome?.let(::File))
        if (preferences.logoChoice == 1) {
            val logoPath = Regex("file://([^\"]+)").find(preferences.homeTag.orEmpty())?.groupValues?.get(1)
            imageMember("logoPath", logoPath?.let(::File))
        }
        val lines = mutableListOf(
            JSONObject().put("meta", JSONObject().put("dataVersion", 1).put("appVersion", 20260823)).toString(),
            JSONObject().put("settings", settings).toString(),
        )
        val settingsData = SettingsDataRepository(database)
        settingsData.list().forEach { item ->
            val value = item.toJson()
            if ((item.type == SettingsData.USER_AGENT && preferences.userAgentChoice == item.id) ||
                (item.type == SettingsData.SEARCH_ENGINE && preferences.searchMode == item.id)) value.put("selected", true)
            lines += JSONObject().put("settingsData", value).toString()
        }
        SiteConfigurationRepository(database).all().forEach { site ->
            val config = runCatching { JSONObject(site.data ?: "{}") }.getOrNull() ?: return@forEach
            if (config.optInt("uachoice") > 0) config.put("uafallback", settingsData.find(config.optInt("uachoice"))?.content.orEmpty())
            lines += JSONObject().put("siteConf", JSONObject().put(site.domain, config)).toString()
        }
        ScriptStore(context).use { scripts ->
            scripts.list().asReversed().forEach { script ->
                val value = JSONObject().put("scriptId", script.scriptId).put("name", script.name).put("enabled", script.enabled)
                script.downloadUrl?.takeIf(String::isNotEmpty)?.let { value.put("url", it) }
                script.userOverrides?.let { value.put("userOverrides", it) }
                if (script.content.isNotEmpty()) {
                    if (!sync) {
                        val path = "script-${stem(script.name)}${script.id}.txt"
                        entries[path] = script.source().toByteArray(Charsets.UTF_8)
                        value.put("path", path).put("lastUpdated", script.lastUpdatedAt)
                    } else if (script.downloadUrl.isNullOrEmpty()) {
                        value.put("code", compress(script.source())).put("lastUpdated", script.lastUpdatedAt)
                    }
                }
                lines += JSONObject().put("script", value).toString()
            }
        }
        val filters = FilterStore(context)
        filters.readSubscriptions().forEach { filter ->
            val value = JSONObject().put("url", filter.url).put("title", filter.title).put("enabled", filter.enabled)
            if (!sync) filter.filePath?.let(::File)?.takeIf { it.isFile && it.length() > 0 }?.let { file ->
                val path = "filters-${stem(filter.title.orEmpty())}${ScriptResources.cacheName(filter.url)}.txt"
                entries[path] = file.readBytes()
                value.put("path", path).put("size", filter.size).put("lastUpdated", file.lastModified())
            }
            lines += JSONObject().put("filters", value).toString()
        }
        if (!sync && filters.customFile.isFile) entries["filters-custom.txt"] = filters.customFile.readBytes()
        return lines.joinToString("\n", postfix = "\n") to entries
    }

    /** m9.l.b ignores its strategy argument; records merge by each subsystem's original identity. */
    @Suppress("UNUSED_PARAMETER")
    fun importRecords(source: String, strategy: Int = 0, entries: Map<String, ByteArray> = emptyMap()): Boolean {
        var changed = false
        // m9.l.j ignores malformed/empty records and processes the first object key.
        val records = source.lineSequence().filter(String::isNotBlank).mapNotNull { line ->
            runCatching {
                val record = JSONObject(line)
                val key = record.keys().asSequence().firstOrNull() ?: return@runCatching null
                val value = record.optJSONObject(key)?.takeIf { it.length() > 0 } ?: return@runCatching null
                JSONObject().put(key, value)
            }.getOrNull()
        }.toList()
        val imageImportDirectory by lazy { File(context.externalCacheDir ?: context.cacheDir, "backup/${UUID.randomUUID()}").apply { mkdirs() } }
        fun imageMember(name: String): File? {
            val bytes = entries[name] ?: return null
            if (name.isEmpty()) return null
            return File(imageImportDirectory, File(name).name).apply { writeBytes(bytes) }
        }
        records.forEach { it.optJSONObject("settings")?.let { value ->
            preferences.importSettings(value)
            val content = context.getExternalFilesDir("content") ?: File(context.filesDir, "content")
            imageMember(value.optString("backgroundPath"))?.let { source ->
                content.mkdirs()
                val destination = File(content, source.name)
                source.copyTo(destination, overwrite = true)
                HomeBackground.clearCache(context)
                preferences.backgroundHome = destination.absolutePath
            }
            imageMember(value.optString("logoPath"))?.let { source ->
                content.mkdirs(); source.copyTo(File(content, source.name), overwrite = true)
                preferences.logoChoice = 1
                // m9.l.l stores the extraction source p1, confirmed by pristine smali lines143–155.
                preferences.homeTag = "<img class=\"smaller\" src=\"file://${source.absolutePath}\" />"
            }
            changed = true
        } }
        val settingsData = SettingsDataRepository(database)
        val customRows = records.mapNotNull { it.optJSONObject("settingsData") }
        if (customRows.isNotEmpty()) {
            settingsData.importEntries(customRows.map(SettingsData::fromJson))
            customRows.filter { it.optBoolean("selected") }.forEach { json ->
                val item = SettingsData.fromJson(json)
                settingsData.findByContent(item.content, item.type)?.let { selected ->
                    when (selected.type) {
                        SettingsData.USER_AGENT -> { preferences.userAgentChoice = selected.id; preferences.userAgent = selected.content.orEmpty() }
                        SettingsData.SEARCH_ENGINE -> { preferences.searchMode = selected.id; preferences.searchUrl = selected.content.orEmpty() }
                    }
                }
            }
            changed = true
        }
        records.forEach { record -> record.optJSONObject("siteConf")?.let { sites ->
            sites.keys().forEach { domain -> sites.optJSONObject(domain)?.let { config ->
                if (config.optInt("uachoice") > 0) {
                    val fallback = config.optString("uafallback")
                    val mapped = settingsData.findByContent(fallback, SettingsData.USER_AGENT)
                    config.put("uachoice", mapped?.id ?: if (fallback.isEmpty()) -1000 else -999)
                    if (mapped == null && fallback.isNotEmpty()) config.put("uastring", fallback)
                }
                SiteConfigurationRepository(database).put(SiteConfiguration(domain, config.toString()))
                changed = true
            } }
        } }
        ScriptStore(context).use { scripts ->
            records.mapNotNull { it.optJSONObject("script") }.forEach { json ->
                val id = json.optString("scriptId")
                val url = json.optString("url").ifEmpty { null }
                val current = scripts.findByIdentity(id, url)
                val enabled = json.optBoolean("enabled", false)
                if (current != null && json.optLong("lastUpdated", 1) < current.lastUpdatedAt) scripts.setEnabled(current.id, enabled)
                else {
                    val encoded = json.optString("code")
                    val code = if (encoded.isNotEmpty()) decompress(encoded) else entries[json.optString("path")]?.toString(Charsets.UTF_8).orEmpty()
                    val parsed = UserScript.parse(code, url) ?: if (current == null && url != null)
                        UserScript(scriptId = id, name = json.optString("name", url), content = "", downloadUrl = url) else null
                    if (parsed != null) scripts.save(parsed.copy(id = current?.id ?: 0, enabled = enabled,
                        userOverrides = json.optString("userOverrides").ifEmpty { null }))
                }
                changed = true
            }
        }
        val filters = FilterStore(context)
        records.mapNotNull { it.optJSONObject("filters") }.forEach { json ->
            val url = json.optString("url").takeIf(String::isNotEmpty) ?: return@forEach
            val existing = filters.readSubscriptions().firstOrNull { it.url == url }
            val file = existing?.filePath?.let(::File) ?: File(filters.root, UUID.randomUUID().toString() + ".txt")
            val content = entries[json.optString("path")]
            var size = existing?.size ?: 0
            if (content != null && (existing == null || json.optLong("lastUpdated", 1) > file.lastModified())) {
                file.writeBytes(content); size = json.optInt("size", 0)
            }
            filters.upsertSubscription(existing?.copy(enabled = json.optBoolean("enabled"), size = size) ?:
                FilterStore.Subscription(url, json.optString("title").ifEmpty { null }, filePath = file.path, enabled = json.optBoolean("enabled"), size = size))
            changed = true
        }
        entries["filters-custom.txt"]?.let { content ->
            val lines = (content.toString(Charsets.UTF_8).lineSequence() + filters.readCustom().lineSequence()).toSet()
            filters.writeCustom(lines.joinToString("\n", postfix = "\n")); changed = true
        }
        return changed
    }

    companion object {
        private fun stem(name: String): String = name.trim().map { if (it in "\\/:*?\"<>|" || it.isWhitespace()) '-' else it }.joinToString("")
            .replace(Regex("-{2,}"), "-").lowercase(Locale.ROOT).take(128).let { if (it.isEmpty()) "" else "$it-" }
        private fun compress(source: String): String {
            val output = ByteArrayOutputStream()
            GZIPOutputStream(output).use { it.write(source.toByteArray(Charsets.UTF_8)) }
            return Base64.encodeToString(output.toByteArray(), Base64.DEFAULT).trim()
        }
        private fun decompress(encoded: String): String = GZIPInputStream(Base64.decode(encoded, Base64.DEFAULT).inputStream())
            .bufferedReader(Charsets.UTF_8).use { it.readText().trim() }
    }
}
