package dev.ujhhgtg.via.search

import android.content.Context
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.SettingsData
import dev.ujhhgtg.via.data.SettingsDataRepository
import org.json.JSONObject
import java.util.Locale

data class SearchProvider(val id: Int, val name: String, val template: String)

/** z8/v2, z9 provider URLs and tb/c shortcut defaults. */
class SearchProviders(private val context: Context, private val preferences: BrowserPreferences, database: BrowserDatabase) {
    private val custom = SettingsDataRepository(database)
    fun list(): List<SearchProvider> {
        val ids = BuiltinSearchProviders.ids()
        return ids.map { SearchProvider(it, name(it), template(it)) } + custom.list(SettingsData.SEARCH_ENGINE).map { SearchProvider(it.id, it.title.orEmpty(), it.content.orEmpty()) }
    }
    fun name(id: Int): String = if (id > 0) custom.find(id)?.title.orEmpty() else context.getString(when (id) {
        -1 -> R.string.google_url; -2 -> R.string.baidu_url; -3 -> R.string.bing_url; -4 -> R.string.shenma_url; -5 -> R.string.haosou_url; -6 -> R.string.sougou_url
        -7 -> R.string.yandex_url; -8 -> R.string.yahoo_url; -9 -> R.string.startpage_url; -10 -> R.string.duckduckgo_url; -11 -> R.string.toutiao_url; -12 -> R.string.metaso_url; else -> R.string.search
    })
    fun template(id: Int): String = BuiltinSearchProviders.resolveTemplate(id,
        preferences.webFlags and 2048 != 0, if (id > 0) custom.find(id)?.content ?: preferences.searchUrl else preferences.searchUrl)
    fun selectDefault(id: Int) {
        if (id > 0 || id <= -999) preferences.searchUrl = template(id)
        preferences.searchMode = id
    }
    fun shortcuts(): Map<Int, String> = BuiltinSearchProviders.shortcuts().apply {
        runCatching { JSONObject(preferences.searchShortcuts) }.getOrNull()?.let { json -> json.keys().forEach { key -> key.toIntOrNull()?.let { put(it, json.optString(key)) } } }
    }
    fun shortcut(text: String): Pair<Int, String>? {
        val whitespace = text.indexOfFirst(Char::isWhitespace)
        if (whitespace <= 0) return null
        val prefix = text.substring(0, whitespace).trim().lowercase(Locale.ROOT)
        val id = shortcuts().entries.firstOrNull { it.value.isNotEmpty() && it.value == prefix }?.key ?: return null
        if (name(id).isEmpty()) return null
        return id to text.substring(whitespace).trim()
    }
    fun suggestionProvider(selected: Int = preferences.searchMode): Int = when (selected) {
        -1 -> 1; -2 -> 2; -3 -> 3; -5 -> 4
        else -> if (selected == preferences.searchMode) { if (!Locale.getDefault().country.equals("CN", true) && !context.resources.configuration.locales[0].toLanguageTag().equals("zh-CN", true)) 1 else 2 } else suggestionProvider(preferences.searchMode)
    }
}
