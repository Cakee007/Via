package dev.ujhhgtg.via.engine.gecko

import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.OptIn
import dev.ujhhgtg.via.engine.DisabledReason
import dev.ujhhgtg.via.engine.EnginePage
import dev.ujhhgtg.via.engine.ExtensionAction
import dev.ujhhgtg.via.engine.ExtensionDownload
import dev.ujhhgtg.via.engine.ExtensionInfo
import dev.ujhhgtg.via.engine.ExtensionInstallError
import dev.ujhhgtg.via.engine.ExtensionManager
import dev.ujhhgtg.via.engine.ExtensionPrompt
import dev.ujhhgtg.via.engine.ExtensionUi
import dev.ujhhgtg.via.engine.InstallSource
import dev.ujhhgtg.via.engine.PopupHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ExperimentalGeckoViewApi
import org.mozilla.geckoview.GeckoPreferenceController
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * User-installed WebExtensions on the process runtime. Gecko owns installation state; Via keeps only
 * the app-wide switch and the unsigned-install preference, which [configure] applies.
 */
internal object GeckoExtensions : ExtensionManager {
    private const val TAG = "ViaGeckoExtensions"
    private const val SIGNATURES_PREF = "xpinstall.signatures.required"

    private var runtime: GeckoRuntime? = null
    /** Completes once the runtime exists and the installed list has been read. */
    private val attached = CompletableDeferred<Unit>()
    private val controller get() = runtime?.webExtensionController
    /** Installed non-built-in extensions by id, kept current from the add-on manager. */
    private val installed = LinkedHashMap<String, WebExtension>()
    /** Default actions, before any tab-specific override. */
    private val browserActions = HashMap<String, WebExtension.Action>()
    private val pageActions = HashMap<String, WebExtension.Action>()
    private val listeners = LinkedHashSet<() -> Unit>()
    private var enabledByApp = true
    private var allowUnsigned = false
    /** Installs started through [install]; their failures are reported to that caller only. */
    private var appInstalls = 0

    override var ui: ExtensionUi? = null

    /** Called once when the runtime is created. Main thread. */
    fun attach(created: GeckoRuntime) {
        runtime = created
        applySignaturePreference()
        val controller = created.webExtensionController
        controller.promptDelegate = prompts
        controller.setAddonManagerDelegate(addonManager)
        controller.setExtensionProcessDelegate(object : WebExtensionController.ExtensionProcessDelegate {
            override fun onDisabledProcessSpawning() {
                val target = ui
                if (target == null) controller.enableExtensionProcessSpawning()
                else target.onProcessDisabled { controller.enableExtensionProcessSpawning() }
            }
        })
        controller.list().accept({ list ->
            list.orEmpty().forEach(::track)
            installed.values.toList().forEach(::applyAppSwitch)
            attached.complete(Unit)
            changed()
        }, { error -> Log.w(TAG, "Cannot list extensions", error); attached.complete(Unit) })
    }

    override fun configure(enabled: Boolean, allowUnsigned: Boolean) {
        val signatureChanged = this.allowUnsigned != allowUnsigned
        this.allowUnsigned = allowUnsigned
        if (signatureChanged) applySignaturePreference()
        if (enabledByApp != enabled) {
            enabledByApp = enabled
            installed.values.toList().forEach(::applyAppSwitch)
        }
    }

    @OptIn(ExperimentalGeckoViewApi::class)
    private fun applySignaturePreference() {
        if (runtime == null) return
        GeckoPreferenceController.setGeckoPref(SIGNATURES_PREF, !allowUnsigned, GeckoPreferenceController.PREF_BRANCH_USER)
            .accept(null) { error -> Log.w(TAG, "Cannot set $SIGNATURES_PREF", error) }
    }

    /** The app-wide switch uses Gecko's embedder-disabled flag, which leaves the user's own switch alone. */
    private fun applyAppSwitch(extension: WebExtension) {
        val controller = controller ?: return
        val appDisabled = extension.metaData.disabledFlags and WebExtension.DisabledFlags.APP != 0
        if (appDisabled == !enabledByApp) return
        val result = if (enabledByApp) controller.enable(extension, WebExtensionController.EnableSource.APP)
        else controller.disable(extension, WebExtensionController.EnableSource.APP)
        result.accept({ updated -> updated?.let(::track); changed() }, { error -> Log.w(TAG, "Cannot switch ${extension.id}", error) })
    }

