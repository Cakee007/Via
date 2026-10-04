package dev.ujhhgtg.via.settings

import android.os.Bundle
import android.view.View
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.search.SearchProviders
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import dev.ujhhgtg.via.Shell

/** hb.b6: engine manager, six-bit address-bar suggestions and search-toolbar page. */
class SearchSettingsFragment : SettingsListFragment() {
    private lateinit var preferences: BrowserPreferences
    private lateinit var providers: SearchProviders
    private lateinit var rows: SettingsRowsAdapter
    private lateinit var database: BrowserDatabase
    override fun configureToolbar(toolbar: SettingsToolbar) = toolbar.setTitle(R.string.search_settings)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = BrowserPreferences(requireContext())
        database = BrowserDatabase(requireContext())
        providers = SearchProviders(requireContext(), preferences, database)
        rows = SettingsRowsAdapter { row -> when (row.id) {
            1 -> (requireActivity() as Shell).navigate(SearchProvidersFragment())
            2 -> chooseSuggestions()
            3 -> (requireActivity() as Shell).navigate(SearchToolbarSettingsFragment())
        } }
        list.adapter = rows; bindRows()
    }
    private fun bindRows() {
        val selected = providers.list().firstOrNull { it.id == preferences.searchMode }
        rows.submit(listOf(
            SettingsRow(1, getString(R.string.search), selected?.name ?: providers.name(preferences.searchMode)),
            SettingsRow(2, getString(R.string.title_search_suggestions), suggestionLabels().filterIndexed { index, _ -> preferences.searchSuggestion and (1 shl index).toInt() != 0 }.joinToString("/").ifEmpty { null }),
            SettingsRow(3, getString(R.string.search_toolbar), getString(if (preferences.appFlags and 32768 == 0) R.string.enabled else R.string.disabled)),
        ))
    }
    private fun suggestionLabels() = arrayOf(R.string.action_favorites, R.string.action_bookmarks, R.string.title_open_tabs,
        R.string.action_history, R.string.search, R.string.search_history).map(::getString).toTypedArray()
    private fun chooseSuggestions() {
        ViaDialog(requireActivity()).title(R.string.title_search_suggestions).message(R.string.message_search_suggestions)
            .multipleChoice(suggestionLabels(), (0..5).filter { preferences.searchSuggestion and (1 shl it).toInt() != 0 }.toIntArray())
            .positive(android.R.string.ok) { _, result ->
                preferences.searchSuggestion = result.selected?.fold(0) { flags: Int, index: Int -> flags or (1 shl index) } ?: 0
                bindRows()
            }.negative(android.R.string.cancel).show()
    }
    override fun onHiddenChanged(hidden: Boolean) { super.onHiddenChanged(hidden); if (!hidden && ::rows.isInitialized) bindRows() }
    override fun onDestroyView() { database.close(); super.onDestroyView() }
}
