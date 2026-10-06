package dev.ujhhgtg.via.engine

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Installed WebExtensions. Only backends with [Capabilities.webExtensions] provide one.
 * Call on the main thread; suspend functions resume there.
 */
interface ExtensionManager {
    /** Installed extensions, excluding the backend's own built-ins. */
    suspend fun list(): List<ExtensionInfo>
    /** Installs from a `file://` URI; throws [ExtensionInstallError]. */
    suspend fun install(uri: String, source: InstallSource): ExtensionInfo
    suspend fun uninstall(id: String)
    /** The user's own switch for one extension. */
    suspend fun setEnabled(id: String, enabled: Boolean): ExtensionInfo
    /** The app-wide switch. Each extension's own switch is kept and applies again when this turns back on. */
    suspend fun setAllEnabled(enabled: Boolean)
    /** The updated extension, or null when it is already current; throws [ExtensionInstallError]. */
    suspend fun update(id: String): ExtensionInfo?
    /** Grants or revokes one optional [permission], [origin] or [dataCollection] permission. */
    suspend fun setOptionalPermission(id: String, permission: String?, origin: String?, granted: Boolean,
        dataCollection: String? = null): ExtensionInfo
    suspend fun icon(id: String, size: Int): Bitmap?
    /** Opens the extension's options page, in a tab or a sheet as the extension asks. */
    fun openOptions(id: String)
    /** Toolbar actions for [page]'s tab, with tab-specific overrides applied. */
    fun actions(page: EnginePage): List<ExtensionAction>
    fun clickAction(page: EnginePage, id: String)
    /** Applies stored settings: [enabled] also covers extensions installed later. */
    fun configure(enabled: Boolean, allowUnsigned: Boolean)
    /** The activity-side UI; null while no browser is shown, in which case prompts are declined. */
    var ui: ExtensionUi?
    /** Called on the main thread when extensions are installed, removed, enabled or disabled; not for action updates. */
    fun addListener(listener: () -> Unit)
    fun removeListener(listener: () -> Unit)
}

enum class InstallSource { FILE, LINK }

data class ExtensionInfo(
    val id: String,
    val name: String,
    val version: String,
    val description: String?,
    val creator: String?,
    val homepageUrl: String?,
    val amoUrl: String?,
    val optionsUrl: String?,
    val optionsInTab: Boolean,
    /** The user's own switch. */
    val userEnabled: Boolean,
    /** Whether the extension actually runs. */
    val enabled: Boolean,
    val disabledReason: DisabledReason?,
    val permissions: List<String>,
    val origins: List<String>,
    val optionalPermissions: List<String>,
    val grantedOptionalPermissions: List<String>,
    val optionalOrigins: List<String>,
    val grantedOptionalOrigins: List<String>,
    val dataCollection: List<String>,
    val optionalDataCollection: List<String> = emptyList(),
    val grantedOptionalDataCollection: List<String> = emptyList(),
)

/** Why an extension the user enabled does not run. */
enum class DisabledReason { UNSIGNED, BLOCKLISTED, INCOMPATIBLE }

/** A browser or page action as it applies to one tab. */
class ExtensionAction(
    val extensionId: String,
    val extensionName: String,
    val title: String?,
    val badgeText: String?,
    val badgeBackground: Int?,
    val badgeTextColor: Int?,
    val enabled: Boolean,
    val loadIcon: suspend (size: Int) -> Bitmap?,
)

/** A permission question. Exactly one of [allow] or [deny] takes effect. */
class ExtensionPrompt(
    val kind: Kind,
    val extension: ExtensionInfo,
    /** Where an install comes from, such as the `file://` URI passed to [ExtensionManager.install]. */
    val sourceUri: String?,
    val permissions: List<String>,
    val origins: List<String>,
    val dataCollection: List<String>,
    private val respond: (Boolean) -> Unit,
) {
    enum class Kind { INSTALL, UPDATE, OPTIONAL }

    private var answered = false
    fun allow() = answer(true)
    fun deny() = answer(false)
    private fun answer(value: Boolean) {
        if (answered) return
        answered = true
        respond(value)
    }
}

class ExtensionInstallError(val kind: Kind, val extensionName: String?, cause: Throwable? = null) :
    Exception(kind.name, cause) {
    enum class Kind { NETWORK, CORRUPT, UNSIGNED, INCOMPATIBLE, BLOCKLISTED, CANCELED, POSTPONED, OTHER }
}

fun interface PopupHandle { fun close() }

/**
 * `downloads.download` from an extension. The engine has already requested the URL; the app owns [body]
 * and calls [finish] once, which the extension sees through `downloads.onChanged`.
 */
class ExtensionDownload(
    val extensionName: String,
    val url: String,
    /** The name the extension asked for, without directories; null lets [disposition] or the URL decide. */
    val fileName: String?,
    val disposition: String?,
    val mimeType: String?,
    /** -1 when unknown. */
    val size: Long,
    val body: InputStream,
    private val report: (Outcome, Long) -> Unit,
) {
    enum class Outcome { COMPLETE, FAILED, CANCELED }

    private val reported = AtomicBoolean(false)
    /** [bytes] is the saved size for [Outcome.COMPLETE]. Any thread. */
    fun finish(outcome: Outcome, bytes: Long = 0) { if (reported.compareAndSet(false, true)) report(outcome, bytes) }
}

/** What the engine asks of the app's activity. Main thread. */
interface ExtensionUi {
    fun onPrompt(prompt: ExtensionPrompt)
    /** Shows an extension popup or options page; [onClosed] runs once when it goes away. */
    fun showPopup(title: String, createView: (Context) -> View, onClosed: () -> Unit): PopupHandle?
    /** A blank tab for `tabs.create`; the engine loads its URL. */
    fun openTab(active: Boolean): EnginePage?
    fun openUrl(url: String)
    fun selectTab(page: EnginePage)
    fun closeTab(page: EnginePage)
    /** An extension started a download; the app saves [download] through its own download flow. */
    fun onDownload(download: ExtensionDownload)
    /** An install the app did not start, such as one from addons.mozilla.org, failed. */
    fun onInstallFailed(error: ExtensionInstallError)
    /** Extensions stopped after crashing repeatedly; [restart] starts them again. */
    fun onProcessDisabled(restart: () -> Unit)
}
