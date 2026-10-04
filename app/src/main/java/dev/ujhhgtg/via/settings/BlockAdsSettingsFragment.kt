package dev.ujhhgtg.via.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.text.format.DateUtils
import android.view.View
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.browser.UrlParser
import dev.ujhhgtg.via.browser.UrlResolver
import dev.ujhhgtg.via.browser.filter.CustomFiltersFragment
import dev.ujhhgtg.via.browser.filter.FilterStore
import dev.ujhhgtg.via.browser.filter.FilterSubscriptionUpdater
import dev.ujhhgtg.via.browser.filter.SubscriptionFiltersFragment
import dev.ujhhgtg.via.browser.script.BuiltinExpandScript
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

/** z7.d: filtering switches and the two filter settings pages. */
class BlockAdsSettingsFragment : SettingsListFragment() {
    private lateinit var preferences: BrowserPreferences
    private lateinit var rows: SettingsRowsAdapter

    override fun configureToolbar(toolbar: SettingsToolbar) = toolbar.setTitle(R.string.block_ads)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = BrowserPreferences(requireContext())
        rows = SettingsRowsAdapter { row ->
            when (row.id) {
                1 -> { preferences.adBlocking = !preferences.adBlocking; reloadBrowser(); bindRows() }
                2 -> {
                    preferences.appFlags = preferences.appFlags xor 64
                    reloadBrowser(); bindRows()
                }
                3 -> (requireActivity() as Shell).navigate(CustomFiltersFragment())
                4 -> (requireActivity() as Shell).navigate(FilterSubscriptionsFragment())
                5 -> {
                    BuiltinExpandScript.setEnabled(requireContext(), !BuiltinExpandScript.enabled(requireContext()))
                    reloadBrowser(); bindRows()
                }
            }
        }
        list.itemAnimator = null
        list.adapter = rows
        bindRows()
    }

    private fun bindRows() = rows.submit(buildList {
        addAll(listOf(
        SettingsToggleRow(1, getString(R.string.block_ads), if (preferences.adBlocking)
            getString(R.string.adblock_info, preferences.adBlockedTimes, savedDataLabel()) else getString(R.string.disable), preferences.adBlocking),
        SettingsToggleRow(2, getString(R.string.enable_built_in_filters), getString(R.string.enable_built_in_filters_description), preferences.appFlags and 64 != 0),
        SettingsRow(3, getString(R.string.custom_filters), getString(R.string.custom_filters_description)),
        SettingsRow(4, getString(R.string.filter_subscriptions), getString(R.string.filter_subscriptions_description)),
        ))
        add(SettingsToggleRow(5, getString(R.string.expand_web_content_automatically),
            getString(R.string.expand_web_content_automatically_description), BuiltinExpandScript.enabled(requireContext()),
            disabled = preferences.webFlags and 0x10000000 != 0))
    })

    /** z8.b0.x/w: the preference stores KiB, and units change at 0.8 of the next unit. */
    private fun savedDataLabel(): String {
        val bytes = preferences.getLong("savedata") * 1024.0
        val (amount, unit) = when {
            bytes >= 858993459.2 -> bytes / 1073741824.0 to "GB"
            bytes >= 838860.8 -> bytes / 1048576.0 to "MB"
            bytes >= 819.2 -> bytes / 1024.0 to "KB"
            else -> bytes to "B"
        }
        return String.format(Locale.ROOT, "%.1f %s", amount, unit)
    }

    private fun reloadBrowser() { (activity as? Shell)?.browserReloadPreferences() }
}

/** z7.q0: subscriptions, locale defaults, update interval and per-subscription actions. */
class FilterSubscriptionsFragment : SettingsListFragment() {
    private lateinit var store: FilterStore
    private lateinit var preferences: BrowserPreferences
    private lateinit var rows: SettingsRowsAdapter
    private var records = emptyList<FilterStore.Subscription>()
    private val worker = Executors.newSingleThreadExecutor()
    private var updating = false
    private val intervals = longArrayOf(0, 1, 3, 7, 15)
    private val intervalLabels = intArrayOf(R.string.never, R.string.every_day, R.string.every_3_days, R.string.every_week, R.string.every_15_days)

