package dev.ujhhgtg.via.records

import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.SessionRepository
import dev.ujhhgtg.via.data.SessionTab
import dev.ujhhgtg.via.home.HomeIcons
import dev.ujhhgtg.via.settings.SettingsPageFragment
import dev.ujhhgtg.via.settings.SettingsToolbar
import dev.ujhhgtg.via.skins.SkinResources
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog

/**
 * c8.pc: the recently closed tabs page. Toolbar (u.Ka), r0 search/list/empty
 * scaffold, More action opening the Tab settings page (hb.e7) and the Delete
 * all action with its g8 confirmation; rows restore through the browser
 * (click/s1: new tab + finish page; background: toast p7, page stays).
 */
class ClosedTabsFragment : SettingsPageFragment(), RecordsBackHandler {
    private lateinit var database: BrowserDatabase
    private lateinit var sessions: SessionRepository
    private lateinit var page: RecordsPageLayout
    private val adapter = RecordsRowAdapter()
    private var query = ""
    private val host get() = requireActivity() as Shell
    override val recordsHasTransientState get() = query.isNotEmpty()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        database = BrowserDatabase.shared(requireContext())
        sessions = SessionRepository(database)
        query = state?.getString("query") ?: ""
    }

    override fun configureToolbar(toolbar: SettingsToolbar) = toolbar.setTitle(R.string.recently_closed_tabs)

    override fun createContent(inflater: LayoutInflater, container: ViewGroup?): View {
        page = RecordsPageLayout(requireContext(), query) { query = it; render() }
        page.list.adapter = adapter
        return page
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::page.isInitialized) render()
    }

    override fun handleRecordsBack(): Boolean = if (query.isNotEmpty()) { page.search.setText(""); page.search.clearFocus(); page.search.hideKeyboard(); true } else false

    private var generation = 0L

    /** pc.u3: closed tabs ordered newest first, loaded on the IO scheduler. */
    private fun render() {
        if (!::page.isInitialized) return
        val token = ++generation
        val context = requireContext()
        val currentQuery = query
        RecordsLoading.load(token, {
            sessions.listClosed()
                .filter { currentQuery.isBlank() || (it.title ?: "").contains(currentQuery, true) || (it.url ?: "").contains(currentQuery, true) }
                .sortedByDescending { it.lastVisitedAt }
        }, { records ->
            if (token != generation || !isAdded) return@load
            adapter.submit(records.map { tab -> row(context, tab) })
            page.setEmpty(records.isEmpty())
            page.actions.show(
                listOf(RecordsActionBar.Action(getString(R.string.more)) { (requireActivity() as Shell).navigate(dev.ujhhgtg.via.settings.TabSettingsFragment()) }),
                listOf(RecordsActionBar.Action(getString(R.string.action_delete_all), records.isNotEmpty()) {
                    // pc.o3: y title with the g8 confirmation, then the whole store.
                    ViaDialog(requireActivity()).title(R.string.action_delete_all).message(R.string.dialog_sure)
                        .positive(android.R.string.ok) { _, _ -> sessions.clearClosed(); render() }
                        .negative(android.R.string.cancel).show()
                }),
            )
        })
    }

    private fun row(context: android.content.Context, tab: SessionTab): RecordsRow {
        val url = tab.url.orEmpty()
        return RecordsRow(
            title = (tab.title ?: "").take(256).ifBlank { getString(R.string.untitled) },
            subtitle = recordDisplayUrl(url),
            icon = rowIcon(context, url), dragKey = tab.id,
            onClick = { host.openClosedTabFromRecords(tab.id, 1) },
            onLongClick = { anchor -> actions(anchor, tab); true },
            timestamp = tab.lastVisitedAt,
            tintIcon = false,
        )
    }

    /** pc.V1/a.o: stored favicon by URL, else the c_ history icon tinted k.f13624h. */
    private fun rowIcon(context: android.content.Context, url: String): Drawable {
        HomeIcons.load(context, url)?.let { return it.toDrawable(context.resources) }
        val fallback = SkinResources.drawable(context, R.drawable.clock)!!.mutate()
        fallback.setColorFilter(recordColor(context, R.attr.viaSubtleColor), PorterDuff.Mode.SRC_IN)
        return fallback
    }

    /** pc.c3/j3: [background open, new tab, delete, copy, share] anchored menu. */
    private fun actions(anchor: View, tab: SessionTab) {
        val url = tab.url.orEmpty()
        ViaDialog(requireActivity()).items(
            arrayOf(
                getString(R.string.action_open_in_background),
                getString(R.string.action_open_in_new),
                getString(R.string.action_delete),
                getString(R.string.action_copy),
                getString(R.string.action_share),
            ),
            onClick = { which ->
                when (which) {
                    0 -> host.openClosedTabFromRecords(tab.id, 2)
                    1 -> host.openClosedTabFromRecords(tab.id, 1)
                    2 -> { sessions.delete(tab.id); render() }
                    3 -> {
                        host.copyRecordFromRecords(url)
                        ViaToast.makeText(requireContext(), getString(R.string.toast_copy_url_successful), ViaToast.LENGTH_SHORT).show()
                    }
                    4 -> shareUrl(url)
                }
            },
        ).showAnchored(anchor)
    }

    /** z8.f1.k: the source share entry point. */
    private fun shareUrl(url: String) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url), getString(R.string.action_share)))
    }

    override fun onSaveInstanceState(out: Bundle) { out.putString("query", query); super.onSaveInstanceState(out) }
}