    override suspend fun setAllEnabled(enabled: Boolean) = configure(enabled, allowUnsigned)

    // --- Queries and management ---

    override suspend fun list(): List<ExtensionInfo> {
        attached.await()
        val controller = controller ?: return emptyList()
        controller.list().await().orEmpty().forEach(::track)
        // Gecko's own order changes between runs; keep the list stable.
        return installed.values.map(::info).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    override suspend fun install(uri: String, source: InstallSource): ExtensionInfo {
        val controller = controller ?: throw ExtensionInstallError(ExtensionInstallError.Kind.OTHER, null)
        val method = if (source == InstallSource.FILE) WebExtensionController.INSTALLATION_METHOD_FROM_FILE
        else WebExtensionController.INSTALLATION_METHOD_MANAGER
        appInstalls++
        try {
            val extension = controller.install(uri, method).await()
                ?: throw ExtensionInstallError(ExtensionInstallError.Kind.OTHER, null)
            track(extension)
            applyAppSwitch(extension)
            changed()
            return info(extension)
        } catch (error: Throwable) {
            throw installError(error)
        } finally {
            appInstalls--
        }
    }

    override suspend fun uninstall(id: String) {
        val extension = installed[id] ?: return
        controller?.uninstall(extension)?.await()
        forget(id)
        changed()
    }

    override suspend fun setEnabled(id: String, enabled: Boolean): ExtensionInfo {
        val controller = controller ?: throw IllegalStateException("No runtime")
        val extension = installed[id] ?: throw IllegalArgumentException(id)
        val updated = (if (enabled) controller.enable(extension, WebExtensionController.EnableSource.USER)
            else controller.disable(extension, WebExtensionController.EnableSource.USER)).await() ?: extension
        track(updated)
        changed()
        return info(updated)
    }

    override suspend fun update(id: String): ExtensionInfo? {
        val controller = controller ?: return null
        val extension = installed[id] ?: return null
        // Like install, the caller reports a failed update; the add-on manager's failure event stays quiet.
        appInstalls++
        val updated = try { controller.update(extension).await() } catch (error: Throwable) { throw installError(error) }
            finally { appInstalls-- }
            ?: return null
        track(updated)
        changed()
        return info(updated)
    }

    override suspend fun setOptionalPermission(id: String, permission: String?, origin: String?, granted: Boolean,
        dataCollection: String?): ExtensionInfo {
        val controller = controller ?: throw IllegalStateException("No runtime")
        val permissions = listOfNotNull(permission).toTypedArray()
        val origins = listOfNotNull(origin).toTypedArray()
        val data = listOfNotNull(dataCollection).toTypedArray()
        val updated = (if (granted) controller.addOptionalPermissions(id, permissions, origins, data)
            else controller.removeOptionalPermissions(id, permissions, origins, data)).await()
            ?: installed[id] ?: throw IllegalArgumentException(id)
        track(updated)
        changed()
        return info(updated)
    }

    override suspend fun icon(id: String, size: Int): Bitmap? =
        runCatching { installed[id]?.metaData?.icon?.getBitmap(size)?.await() }.getOrNull()

    override fun openOptions(id: String) {
        val extension = installed[id] ?: return
        val url = extension.metaData.optionsPageUrl?.takeIf(String::isNotEmpty) ?: return
        if (extension.metaData.openOptionsPageInTab) ui?.openUrl(url)
        else showPopup(extension) { session -> session.loadUri(url) }
    }

    override fun addListener(listener: () -> Unit) { listeners += listener }
    override fun removeListener(listener: () -> Unit) { listeners -= listener }
    private fun changed() = listeners.toList().forEach { it() }

    // --- Actions ---

    override fun actions(page: EnginePage): List<ExtensionAction> {
        val gecko = page as? GeckoPage
        return installed.values.filter { it.metaData.enabled }.mapNotNull { extension ->
            val action = resolvedAction(gecko, extension.id) ?: return@mapNotNull null
            ExtensionAction(extension.id, extension.metaData.name ?: extension.id, action.title,
                action.badgeText?.takeIf(String::isNotEmpty), action.badgeBackgroundColor, action.badgeTextColor,
                action.enabled != false) { size -> runCatching { action.icon?.getBitmap(size)?.await() }.getOrNull() }
        }
    }

    private fun resolvedAction(page: GeckoPage?, id: String): WebExtension.Action? {
        val browser = browserActions[id]?.let { default -> page?.browserActions?.get(id)?.withDefault(default) ?: default }
        if (browser != null) return browser
        return pageActions[id]?.let { default -> page?.pageActions?.get(id)?.withDefault(default) ?: default }
    }

    override fun clickAction(page: EnginePage, id: String) {
        resolvedAction(page as? GeckoPage, id)?.click()
    }

    private val extensionActions = object : WebExtension.ActionDelegate {
        override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
            browserActions[extension.id] = action
        }

        override fun onPageAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
            pageActions[extension.id] = action
        }