    override fun configureToolbar(toolbar: SettingsToolbar) {
        toolbar.setTitle(R.string.filter_subscriptions)
        toolbar.addAction(R.drawable.plus, R.string.action_new) { add() }
        toolbar.addAction(null, R.string.update) { updateEnabled() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        store = FilterStore(requireContext())
        preferences = BrowserPreferences(requireContext())
        records = if (store.subscribedFile.isFile) store.readSubscriptions() else store.defaultSubscriptions()
        rows = SettingsRowsAdapter { row ->
            if (row is SubscriptionRow) {
                records = records.map { if (it.url == row.subscription.url) it.copy(enabled = !it.enabled) else it }
                persist()
            } else if (row.id == INTERVAL_ROW) chooseUpdateInterval()
        }.apply {
            onLongClick = { anchor, row ->
                // q0.E3 returns false after opening the anchored menu.
                (row as? SubscriptionRow)?.let { actions(anchor, it.subscription) }
                false
            }
        }
        list.adapter = rows
        refresh()
    }

    private fun refresh() {
        rows.submit(buildList {
            if (records.isNotEmpty()) {
                add(SettingsRow(INTERVAL_ROW, getString(R.string.update_interval), getString(intervalLabels[intervalIndex()])))
                add(SettingsHeadingRow(getString(R.string.filter_subscriptions)))
            }
            records.forEach { item ->
                val title = item.title?.takeIf(String::isNotEmpty) ?: item.url.substringAfter("://")
                val modified = item.filePath?.let { File(it).lastModified() } ?: 0L
                val updated = if (modified == 0L) getString(R.string.has_not_been_updated)
                    else getString(R.string.updated_, if ((System.currentTimeMillis() - modified) / 60_000 < 1)
                        getString(R.string.just_now) else DateUtils.getRelativeTimeSpanString(modified, System.currentTimeMillis(), 60_000, 65552).toString())
                val summary = getString(R.string.filter_subscription_info, resources.getQuantityString(R.plurals.filters, item.size, item.size), updated)
                add(SubscriptionRow(item, title, summary))
            }
        })
        showEmptyState(records.isEmpty())
    }

    private fun persist() {
        store.writeSubscriptions(records)
        (activity as? Shell)?.browserReloadPreferences()
        refresh()
    }

    private fun add() {
        val available = store.defaultSubscriptions().filter { candidate -> records.none { it.url == candidate.url } }
        if (available.isEmpty()) { edit(null); return }
        ViaDialog(requireActivity()).title(R.string.add_filter_subscription)
            .items((available.map { it.title.orEmpty() } + getString(R.string.custom_url)).toTypedArray(), onClick = { position ->
                if (position == available.size) edit(null)
                else { records = records + available[position]; persist() }
            }).show()
    }

    private fun actions(anchor: View, item: FilterStore.Subscription) {
        ViaDialog(requireActivity()).items(arrayOf(getString(R.string.action_preview), getString(R.string.update),
            getString(R.string.action_edit), getString(R.string.action_copy), getString(R.string.action_delete)), onClick = { choice ->
            when (choice) {
                0 -> (requireActivity() as Shell).navigate(SubscriptionFiltersFragment.create(item.filePath, item.title))
                1 -> update(listOf(item), all = false)
                2 -> edit(item)
                3 -> {
                    (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText(null, item.url))
                    toast(R.string.toast_copy_url_successful)
                }
                4 -> ViaDialog(requireActivity()).title(R.string.action_delete)
                    .message(getString(R.string.delete_favorite_message, item.title ?: item.url.substringAfter("://", "").substringBefore('/').substringBefore(':')))
                    .positive(android.R.string.ok) { _, _ ->
                        records = records.filterNot { it.url.equals(item.url, true) }
                        item.filePath?.let { File(it).delete() }
                        persist()
                    }.negative(android.R.string.cancel).show()
            }
        }).showAnchored(anchor)
    }

    private fun edit(existing: FilterStore.Subscription?) {
        ViaDialog(requireActivity()).title(if (existing == null) R.string.add_filter_subscription else R.string.edit_filter_subscription)
            .input(existing?.url ?: "https://", getString(R.string.hint_url), 3)
            .canceledOnTouchOutside(false)
            .positive(android.R.string.ok) { _, result ->
                val source = result.edit?.firstOrNull().orEmpty()
                if (!UrlParser(source).isValid) { toast(R.string.url_is_invalid); return@positive }
                val url = UrlResolver.normalizeInput(source).orEmpty()
                // q0.w3 checks every record, including the record currently being edited.
                if (records.any { it.url.equals(url, true) }) { toast(R.string.filter_subscription_exist); return@positive }
                val item = existing?.copy(url = url)
                    ?: FilterStore.Subscription(url, filePath = File(store.root, UUID.randomUUID().toString() + ".txt").path)
                val position = records.indexOfFirst { it.url.equals(existing?.url, true) }
                records = records.toMutableList().apply { if (position < 0) add(item) else this[position] = item }
                persist()
            }.negative(android.R.string.cancel).show()
    }

    private fun intervalIndex() = intervals.indexOfLast { preferences.getLong("updater_filter_subscriptions") >= it * 86_400_000L }.coerceAtLeast(0)

    private fun chooseUpdateInterval() {
        ViaDialog(requireActivity()).title(R.string.update_interval).singleChoice(intervalLabels.map(::getString).toTypedArray(), intervalIndex()) { index ->
            preferences.putLong("updater_filter_subscriptions", intervals[index] * 86_400_000L)
            refresh()
        }.show()
    }

    private fun updateEnabled() = update(records.filter { it.enabled }, all = true)

    private fun update(selected: List<FilterStore.Subscription>, all: Boolean) {
        if (selected.isEmpty() || all && updating) return
        if (all) {
            updating = true
            preferences.putLong("updated_filter_subscriptions", System.currentTimeMillis())
        }
        toast(if (all) R.string.download_pending else R.string.update_pending)
        val host = requireActivity()
        worker.execute {
            var updated = false
            for (item in selected) {
                val result = runCatching { FilterSubscriptionUpdater.update(store, item) }.getOrNull() ?: continue
                host.runOnUiThread {
                    if (!isAdded || view == null) return@runOnUiThread
                    val index = records.indexOfFirst { it.url.equals(item.url, true) }
                    if (index >= 0) {
                        records = records.toMutableList().apply { this[index] = result.copy(enabled = this[index].enabled) }
                        persist()
                    }
                }
                updated = true
            }
            host.runOnUiThread {
                if (all) updating = false
                if (isAdded && view != null && all && updated) toast(R.string.update_filter_subscriptions_completed)
            }
        }
    }

    private fun toast(resource: Int) = ViaToast.makeText(requireContext(), getString(resource), ViaToast.LENGTH_LONG).show()

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    private companion object { const val INTERVAL_ROW = -2 }

    /** ib.f matches rows by subscription URL, so removing an item preserves the remaining rows. */
    private class SubscriptionRow(val subscription: FilterStore.Subscription, title: String, summary: String) :
        SettingsToggleRow(subscription.url.hashCode(), title, summary, subscription.enabled, titleEllipsize = TextUtils.TruncateAt.MIDDLE)
}
