package dev.ujhhgtg.via.browser.filter

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Filesystem store matching Via's custom.txt/subscribed.txt and subscription JSON records. */
class FilterStore(private val context: Context) {
    data class Subscription(
        val url: String,
        val title: String? = null,
        val homepage: String? = null,
        val license: String? = null,
        val filePath: String? = null,
        val enabled: Boolean = false,
        val size: Int = 0,
    ) {
        fun toJson(): String = JSONObject().apply {
            put("title", title)
            put("url", url)
            put("homepage", homepage)
            put("license", license)
            put("filePath", filePath)
            if (filePath != null) { put("enabled", enabled); put("size", size) }
        }.toString()

        companion object {
            fun fromJson(raw: String): Subscription? = runCatching {
                val json = JSONObject(raw)
                val url = json.optString("url").trim().takeIf { it.isNotEmpty() } ?: return null
                val path = json.optString("filePath").ifBlank { null }
                Subscription(url, json.optString("title").ifBlank { null }, json.optString("homepage").ifBlank { null }, json.optString("license").ifBlank { null }, path,
                    path != null && json.optBoolean("enabled", false), if (path != null) json.optInt("size", 0) else 0)
            }.getOrNull()
        }
    }

    // z8.c1.x/w: use external app storage with an internal fallback.
    val root = (context.getExternalFilesDir("filters") ?: File(context.filesDir, "filters")).apply { mkdirs() }
    init {
        val old = File(context.filesDir, "filters")
        val imported = File(root, ".legacy-imported")
        if (old != root && old.isDirectory && !imported.exists()) {
            old.copyRecursively(root, overwrite = false, onError = { _, _ -> OnErrorAction.SKIP })
            imported.writeText("1")
        }
    }
    val customFile: File = File(root, "custom.txt")
    val subscribedFile: File = File(root, "subscribed.txt")

    fun readLists(includeBuiltIn: Boolean): List<String> = buildList {
        add(readCustom())
        if (includeBuiltIn) add(runCatching { context.assets.open("simple.txt").bufferedReader().use { it.readText() } }.getOrDefault(""))
        readSubscriptions().filter { it.enabled }.forEach { subscription ->
            subscription.filePath?.let { path -> add(File(path).readTextOrNull()) }
        }
    }

    fun readCustom(): String = customFile.readTextOrNull()
    fun writeCustom(text: String) = atomicWrite(customFile, text)
    fun appendCustom(raw: String): Boolean = FilterRuntime.addCustom(this, raw)
    fun removeCustom(raw: String): Boolean = FilterRuntime.removeCustom(this, raw)

    fun readSubscriptions(): List<Subscription> = if (subscribedFile.exists()) subscribedFile.readLines().mapNotNull(Subscription::fromJson) else emptyList()
    fun defaultSubscriptions(): List<Subscription> {
        // z8.t1.b/n/i normalizes the obsolete Java language codes before b5.d.b.
        val language = context.resources.configuration.locales[0].language.let {
            when (it) { "in" -> "id"; "iw" -> "he"; "ji" -> "yi"; "jw" -> "jv"; "tl" -> "fil"; else -> it }
        }
        return FilterSubscriptionCatalog.create(root, language)
    }
    fun writeSubscriptions(values: Collection<Subscription>) = atomicWrite(subscribedFile, values.joinToString("\n") { it.toJson() } + if (values.isEmpty()) "" else "\n")
    fun upsertSubscription(value: Subscription) {
        val values = readSubscriptions().toMutableList()
        val index = values.indexOfFirst { it.url == value.url }
        if (index < 0) values += value else values[index] = value
        writeSubscriptions(values)
    }
    private fun atomicWrite(file: File, text: String) {
        val temp = File(file.parentFile, ".${file.name}.tmp")
        temp.writeText(text)
        if (!temp.renameTo(file)) { file.writeText(text); temp.delete() }
        FilterRuntime.listsChanged(context)
    }

    private fun File.readTextOrNull(): String = if (exists()) runCatching { readText() }.getOrDefault("") else ""
}
