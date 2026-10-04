package dev.ujhhgtg.via.settings

import android.os.Bundle
import android.view.View
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.SettingsDataRepository
import dev.ujhhgtg.via.search.SearchProvider
import dev.ujhhgtg.via.search.SearchProviders
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import org.json.JSONObject

/** hb.w5: engine choices and built-in/custom shortcut editors. */
class SearchProvidersFragment : SettingsListFragment() {
    private lateinit var preferences: BrowserPreferences
    private lateinit var database: BrowserDatabase
    private lateinit var providers: SearchProviders
    private lateinit var rows: SettingsRowsAdapter
    private var values = emptyList<SearchProvider>()

    override fun configureToolbar(toolbar: SettingsToolbar) {
        toolbar.setTitle(R.string.search)
        toolbar.addAction(R.drawable.plus, R.string.action_new) { edit(0) }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = BrowserPreferences(requireContext())
        database = BrowserDatabase(requireContext())
        providers = SearchProviders(requireContext(), preferences, database)
        rows = SettingsRowsAdapter { row -> providers.selectDefault(row.id); bindRows() }.apply {
            onLongClick = { anchor, row ->
                if (row.id <= 0) edit(row.id)
                else ViaDialog(requireActivity()).items(arrayOf(getString(R.string.action_edit), getString(R.string.action_delete)), onClick = { index ->
                    if (index == 0) edit(row.id) else {
                        // z8.v2.a(): this original CN build falls back to Baidu.
                        if (preferences.searchMode == row.id) providers.selectDefault(-2)
                        SettingsDataRepository(database).delete(row.id)
                        val shortcuts = providers.shortcuts().toMutableMap().apply { remove(row.id) }
                        preferences.searchShortcuts = JSONObject(shortcuts.mapKeys { it.key.toString() }).toString()
                        reload()
                    }
                }).showAnchored(anchor)
                true
            }
        }
        list.adapter = rows
        reload()
    }
    private fun reload() { values = providers.list(); bindRows() }
    private fun bindRows() {
        val shortcuts = providers.shortcuts()
        rows.submit(values.map { SettingsChoiceRow(it.id, it.name, it.id == preferences.searchMode, shortcuts[it.id]) })
    }
    private fun edit(id: Int) {
        parentFragmentManager.setFragmentResultListener("engine_result", this) { _, result ->
            val savedId = result.getInt("engine_result")
            if (savedId > 0 && preferences.searchMode == savedId) providers.selectDefault(savedId)
            reload()
            if (id == 0 && savedId > 0) list.smoothScrollToPosition(values.lastIndex)
            parentFragmentManager.clearFragmentResultListener("engine_result")
        }
        (requireActivity() as Shell).navigate(SearchProviderEditorFragment.newInstance(id))
    }
    override fun onDestroyView() { database.close(); super.onDestroyView() }
}
