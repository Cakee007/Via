package dev.ujhhgtg.via.search

import dev.ujhhgtg.via.browser.UrlResolver
import dev.ujhhgtg.via.data.BookmarkRepository
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.FavoritesRepository
import dev.ujhhgtg.via.data.HistoryRepository

data class SearchTab(val id: Long, val url: String, val title: String)
data class SearchSuggestion(val title: String, val url: String? = null, val type: Int, val tabId: Long? = null) {
    val input: String get() = url?.takeIf(String::isNotEmpty) ?: title
    companion object { const val FAVORITE = 1; const val BOOKMARK = 2; const val TAB = 4; const val HISTORY = 8; const val REMOTE = 16; const val QUERY_HISTORY = 32; const val CALCULATION = 64 }
}

/** ka/g + la/a,b: local ordering, category limits, URL dedup and process-only recent searches. */
class SuggestionRepository(database: BrowserDatabase) {
    private val bookmarks = BookmarkRepository(database)
    private val history = HistoryRepository(database)
    private val favorites = FavoritesRepository(database)
    fun local(text: String, flags: Int, tabs: List<SearchTab>): List<SearchSuggestion> {
        val query = text.trim()
        if (query.isEmpty()) return if (flags and 32 != 0) recent.take(5).map { SearchSuggestion(it, type = SearchSuggestion.QUERY_HISTORY) } else emptyList()
        if (query.length >= 10000) return emptyList()
        SearchCalculator.evaluate(query)?.let { return listOf(SearchSuggestion(it, type = SearchSuggestion.CALCULATION)) }
        val extracted = UrlResolver.extractUrls(query).mapNotNull { url ->
            url.substringAfter("://").substringBefore('/').takeIf(String::isNotEmpty)?.let { SearchSuggestion(it, url, 0) }
        }
        if (query.length >= 500 || flags and 15 == 0) return extracted
        val rows = buildList {
            if (flags and 4 != 0) tabs.filter { (it.url.startsWith("http://", true) || it.url.startsWith("https://", true)) && (it.title.contains(query) || query.length > 4 && it.url.contains(query)) }
                .take(5).forEach { add(SearchSuggestion(it.title, it.url, SearchSuggestion.TAB, it.id)) }
            if (flags and 1 != 0) favorites.search(query, 5).forEach { add(SearchSuggestion(it.title.orEmpty(), it.url, SearchSuggestion.FAVORITE)) }
            if (flags and 2 != 0) bookmarks.search(query, limit = 5).forEach { add(SearchSuggestion(it.title.orEmpty(), it.url, SearchSuggestion.BOOKMARK)) }
            if (flags and 8 != 0) history.search(query, 5).forEach { add(SearchSuggestion(it.title.orEmpty(), it.url, SearchSuggestion.HISTORY)) }
        }
        val seen = mutableSetOf<String>()
        return extracted + rows.filter { it.url.isNullOrEmpty() || seen.add(it.url) }
    }
    fun recordQuery(query: String) { query.trim().takeIf(String::isNotEmpty)?.let { recent.remove(it); recent.add(0, it) } }
    fun clearQueries() = recent.clear()
    companion object { private val recent = mutableListOf<String>() }
}
