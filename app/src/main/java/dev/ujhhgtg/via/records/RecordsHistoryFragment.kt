package dev.ujhhgtg.via.records

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.common.GeneratedDocumentState
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.HistoryEntry
import dev.ujhhgtg.via.data.HistoryRepository
import dev.ujhhgtg.via.data.historyDeletionPeriods
import dev.ujhhgtg.via.skins.SkinResources
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import java.util.TimeZone

/** db.n: grouped history, selection, original toolbar and deletion periods. */
class RecordsHistoryFragment : Fragment(), RecordsBackHandler {
    private lateinit var database: BrowserDatabase
    private lateinit var history: HistoryRepository
    private lateinit var page: RecordsPageLayout
    private val adapter = RecordsRowAdapter()
    private var query = ""
    private var editing = false
    override val recordsHasTransientState get() = editing || query.isNotEmpty()
    private val selected = linkedSetOf<Int>()
    private var entries = emptyList<HistoryEntry>()
    private val host get() = requireActivity() as Shell
    override fun onCreate(state: Bundle?) { super.onCreate(state); database = BrowserDatabase.shared(requireContext()); history = HistoryRepository(database); query = state?.getString("query") ?: arguments?.getString("query").orEmpty() }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        page = RecordsPageLayout(requireContext(), query) { query = it; editing = false; selected.clear(); render() }
        adapter.faviconLoader = { url -> dev.ujhhgtg.via.home.HomeIcons.load(requireContext(), url)?.let {
            it.toDrawable(resources)
        } }
        page.list.adapter = adapter; page.list.addItemDecoration(RecordsDateDecoration(adapter))
        return page
    }
    override fun onViewCreated(view: View, state: Bundle?) { super.onViewCreated(view, state); render() }
    override fun onResume() { super.onResume(); if (::page.isInitialized) render() }
    private var generation = 0L
    private fun render() {
        if (!::page.isInitialized) return
        val token = ++generation
        page.post { (parentFragment as? RecordsContainerFragment)?.updateRecordsNavigation() }
        val context = requireContext()
        val editingSnapshot = editing
        val selectedSnapshot = selected.toSet()
        val currentQuery = query
        RecordsLoading.load(token, {
            // na.h search runs on the IO scheduler like the source's Rx chain.
            val loaded = history.search(currentQuery)
            loaded to loaded.map { item -> RecordsRow(item.title.orEmpty(), recordDisplayUrl(item.url.orEmpty()), icon = SkinResources.drawable(context, R.drawable.clock), faviconUrl = item.url,
                onClick = { if (editingSnapshot) toggle(item.id) else host.openRecordFromRecords(item.url.orEmpty(), 0) },
                onLongClick = { anchor -> if (editingSnapshot) toggle(item.id) else historyActions(anchor, item); true }, dragKey = item.id.toString(),
                selected = if (editingSnapshot) item.id in selectedSnapshot else null, timestamp = item.updatedAt * 1000) }
        }, { (loaded, rows) ->
            if (token != generation || !isAdded) return@load
            entries = loaded
            selected.retainAll(entries.map { it.id }.toSet())
            if (entries.isEmpty()) editing = false
            adapter.submit(rows)
            page.setEmpty(entries.isEmpty())
        })
        if (!editing) page.actions.show(listOf(RecordsActionBar.Action(getString(R.string.tabs)) { host.openTabsFromRecords() }), listOf(
            RecordsActionBar.Action(getString(R.string.action_delete_all), entries.isNotEmpty()) { anchor -> if (query.isEmpty()) chooseDeleteRange(anchor) else confirmDelete(entries) },
            RecordsActionBar.Action(getString(R.string.action_edit), entries.isNotEmpty()) { editing = true; render() }
        )) else page.actions.show(listOf(
            RecordsActionBar.Action(getString(if (selected.size == entries.size && selected.isNotEmpty()) R.string.cancel_all else R.string.select_all)) { if (selected.size == entries.size) selected.clear() else selected.addAll(entries.map { it.id }); render() },
            RecordsActionBar.Action(if (selected.isEmpty()) getString(R.string.action_delete) else getString(R.string.delete_hint, selected.size), selected.isNotEmpty(), true) { confirmDelete(entries.filter { it.id in selected }) }
        ), listOf(RecordsActionBar.Action(getString(R.string.done)) { finishEdit() }))
    }
    private fun toggle(id: Int) { if (!selected.add(id)) selected.remove(id); render() }
    private fun finishEdit() { editing = false; selected.clear(); render() }
    override fun handleRecordsBack(): Boolean = when { editing -> { finishEdit(); true }; query.isNotEmpty() -> { page.search.setText(""); page.search.clearFocus(); page.search.hideKeyboard(); true }; else -> false }
    private fun historyActions(anchor: View, item: HistoryEntry) {
        ViaDialog(requireActivity()).items(arrayOf(getString(R.string.action_open_in_background), getString(R.string.action_open_in_new), getString(R.string.action_delete), getString(R.string.action_copy), getString(R.string.action_share)), onClick = { which -> when (which) {
            0 -> host.openRecordFromRecords(item.url.orEmpty(), 2)
            1 -> host.openRecordFromRecords(item.url.orEmpty(), 1)
            2 -> confirmDelete(listOf(item))
            3 -> host.copyRecordFromRecords(item.url.orEmpty())
            4 -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, item.url), getString(R.string.action_share)))
        } }).showAnchored(anchor)
    }
    private fun confirmDelete(items: List<HistoryEntry>) {
        if (items.isEmpty()) return
        val message = if (items.size == 1) getString(R.string.delete_item_message, items[0].title.orEmpty()) else getString(R.string.delete_items_message, items.size)
        ViaDialog(requireActivity()).title(R.string.action_delete).message(message).positive(android.R.string.ok) { _, _ -> if (history.deleteIds(items.map { it.id }) > 0) GeneratedDocumentState.mark(GeneratedDocumentState.HISTORY); finishEdit() }.negative(android.R.string.cancel).show()
    }
    private fun chooseDeleteRange(anchor: View) {
        // db.n.n3 -> bb.v.y queries current DB counts on IO, then bb.v.t groups
        // by the raw timezone offset (including its original DST behavior).
        RecordsLoading.load(generation, {
            val now = System.currentTimeMillis() / 1000
            historyDeletionPeriods(history.ranges(now), now, TimeZone.getDefault().rawOffset / 1000)
        }, { periods ->
            if (!isAdded || view == null) return@load
            val titles = intArrayOf(R.string.last_hour, R.string.today, R.string.today_and_yesterday, R.string.last_seven_days, R.string.all_time)
            var previousCount = 0
            val ranges = periods.mapIndexedNotNull { index, period ->
                if (period.count == 0 || period.count == previousCount) null else {
                    previousCount = period.count
                    period.since to getString(R.string.description_with_number, getString(titles[index]), period.count)
                }
            }.reversed()
            if (ranges.isEmpty()) return@load
            ViaDialog(requireActivity()).title(R.string.delete_history_from).items(ranges.map { it.second }.toTypedArray(), onClick = { index ->
                ViaDialog(requireActivity()).title(R.string.delete_history_from).message(ranges[index].second).positive(android.R.string.ok) { _, _ -> if (history.clearSince(ranges[index].first) > 0) GeneratedDocumentState.mark(GeneratedDocumentState.HISTORY); render() }.negative(android.R.string.cancel).showAnchored(anchor)
            }).showAnchored(anchor)
        })
    }
    override fun onSaveInstanceState(out: Bundle) { out.putString("query", query); super.onSaveInstanceState(out) }

    companion object { fun newInstance(query: String = "") = RecordsHistoryFragment().apply { arguments = Bundle().apply { putString("query", query) } } }
}
