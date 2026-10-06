package dev.ujhhgtg.via.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import dev.ujhhgtg.via.common.launchIo
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.SettingsData
import dev.ujhhgtg.via.data.SettingsDataRepository
import java.util.Locale

/** hb.c4's list, lifecycle refreshes and original numeric action routes. */
class GeneralSettingsFragment : SettingsListFragment() {
    private lateinit var preferences: BrowserPreferences
    private lateinit var rows: SettingsRowsAdapter
    private lateinit var handlers: SettingsController
    private var managers: List<ExternalDownloadManagers.Choice>? = null
    private var players: List<ExternalVideoPlayers.Choice>? = null
    private var agentTitle: String? = null
    private var pendingRequest = 0
    private val document = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (!::handlers.isInitialized) return@registerForActivityResult
        handlers.onActivityResult(pendingRequest, result.resultCode, result.data)
        refreshRows()
    }
    private val preferenceChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refreshRows() }

    override fun configureToolbar(toolbar: SettingsToolbar) = toolbar.setTitle(R.string.settings_general)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = BrowserPreferences(requireContext())
        handlers = SettingsController(requireActivity(),
            openPage = { (requireActivity() as Shell).openPage(it) },
            launchForResult = { intent, code -> pendingRequest = code; document.launch(intent) }, scopeOwner = viewLifecycleOwner, exportScopeOwner = this)
        handlers.restoreState(savedInstanceState)
        pendingRequest = savedInstanceState?.getInt("pending_request") ?: 0
        rows = SettingsRowsAdapter(::openRow)
        list.itemAnimator = null
        list.adapter = rows
        refreshRows()
        requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(preferenceChanged)
    }

    private fun refreshRows() {
        if (!::rows.isInitialized) return
        val orientationAllowed = Build.VERSION.SDK_INT < 36 || resources.configuration.smallestScreenWidthDp < 600
        val orientation = intArrayOf(R.string.follow_system, R.string.orientation_auto, R.string.orientation_portrait, R.string.orientation_landscape)
        val restored = intArrayOf(R.string.disable_restore, R.string.always_restore, R.string.ask_first)
        val manager = managers?.firstOrNull { it.id == preferences.downloadManager.orEmpty() } ?: managers?.firstOrNull()
        val player = players?.firstOrNull { it.packageName == preferences.videoPlayer.orEmpty() } ?: players?.firstOrNull()
        rows.submit(listOf(
            // hb.c4's first row is the Sync entry; cloud pull/upload actions belong inside it.
            SettingsRow(26, getString(R.string.sync)),
            SettingsRow(2, getString(R.string.agent), agentTitle),
            SettingsRow(3, getString(R.string.clear_data)),
            SettingsRow(4, getString(R.string.block_ads)),
            SettingsRow(5, getString(R.string.site_conf)),
            SettingsRow(36, getString(R.string.password_manager)),
            SettingsRow(6, getString(R.string.action_night)),
            SettingsRow(30, getString(R.string.reader_mode)),
            SettingsRow(32, getString(R.string.toolbars_settings)),
            SettingsRow(28, getString(R.string.customize_menu)),
            SettingsRow(37, getString(R.string.customize_context_menu)),
            SettingsRow(9, getString(R.string.language), languageTitle()),
            SettingsRow(10, getString(R.string.home), homeTitle()),
            SettingsRow(31, getString(R.string.search_settings)),
            SettingsRow(13, getString(R.string.orientation), getString(orientation[if (orientationAllowed) (preferences.screenOrientation - 1).coerceIn(0, 3) else 0]), !orientationAllowed),
            SettingsRow(14, getString(R.string.download), downloadTitle()),
            SettingsRow(15, getString(R.string.addon_download), manager?.label ?: getString(R.string.built_in_download_manager), managers == null),
            SettingsRow(27, getString(R.string.external_video_player), player?.label ?: getString(R.string.player_system_sharing), players == null),
            SettingsRow(18, getString(R.string.clear_data_on_exit)),
            SettingsRow(35, getString(R.string.font), preferences.uiFont),
            SettingsRow(19, getString(R.string.settings_operation), getString(R.string.operation_hint)),
            SettingsRow(20, getString(R.string.import_or_export_bookmarks)),
            SettingsRow(21, getString(R.string.import_data)),
            SettingsRow(22, getString(R.string.export_data), getString(R.string.export_data_description)),
            SettingsRow(23, getString(R.string.restore_tabs), getString(restored[preferences.restoreClosedTabs.coerceIn(0, 2)])),
            SettingsToggleRow(33, getString(R.string.show_toast_to_undo_closing_tab), getString(R.string.show_toast_to_undo_closing_tab_description), preferences.showUndoCloseTab),
            SettingsRow(24, getString(R.string.setting_default)),
        ))
    }

    private fun languageTitle(): String {
        val language = preferences.language.orEmpty()
        if (language.isEmpty()) return getString(R.string.follow_system)
        val locale = Locale.forLanguageTag(language)
        val name = locale.getDisplayLanguage(locale)
        val country = locale.getDisplayCountry(locale)
        return (if (country.isEmpty()) name else "$name($country)").takeUnless { it == language } ?: getString(R.string.follow_system)
    }

    private fun homeTitle(): String = when (preferences.home) {
        "about:home", "about:links" -> getString(R.string.default_set)
        "about:blank" -> getString(R.string.action_blank)
        "about:bookmarks" -> getString(R.string.action_bookmarks)
        else -> preferences.home
    }

    // The original prefixes the stored relative directory with a literal /sdcard/ here.
    @SuppressLint("SdCardPath")
    private fun downloadTitle(): String {
        val directory = preferences.downloadDirectory
        if (!directory.startsWith("content://")) return "/sdcard/$directory"
        val uri = directory.toUri()
        runCatching { requireContext().contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.takeIf(String::isNotEmpty) else null
        } }.getOrNull()?.let { return it }
        val document = runCatching { DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri)) }.getOrDefault(uri)
        return runCatching { requireContext().contentResolver.query(document, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } }.getOrNull() ?: directory
    }

    override fun onResume() { super.onResume(); refreshExternalApps(); refreshAgentTitle() }
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && ::preferences.isInitialized) { refreshRows(); refreshAgentTitle() }
    }

    /** Query external apps on IO and update rows within the view lifecycle. */
    private fun refreshExternalApps() {
        val context = requireContext().applicationContext
        viewLifecycleOwner.launchIo({ ExternalDownloadManagers.choices(context) }, { managers = it; refreshRows() }) { android.util.Log.w("ViaSettings", "Download manager query failed", it) }
        viewLifecycleOwner.launchIo({ ExternalVideoPlayers.choices(context) }, { players = it; refreshRows() }) { android.util.Log.w("ViaSettings", "Video player query failed", it) }
    }

    private fun refreshAgentTitle() {
        val context = requireContext().applicationContext
        val selected = preferences.userAgentChoice
        viewLifecycleOwner.launchIo({
            if (selected > 0) BrowserDatabase(context).use { database -> SettingsDataRepository(database).find(selected)?.takeIf { it.type == SettingsData.USER_AGENT }?.title.orEmpty() }
            else {
                val names = intArrayOf(R.string.default_set, R.string.agent_android_phone, R.string.agent_android_tablet, R.string.agent_windows_chrome,
                    R.string.agent_windows_ie11, R.string.agent_osx, R.string.agent_iphone, R.string.agent_ipad, R.string.agent_symbian)
                names.getOrNull(-selected)?.let(context::getString).orEmpty()
            }
        }, { agentTitle = it; refreshRows() }) { android.util.Log.w("ViaSettings", "User agent title query failed", it) }
    }

    private fun openRow(row: SettingsRow) {
        val shell = requireActivity() as Shell
        when (row.id) {
            2 -> shell.openPage("agent")
            3 -> handlers.route("clear_data")
            4 -> shell.openPage("block_ads")
            5 -> shell.openPage("site_conf")
            6 -> shell.openPage("action_night")
            9 -> handlers.route("language")
            10 -> handlers.route("home")
            13 -> handlers.route("orientation")
            14 -> handlers.route("download")
            15 -> handlers.route("addon_download")
            18 -> handlers.route("clear_data_on_exit")
            19 -> shell.openPage("settings_operation")
            20 -> handlers.route("import_or_export_bookmarks")
            21 -> handlers.route("import_data")
            22 -> handlers.route("export_data")
            23 -> handlers.route("restore_tabs")
            24 -> handlers.route("setting_default")
            26 -> shell.openPage("user_syns")
            27 -> handlers.route("external_video_player")
            28 -> shell.openPage("customize_menu")
            30 -> shell.openPage("reader_mode")
            31 -> shell.openPage("search_settings")
            32 -> shell.openPage("toolbars_settings")
            33 -> { preferences.showUndoCloseTab = !(row as SettingsToggleRow).checked; refreshRows() }
            35 -> shell.openPage("font")
            36 -> shell.openPage("password_manager")
            37 -> shell.openPage("customize_context_menu")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("pending_request", pendingRequest)
        if (::handlers.isInitialized) handlers.saveState(outState)
    }
    override fun onDestroyView() {
        requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(preferenceChanged)
        handlers.close()
        super.onDestroyView()
    }
}
