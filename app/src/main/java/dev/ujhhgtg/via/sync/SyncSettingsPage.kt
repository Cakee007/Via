package dev.ujhhgtg.via.sync

import android.app.Activity
import android.content.ContextWrapper
import android.os.Bundle
import android.text.format.DateUtils
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import dev.ujhhgtg.via.settings.SettingsRecyclerView
import dev.ujhhgtg.via.settings.SettingsRow
import dev.ujhhgtg.via.settings.SettingsHeadingRow
import dev.ujhhgtg.via.settings.SettingsToggleRow
import android.widget.FrameLayout
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import dev.ujhhgtg.via.sync.WebDavSync.Operation
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking

/** hb.a7/u/a8 and nb.n/ob.i/d account and WebDAV settings flows. */
class SyncSettingsPage(private val activity: Activity,
    private val openWebDav: (SyncConfiguration?) -> Unit,
    private val openCloudLogin: () -> Unit,
) : ContextWrapper(activity), AutoCloseable {
    val view = FrameLayout(activity)
    private var closed = false
    private val preferences: BrowserPreferences = BrowserPreferences(this)
    private val database: BrowserDatabase = BrowserDatabase(this)
    private val configuration: SyncConfigurationStore = SyncConfigurationStore(this)
    private val worker = Executors.newSingleThreadExecutor()
    private var busy = false
    private var cloudProgress: ViaDialog? = null
    private var lastSyncRequest = 0L
    private var displayedMode: Int? = null
    private val rowAnimator = DefaultItemAnimator()
    private val rows = SyncSettingsAdapter(::onRowClick, { synchronize(Operation.SYNC) }, ::chooseOperation)
    val list = SettingsRecyclerView(this).apply {
        layoutManager = LinearLayoutManager(context)
        adapter = rows
        clipToPadding = false
    }

    init {
        render()
    }

    /** hb.a7 hosts u/nb.n, both of which use Via's transparent settings RecyclerView. */
    fun render() {
        val mode = preferences.syncingChoice
        val changed = displayedMode != mode
        list.itemAnimator = if (mode == 0 || changed) null else rowAnimator
        rows.submit(if (mode == 0) accountRows() else webDavRows())
        if (mode != 0) list.itemAnimator = rowAnimator
        displayedMode = mode
        if (list.parent == null) view.addView(list, FrameLayout.LayoutParams(-1, -1))
    }

    private fun accountRows(): List<SettingsRow> = buildList {
        if (!preferences.login) add(SettingsRow(1, getString(R.string.user)))
        else {
            add(SettingsRow(2, preferences.languageUserName))
            add(SettingsRow(4, getString(R.string.user_syns)))
            add(SettingsRow(5, getString(R.string.user_update)))
            add(SettingsRow(6, getString(R.string.delete_account), getString(R.string.delete_account_hint)))
        }
        add(SettingsRow(3, getString(R.string.cloud_data_server), getString(if (preferences.cloudServer == 1) R.string.chinese_server else R.string.global_server)))
    }

    private fun onRowClick(row: SettingsRow) {
        if (row.disabled) return
        if (preferences.syncingChoice != 0) {
            val saved = configuration.read()
            when (row.id) {
                2 -> openWebDav(saved)
                3 -> saved?.let { configuration.save(it.copy(flags = it.flags xor 1)); render() }
                4 -> saved?.let { chooseSections(it, Operation.SYNC) }
                5 -> chooseOperation()
            }
            return
        }
        when (row.id) {
            1 -> openCloudLogin()
            2 -> confirm(getString(R.string.log_out), preferences.languageUserName) { preferences.login = false; render() }
            3 -> {
                val selected = preferences.cloudServer
                ViaDialog(activity).title(R.string.cloud_data_server).note(R.string.server_message)
                    .singleChoice(arrayOf(getString(R.string.global_server), getString(R.string.chinese_server)), selected) { index ->
                        if (selected != index) { preferences.cloudServer = index; preferences.login = false; render() }
                    }.show()
            }
            4 -> confirm(getString(R.string.sync_prompt), getString(R.string.user_syns)) {
                // hb.u.q3: both outcomes are reported in a "Sync" message dialog.
                work(requiresNetwork = false) {
                    val source = cloud().pull(preferences.languageUserName, passwordHash())
                    if (source.isEmpty()) Feedback.Dialog(getString(R.string.user_syns), getString(R.string.code_500))
                    else {
                        LegacyCloudData(this, database).restore(source)
                        Feedback.Dialog(getString(R.string.user_syns), getString(R.string.syns_success))
                    }
                }
            }
            5 -> confirm(getString(R.string.upload_prompt), getString(R.string.user_update)) { prepareUpload() }
            6 -> deleteAccount()
        }
    }

    fun login(name: String, secret: String) {
        val hash = CloudAccountClient.passwordHash(secret)
        preferences.languageUserName = name
        preferences.putString("userpsw", hash)
        // hb.u.i3: the login answer is reported with toasts.
        work(requiresNetwork = false) {
            when (cloud().login(name, hash)) {
                CloudAccountClient.LoginResult.SIGNED_IN -> { preferences.login = true; Feedback.None }
                CloudAccountClient.LoginResult.ACCOUNT_CREATED -> { preferences.login = true; Feedback.Toast(getString(R.string.sign_up_success)) }
                CloudAccountClient.LoginResult.PASSWORD_REJECTED -> { preferences.login = false; Feedback.Toast(getString(R.string.wrong_psw)) }
                CloudAccountClient.LoginResult.UNKNOWN_RESPONSE -> { preferences.login = false; Feedback.Toast(getString(R.string.code_500)) }
            }
        }
    }

    private fun prepareUpload() {
        if (busy) return
        val sections = LegacyCloudData(this, database).uploadSections()
        if (sections["bookmark"].orEmpty().length > 48_000) {
            confirm(getString(R.string.bookmarks_data_to_long), getString(R.string.user_update)) {
                work { cloud().push(preferences.languageUserName, passwordHash(), sections + ("bookmark" to null)); uploaded() }
            }
        } else work { cloud().push(preferences.languageUserName, passwordHash(), sections); uploaded() }
    }

    private fun deleteAccount() {
        ViaDialog(activity).title(R.string.delete_account).message(R.string.delete_account_message)
            .input("", getString(R.string.hint_password), 1).negative(android.R.string.cancel)
            .positive(R.string.confirm_to_delete) { _, result ->
                val password = result.edit?.firstOrNull().orEmpty()
                if (password.isEmpty()) toast(getString(R.string.empty_password_info))
                else if (CloudAccountClient.passwordHash(password) != passwordHash()) toast(getString(R.string.wrong_psw))
                else work {
                    cloud().requestDeletion(preferences.languageUserName, passwordHash()); preferences.login = false
                    // hb.u.S2
                    Feedback.Dialog(getString(R.string.delete_account), getString(R.string.delete_account_request_submitted))
                }
            }.show()
    }

    private fun webDavRows(): List<SettingsRow> {
        val saved = configuration.read()
        val configured = saved?.isConfigured == true
        val title = if (configured) {
            val username = requireNotNull(saved).username
            getString(R.string.sync_webdav_user, username.take(3) + "*".repeat((username.length - 3).coerceAtLeast(0)))
        } else getString(R.string.sync_webdav)
        val synced = WebDavSyncRuntime.lastSynced(this)
        val summary = if (!configured) getString(R.string.no_configuration)
            else if (synced == 0L) getString(R.string.has_never_been_synced)
            else getString(R.string.synced_, if ((System.currentTimeMillis() - synced) / 60_000 < 1) getString(R.string.just_now)
                else DateUtils.getRelativeTimeSpanString(synced, System.currentTimeMillis(), 60_000, 65552))
        return listOf(
            SyncAccountRow(title, summary, configured),
            SettingsHeadingRow(getString(R.string.auto_sync)),
            SettingsToggleRow(3, getString(R.string.auto_sync), checked = saved?.autoSync == true, disabled = !configured),
            SettingsRow(4, getString(R.string.sync_data), selectedSections(saved?.sections ?: 0), disabled = !configured),
            SettingsRow(5, getString(R.string.sync_manually), disabled = !configured),
        )
    }

    /** nb.n.b3 preserves the flags and ID when the URL and username still match. */
    fun updateWebDav(result: Bundle) {
        val saved = configuration.read()
        val server = result.getString("URL").orEmpty().let { if (it.isNotEmpty() && !it.endsWith('/')) "$it/" else it }
        val username = result.getString("USERNAME").orEmpty()
        val same = saved != null && saved.baseUrl == server && saved.username == username
        val id = if (same) requireNotNull(saved).id else generateSequence { (0 until 4).map { ('a'..'z').random() }.joinToString("") }.first { it != saved?.id }
        configuration.save(SyncConfiguration(id, server, username, result.getString("PASSWORD").orEmpty(),
            result.getBoolean("DIGEST_AUTH"), result.getString("PATH").orEmpty(), saved?.flags ?: 81))
        render()
    }

    private fun chooseSections(saved: SyncConfiguration, operation: Operation) {
        val bits = intArrayOf(64, 16, 32)
        val labels = arrayOf(getString(R.string.sync_data_favorites), getString(R.string.sync_data_bookmarks), getString(R.string.sync_data_settings))
        val selected = BooleanArray(3) { saved.flags and bits[it] != 0 }
        ViaDialog(activity).title(R.string.sync_data).multipleChoice(labels, bits.indices.filter { selected[it] }.toIntArray())
            .positive(android.R.string.ok) { _, result ->
                val chosen = result.selected?.toSet().orEmpty()
                val flags = bits.indices.filter { it in chosen }.fold(saved.flags and 112.inv()) { value, index -> value or bits[index] }
                configuration.save(saved.copy(flags = flags)); render()
                if (flags and 112 != 0) synchronize(operation)
            }.negative(android.R.string.cancel).show()
    }

    private fun chooseOperation() {
        val labels = arrayOf(getString(R.string.sync_push_description), getString(R.string.sync_pull_description), getString(R.string.sync_merge_description))
        ViaDialog(activity).title(R.string.sync_manually).items(labels, onClick = { index ->
            when (index) {
                0 -> confirm(getString(R.string.sync_push_confirm), getString(R.string.title_warning)) { synchronize(Operation.PUSH) }
                1 -> confirm(getString(R.string.sync_pull_confirm), getString(R.string.title_warning)) { synchronize(Operation.PULL) }
                else -> synchronize(Operation.SYNC)
            }
        }).show()
    }

    private fun synchronize(operation: Operation) {
        val saved = configuration.read()?.takeIf { it.isConfigured } ?: return
        if (System.currentTimeMillis() - lastSyncRequest < 1000L) return
        lastSyncRequest = System.currentTimeMillis()
        if (saved.sections == 0) { chooseSections(saved, operation); return }
        if (busy) return
        busy = true
        WebDavSyncRuntime.run(this, operation) { result ->
            busy = false
            if (closed || activity.isFinishing || activity.isDestroyed) return@run
            result.onSuccess { failures ->
                if (failures.isEmpty()) { toast(getString(R.string.sync_done)); render() }
                else ViaDialog(activity).title(R.string.sync_failed)
                    .message(failures.joinToString("\n") { it.description }).positive(android.R.string.ok).show()
            }.onFailure { android.util.Log.d("ViaSync", "Cannot sync data", it) }
        }
    }

    /** How hb.u reports a finished request. */
    private sealed interface Feedback {
        object None : Feedback
        class Toast(val text: String) : Feedback
        class Dialog(val title: String, val message: String) : Feedback
    }

    /** hb.u.Z2: a4's upload callback. */
    private fun uploaded() = Feedback.Dialog(getString(R.string.dialog_message), getString(R.string.upload_success))

    /**
     * z8.u0 (GET, [requiresNetwork] false) / z8.a4 (POST): a4 does nothing without a network, u0 reports a timeout;
     * a non-200 answer is "Service unavailable" and any other failure is reported as a timeout.
     */
    private fun work(requiresNetwork: Boolean = true, task: suspend () -> Feedback) {
        if (busy) return
        if (!dev.ujhhgtg.via.downloads.DownloadNetwork.isAvailable(this)) {
            if (!requiresNetwork) message(getString(R.string.dialog_message), getString(R.string.conn_timeout))
            return
        }
        busy = true
        // hb.u -> z8.u0.o / a4.j -> w5.k.X: this is the custom spinner row,
        // not the message body (which applies a different minimum height).
        val progress = ViaDialog(activity).progress(R.string.wait_a_moment)
            .cancelable(false).canceledOnTouchOutside(false)
        cloudProgress = progress
        progress.show()
        worker.execute {
            val result = runBlocking { runCatching { task() } }
            activity.runOnUiThread {
                busy = false
                progress.dismiss()
                cloudProgress = null
                if (closed || activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                result.onSuccess { feedback ->
                    when (feedback) {
                        is Feedback.Toast -> toast(feedback.text)
                        is Feedback.Dialog -> message(feedback.title, feedback.message)
                        Feedback.None -> Unit
                    }
                    render()
                }.onFailure { error ->
                    android.util.Log.d("ViaSync", "Cloud request failed", error)
                    message(getString(R.string.dialog_message), getString(
                        if (error is CloudAccountClient.ServiceUnavailable) R.string.code_500 else R.string.conn_timeout))
                }
            }
        }
    }
    private fun selectedSections(flags: Int): String = buildList {
        if (flags and 64 != 0) add(getString(R.string.sync_data_favorites))
        if (flags and 16 != 0) add(getString(R.string.sync_data_bookmarks))
        if (flags and 32 != 0) add(getString(R.string.sync_data_settings))
    }.joinToString(getString(R.string.delimiter)).ifEmpty { getString(R.string.sync_data_none) }
    private fun cloud() = CloudAccountClient(CloudAccountClient.Endpoints.original(preferences.cloudServer != 1))
    private fun passwordHash() = preferences.getString("userpsw", "").orEmpty()
    private fun confirm(message: String, title: String? = null, action: () -> Unit) {
        ViaDialog(activity).apply { title?.let { this.title(it) } }.message(message)
            .positive(android.R.string.ok) { _, _ -> action() }.negative(android.R.string.cancel).show()
    }
    private fun toast(value: String) = ViaToast.makeText(this, value, ViaToast.LENGTH_LONG).show()
    /** g6.n.m */
    private fun message(title: String, message: String) = ViaDialog(activity).title(title).message(message).positive(android.R.string.ok).show()
    override fun close() {
        if (closed) return
        closed = true
        cloudProgress?.dismiss()
        cloudProgress = null
        worker.execute { database.close() }; worker.shutdown()
    }
}