        override fun onTogglePopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? =
            popupSession(extension, action.title)

        override fun onOpenPopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? =
            popupSession(extension, action.title)
    }

    /** Gecko loads the popup URL into the returned open session. */
    private fun popupSession(extension: WebExtension, title: String?): GeckoResult<GeckoSession>? {
        var created: GeckoSession? = null
        showPopup(extension, title) { session -> created = session }
        return created?.let { GeckoResult.fromValue(it) }
    }

    private fun showPopup(extension: WebExtension, title: String? = null, start: (GeckoSession) -> Unit) {
        val runtime = runtime ?: return
        val target = ui ?: return
        val session = GeckoSession(GeckoSessionSettings.Builder().build())
        var handle: PopupHandle? = null
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onCloseRequest(session: GeckoSession) { handle?.close() }
        }
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> {
                // Links that leave the popup open as tabs, as Firefox for Android does.
                if (request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW) {
                    target.openUrl(request.uri)
                    handle?.close()
                    return GeckoResult.deny()
                }
                return GeckoResult.allow()
            }
        }
        session.open(runtime)
        val label = title?.takeIf(String::isNotEmpty) ?: extension.metaData.name ?: extension.id
        handle = target.showPopup(label, { context -> GeckoView(context).apply { setSession(session) } }) { session.close() }
        if (handle == null) { session.close(); return }
        start(session)
    }

    // --- Tabs ---

    private val extensionTabs = object : WebExtension.TabDelegate {
        override fun onNewTab(extension: WebExtension, details: WebExtension.CreateTabDetails): GeckoResult<GeckoSession> {
            val page = ui?.openTab(details.active != false) as? GeckoPage ?: return GeckoResult.fromValue(null)
            // Gecko opens the returned session and loads the requested URL into it itself.
            return GeckoResult.fromValue(page.adoptPopupSession())
        }

        override fun onOpenOptionsPage(extension: WebExtension) = openOptions(extension.id)
    }

    // --- Downloads ---

    /** `downloads.download` ids are Gecko's; the latest state per download is kept for onChanged deltas. */
    private var nextDownload = 1

    /**
     * Fetches the request on Gecko's network stack, so the extension's cookies and headers apply, then
     * hands the body to the app's download flow. Gecko learns the result through `downloads.onChanged`.
     */
    private val extensionDownloads = object : WebExtension.DownloadDelegate {
        override fun onDownload(extension: WebExtension, request: WebExtension.DownloadRequest): GeckoResult<WebExtension.DownloadInitData>? {
            val runtime = runtime ?: return GeckoResult.fromException(IllegalStateException("No runtime"))
            val target = ui ?: return GeckoResult.fromException(IllegalStateException("Downloads need the browser"))
            val controller = runtime.webExtensionController
            val download = controller.createDownload(nextDownload++)
                ?: return GeckoResult.fromException(IllegalStateException("Cannot create a download"))
            val name = request.filename?.substringAfterLast('/')?.takeIf(String::isNotEmpty)
            val started = System.currentTimeMillis()
            val state = DownloadState(request.request.uri, name, started)
            val flags = if (request.downloadFlags and GeckoWebExecutor.FETCH_FLAGS_PRIVATE != 0) GeckoWebExecutor.FETCH_FLAGS_PRIVATE
                else GeckoWebExecutor.FETCH_FLAGS_NONE
            GeckoWebExecutor(runtime).fetch(request.request, flags).accept({ response ->
                val body = response?.body
                if (response == null || body == null || (!request.allowHttpErrors && response.statusCode !in 200..299)) {
                    runCatching { body?.close() }
                    download.update(state.finish(WebExtension.Download.STATE_INTERRUPTED, 0,
                        WebExtension.Download.INTERRUPT_REASON_SERVER_FAILED))
                    return@accept
                }
                fun header(key: String) = response.headers.entries.firstOrNull { it.key.equals(key, true) }?.value
                state.mime = header("Content-Type")?.substringBefore(';')?.trim()
                state.total = header("Content-Length")?.toLongOrNull() ?: -1
                download.update(state)
                target.onDownload(ExtensionDownload(extension.metaData.name ?: extension.id, response.uri, name,
                    header("Content-Disposition"), state.mime, state.total, body) { outcome, bytes ->
                    // The app reports from any thread; Gecko's download state changes on the UI thread.
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        download.update(when (outcome) {
                            ExtensionDownload.Outcome.COMPLETE -> state.finish(WebExtension.Download.STATE_COMPLETE, bytes, null)
                            ExtensionDownload.Outcome.FAILED -> state.finish(WebExtension.Download.STATE_INTERRUPTED, 0,
                                WebExtension.Download.INTERRUPT_REASON_FILE_FAILED)
                            ExtensionDownload.Outcome.CANCELED -> state.finish(WebExtension.Download.STATE_INTERRUPTED, 0,
                                WebExtension.Download.INTERRUPT_REASON_USER_CANCELED)
                        })
                    }
                })
            }, { error ->
                Log.w(TAG, "Extension download failed: ${request.request.uri}", error)
                download.update(state.finish(WebExtension.Download.STATE_INTERRUPTED, 0,
                    WebExtension.Download.INTERRUPT_REASON_NETWORK_FAILED))
            })
            return GeckoResult.fromValue(WebExtension.DownloadInitData(download, state))
        }
    }

    /** The `downloads.DownloadItem` fields Gecko reads; [finish] moves it to a final state. */
    private class DownloadState(private val url: String, private val name: String?, private val started: Long) :
        WebExtension.Download.Info {
        var mime: String? = null
        var total: Long = -1
        private var state = WebExtension.Download.STATE_IN_PROGRESS
        private var received = 0L
        private var error: Int? = null
        private var ended: Long? = null

        fun finish(state: Int, bytes: Long, error: Int?): DownloadState {
            this.state = state
            this.error = error
            ended = System.currentTimeMillis()
            if (state == WebExtension.Download.STATE_COMPLETE) { received = bytes; if (total < 0) total = bytes }
            return this
        }

        override fun state() = state
        override fun startTime() = started
        override fun endTime() = ended
        override fun error() = error
        override fun bytesReceived() = received
        override fun totalBytes() = total
        override fun fileSize() = if (state == WebExtension.Download.STATE_COMPLETE) received else -1
        override fun fileExists() = state == WebExtension.Download.STATE_COMPLETE
        override fun filename() = name ?: url.substringBefore('?').substringAfterLast('/')
        override fun mime() = mime ?: ""
        override fun canResume() = false
        override fun paused() = false
        override fun referrer() = ""
    }

    /** Registers the per-tab delegates of every known extension on [page]'s [session]. */
    fun registerSession(page: GeckoPage, session: GeckoSession) {
        installed.values.forEach { register(page, session, it) }
    }

    private fun register(page: GeckoPage, session: GeckoSession, extension: WebExtension) {
        val controller = session.webExtensionController
        controller.setActionDelegate(extension, object : WebExtension.ActionDelegate {
            override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
                page.browserActions[extension.id] = action
            }

            override fun onPageAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
                page.pageActions[extension.id] = action
            }
        })
        controller.setTabDelegate(extension, object : WebExtension.SessionTabDelegate {
            override fun onUpdateTab(extension: WebExtension, session: GeckoSession, details: WebExtension.UpdateTabDetails): GeckoResult<AllowOrDeny> {
                if (details.active == true) ui?.selectTab(page)
                return GeckoResult.allow()
            }

            override fun onCloseTab(extension: WebExtension?, session: GeckoSession): GeckoResult<AllowOrDeny> {
                val target = ui ?: return GeckoResult.deny()
                target.closeTab(page)
                return GeckoResult.allow()
            }
        })
    }

    /** Keeps the newest state of [extension] and wires its delegates once. */
    private fun track(extension: WebExtension) {
        if (extension.isBuiltIn) return
        val known = installed.containsKey(extension.id)
        installed[extension.id] = extension
        if (known) return
        // Attaching an action delegate makes Gecko resend every action update, so a listener that lists
        // extensions again would loop. Delegates are kept per extension id: attach them once.
        extension.setActionDelegate(extensionActions)
        extension.tabDelegate = extensionTabs
        extension.downloadDelegate = extensionDownloads
        GeckoBackend.livePages().forEach { page -> page.currentSession?.let { register(page, it, extension) } }
    }

    private fun forget(id: String) {
        installed.remove(id)
        browserActions.remove(id)
        pageActions.remove(id)
        GeckoBackend.livePages().forEach { it.browserActions.remove(id); it.pageActions.remove(id) }
    }

    private val addonManager = object : WebExtensionController.AddonManagerDelegate {
        override fun onInstalled(extension: WebExtension) { track(extension); applyAppSwitch(extension); changed() }
        override fun onReady(extension: WebExtension) { track(extension); changed() }
        override fun onEnabled(extension: WebExtension) { track(extension); changed() }
        override fun onDisabled(extension: WebExtension) { track(extension); changed() }
        override fun onOptionalPermissionsChanged(extension: WebExtension) { track(extension); changed() }
        override fun onUninstalled(extension: WebExtension) { forget(extension.id); changed() }
        override fun onInstallationFailed(extension: WebExtension?, exception: WebExtension.InstallException) {
            if (appInstalls > 0) return
            val error = installError(exception)
            if (error.kind != ExtensionInstallError.Kind.CANCELED) ui?.onInstallFailed(error)
        }
    }

    // --- Prompts ---

    private val prompts = object : WebExtensionController.PromptDelegate {
        override fun onInstallPromptRequest(extension: WebExtension, permissions: Array<String>, origins: Array<String>,
            dataCollection: Array<String>): GeckoResult<WebExtension.PermissionPromptResponse> {
            val result = GeckoResult<WebExtension.PermissionPromptResponse>()
            ask(ExtensionPrompt.Kind.INSTALL, extension, permissions, origins, dataCollection) { allowed ->
                result.complete(WebExtension.PermissionPromptResponse(allowed, false, false))
            }
            return result
        }

        override fun onUpdatePrompt(extension: WebExtension, permissions: Array<String>, origins: Array<String>,
            dataCollection: Array<String>): GeckoResult<AllowOrDeny> = askAllowOrDeny(ExtensionPrompt.Kind.UPDATE,
                extension, permissions, origins, dataCollection)

        override fun onOptionalPrompt(extension: WebExtension, permissions: Array<String>, origins: Array<String>,
            dataCollection: Array<String>): GeckoResult<AllowOrDeny> = askAllowOrDeny(ExtensionPrompt.Kind.OPTIONAL,
                extension, permissions, origins, dataCollection)
    }

    private fun askAllowOrDeny(kind: ExtensionPrompt.Kind, extension: WebExtension, permissions: Array<String>,
        origins: Array<String>, dataCollection: Array<String>): GeckoResult<AllowOrDeny> {
        val result = GeckoResult<AllowOrDeny>()
        ask(kind, extension, permissions, origins, dataCollection) { result.complete(if (it) AllowOrDeny.ALLOW else AllowOrDeny.DENY) }
        return result
    }

    private fun ask(kind: ExtensionPrompt.Kind, extension: WebExtension, permissions: Array<String>, origins: Array<String>,
        dataCollection: Array<String>, respond: (Boolean) -> Unit) {
        val target = ui ?: return respond(false)
        target.onPrompt(ExtensionPrompt(kind, info(extension), extension.location, permissions.toList(), origins.toList(),
            dataCollection.toList(), respond))
    }

    // --- Mapping ---

    private fun info(extension: WebExtension): ExtensionInfo {
        val meta = extension.metaData
        val flags = meta.disabledFlags
        val reason = when {
            flags and WebExtension.DisabledFlags.SIGNATURE != 0 -> DisabledReason.UNSIGNED
            flags and (WebExtension.DisabledFlags.BLOCKLIST or WebExtension.DisabledFlags.SOFT_BLOCKLIST) != 0 -> DisabledReason.BLOCKLISTED
            flags and WebExtension.DisabledFlags.APP_VERSION != 0 -> DisabledReason.INCOMPATIBLE
            else -> null
        }
        return ExtensionInfo(
            id = extension.id,
            name = meta.name?.takeIf(String::isNotEmpty) ?: extension.id,
            version = meta.version,
            description = meta.description?.takeIf(String::isNotEmpty),
            creator = meta.creatorName?.takeIf(String::isNotEmpty),
            homepageUrl = meta.homepageUrl?.takeIf(String::isNotEmpty),
            amoUrl = meta.amoListingUrl?.takeIf(String::isNotEmpty),
            optionsUrl = meta.optionsPageUrl?.takeIf(String::isNotEmpty),
            optionsInTab = meta.openOptionsPageInTab,
            userEnabled = flags and WebExtension.DisabledFlags.USER == 0,
            enabled = meta.enabled,
            disabledReason = reason,
            permissions = meta.requiredPermissions.toList(),
            origins = meta.requiredOrigins.toList(),
            optionalPermissions = meta.optionalPermissions.toList(),
            grantedOptionalPermissions = meta.grantedOptionalPermissions.toList(),
            optionalOrigins = meta.optionalOrigins.toList(),
            grantedOptionalOrigins = meta.grantedOptionalOrigins.toList(),
            dataCollection = meta.requiredDataCollectionPermissions.toList(),
            optionalDataCollection = meta.optionalDataCollectionPermissions.toList(),
            grantedOptionalDataCollection = meta.grantedOptionalDataCollectionPermissions.toList(),
        )
    }

    private fun installError(error: Throwable): ExtensionInstallError {
        if (error is ExtensionInstallError) return error
        val install = error as? WebExtension.InstallException ?: error.cause as? WebExtension.InstallException
        val kind = when (install?.code) {
            WebExtension.InstallException.ErrorCodes.ERROR_NETWORK_FAILURE -> ExtensionInstallError.Kind.NETWORK
            WebExtension.InstallException.ErrorCodes.ERROR_CORRUPT_FILE, WebExtension.InstallException.ErrorCodes.ERROR_INCORRECT_HASH,
            WebExtension.InstallException.ErrorCodes.ERROR_FILE_ACCESS, WebExtension.InstallException.ErrorCodes.ERROR_UNEXPECTED_ADDON_TYPE,
            WebExtension.InstallException.ErrorCodes.ERROR_UNSUPPORTED_ADDON_TYPE,
            WebExtension.InstallException.ErrorCodes.ERROR_INCORRECT_ID -> ExtensionInstallError.Kind.CORRUPT
            WebExtension.InstallException.ErrorCodes.ERROR_SIGNEDSTATE_REQUIRED -> ExtensionInstallError.Kind.UNSIGNED
            WebExtension.InstallException.ErrorCodes.ERROR_INCOMPATIBLE,
            WebExtension.InstallException.ErrorCodes.ERROR_UNEXPECTED_ADDON_VERSION -> ExtensionInstallError.Kind.INCOMPATIBLE
            WebExtension.InstallException.ErrorCodes.ERROR_BLOCKLISTED,
            WebExtension.InstallException.ErrorCodes.ERROR_SOFT_BLOCKED -> ExtensionInstallError.Kind.BLOCKLISTED
            WebExtension.InstallException.ErrorCodes.ERROR_USER_CANCELED -> ExtensionInstallError.Kind.CANCELED
            WebExtension.InstallException.ErrorCodes.ERROR_POSTPONED -> ExtensionInstallError.Kind.POSTPONED
            else -> ExtensionInstallError.Kind.OTHER
        }
        return ExtensionInstallError(kind, install?.extensionName, error)
    }

    private suspend fun <T> GeckoResult<T>.await(): T? = suspendCancellableCoroutine { continuation ->
        accept({ value -> continuation.resume(value) },
            { error -> continuation.resumeWithException(error ?: IllegalStateException("GeckoResult failed")) })
    }
}
