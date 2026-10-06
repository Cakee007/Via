package dev.ujhhgtg.via.extensions

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.common.applicationIoScope
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.downloads.DownloadStreamRegistry
import dev.ujhhgtg.via.engine.EnginePage
import dev.ujhhgtg.via.engine.ExtensionInstallError
import dev.ujhhgtg.via.engine.ExtensionManager
import dev.ujhhgtg.via.engine.ExtensionPrompt
import dev.ujhhgtg.via.engine.ExtensionUi
import dev.ujhhgtg.via.engine.InstallSource
import dev.ujhhgtg.via.engine.PopupHandle
import dev.ujhhgtg.via.skins.SkinResources
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import dev.ujhhgtg.via.ui.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The browser's side of [ExtensionManager]: permission prompts, popups, tabs opened by extensions, and
 * installs from `.xpi` links. Lives as long as the browser fragment.
 */
class ExtensionHost(
    private val fragment: Fragment,
    val manager: ExtensionManager,
    private val tabs: Tabs,
) : ExtensionUi {
    interface Tabs {
        fun openTab(active: Boolean): EnginePage?
        fun openUrl(url: String)
        fun select(page: EnginePage)
        fun close(page: EnginePage)
        /** Hands a staged link body to the standard download dialog. */
        fun download(link: LinkInstall)
        /** Shows the standard download dialog for an extension's `downloads.download`. */
        fun download(download: dev.ujhhgtg.via.engine.ExtensionDownload)
        fun openSettings()
    }

    /** A `.xpi` link response, staged in [DownloadStreamRegistry] under [streamId]. */
    class LinkInstall(val url: String, val userAgent: String?, val disposition: String?, val mime: String?,
        val size: Long, val streamId: String) {
        /** Set once the user chose Download, so the staged body is handed over instead of closed. */
        var downloading = false
    }

    private val context get() = fragment.requireActivity()
    /** Link installs waiting for Gecko's prompt, by the `file://` URI passed to install. */
    private val links = HashMap<String, LinkInstall>()

    fun attach() { manager.ui = this }
    fun detach() { if (manager.ui === this) manager.ui = null }

    // --- Link installs ---

    fun installLink(link: LinkInstall) {
        fragment.lifecycleScope.launch {
            val file = DownloadStreamRegistry.file(link.streamId)
            if (file == null) { DownloadStreamRegistry.close(link.streamId); toast(R.string.extension_error_network); return@launch }
            val uri = File(file.path).toURI().toString().replaceFirst("file:/", "file:///")
            links[uri] = link
            try {
                val installed = manager.install(uri, InstallSource.LINK)
                toast(context.getString(R.string.extension_installed, installed.name))
            } catch (error: ExtensionInstallError) {
                if (link.downloading) return@launch
                if (error.kind != ExtensionInstallError.Kind.CANCELED) {
                    // The file stays reachable when Gecko refuses it before any prompt.
                    showError(error, link)
                    return@launch
                }
            } catch (error: Throwable) {
                showError(ExtensionInstallError(ExtensionInstallError.Kind.OTHER, null, error), link)
                return@launch
            } finally {
                links.remove(uri)
            }
            DownloadStreamRegistry.close(link.streamId)
        }
    }

    private fun download(link: LinkInstall) {
        link.downloading = true
        tabs.download(link)
    }

    private fun showError(error: ExtensionInstallError, link: LinkInstall?) {
        if (!fragment.isAdded) { link?.let { DownloadStreamRegistry.close(it.streamId) }; return }
        val dialog = ViaDialog(context).title(R.string.extension_install_failed).message(errorMessage(context, error))
            .positive(android.R.string.ok)
        if (link != null) {
            dialog.neutral(R.string.action_download_file) { download(link) }
                .onDismiss { if (!link.downloading) DownloadStreamRegistry.close(link.streamId) }
        }
        dialog.show()
    }

    // --- ExtensionUi ---

    override fun onPrompt(prompt: ExtensionPrompt) {
        if (!fragment.isAdded) { prompt.deny(); return }
        val activity = context
        val name = prompt.extension.name
        val lines = ExtensionPermissions.lines(activity, prompt.permissions, prompt.origins)
        val data = ExtensionPermissions.dataLines(activity, prompt.dataCollection)
        val message = buildString {
            if (prompt.kind == ExtensionPrompt.Kind.INSTALL && prompt.extension.version.isNotEmpty())
                append(activity.getString(R.string.info_version)).append(": ").append(prompt.extension.version).append("\n\n")
            if (lines.isNotEmpty()) {
                append(activity.getString(when (prompt.kind) {
                    ExtensionPrompt.Kind.INSTALL -> R.string.extension_install_permissions
                    ExtensionPrompt.Kind.UPDATE -> R.string.extension_update_permissions
                    ExtensionPrompt.Kind.OPTIONAL -> R.string.extension_optional_request
                }, name))
                lines.forEach { append("\n• ").append(it) }
            }
            if (data.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append(activity.getString(R.string.extension_data_collection))
                data.forEach { append("\n• ").append(it) }
            }
        }.trim()
        val dialog = ViaDialog(activity).title(if (prompt.kind == ExtensionPrompt.Kind.INSTALL)
            activity.getString(R.string.extension_install_title, name) else name)
        if (message.isNotEmpty()) dialog.message(message)
        dialog.positive(when (prompt.kind) {
            ExtensionPrompt.Kind.INSTALL -> R.string.extension_add
            ExtensionPrompt.Kind.UPDATE -> R.string.update
            ExtensionPrompt.Kind.OPTIONAL -> R.string.allow
        }) { _, _ -> prompt.allow() }
        dialog.negative(android.R.string.cancel) { prompt.deny() }
        // Gecko reports the install source as the staged file's URI; match it by file name.
        val link = prompt.sourceUri?.substringAfterLast('/')?.let { name -> links.entries.firstOrNull { it.key.endsWith("/$name") }?.value }
        if (prompt.kind == ExtensionPrompt.Kind.INSTALL && link != null) {
            dialog.neutral(R.string.action_download_file) { prompt.deny(); download(link) }
        }
        dialog.canceledOnTouchOutside(false).onDismiss { prompt.deny() }.show()
    }

    override fun showPopup(title: String, createView: (Context) -> View, onClosed: () -> Unit): PopupHandle? {
        if (!fragment.isAdded) return null
        val activity = context
        val dialog = Dialog(activity, R.style.Via_Dialog)
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(activity).apply {
                text = title; setTypeface(typeface, Typeface.BOLD); textSize = 16f
                setTextColor(color(activity, R.attr.viaPrimaryTextColor, Color.BLACK))
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                setPadding(activity.dp(20f), activity.dp(16f), activity.dp(20f), activity.dp(8f))
            }, LinearLayout.LayoutParams(-1, -2))
            addView(createView(activity), LinearLayout.LayoutParams(-1, 0, 1f))
        }
        dialog.setContentView(content)
        dialog.window?.let { window ->
            window.setGravity(Gravity.BOTTOM)
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, activity.resources.displayMetrics.heightPixels * 3 / 5)
        }
        var closed = false
        dialog.setOnDismissListener { if (!closed) { closed = true; onClosed() } }
        dialog.show()
        return PopupHandle { if (dialog.isShowing) dialog.dismiss() }
    }

    override fun openTab(active: Boolean): EnginePage? = if (fragment.isAdded) tabs.openTab(active) else null
    override fun openUrl(url: String) { if (fragment.isAdded) tabs.openUrl(url) }
    override fun selectTab(page: EnginePage) { if (fragment.isAdded) tabs.select(page) }
    override fun closeTab(page: EnginePage) { if (fragment.isAdded) tabs.close(page) }

    override fun onDownload(download: dev.ujhhgtg.via.engine.ExtensionDownload) {
        if (!fragment.isAdded) {
            runCatching { download.body.close() }
            download.finish(dev.ujhhgtg.via.engine.ExtensionDownload.Outcome.CANCELED)
            return
        }
        tabs.download(download)
    }

    override fun onInstallFailed(error: ExtensionInstallError) { if (fragment.isAdded) showError(error, null) }

    override fun onProcessDisabled(restart: () -> Unit) {
        if (!fragment.isAdded) { restart(); return }
        ViaDialog(context).title(R.string.settings_extensions).message(R.string.extension_process_disabled)
            .positive(R.string.extension_restart) { _, _ -> restart() }.negative(android.R.string.cancel).show()
    }

    // --- Actions sheet ---

    /** The menu's Extensions entry: each extension's action for [page]'s tab. */
    fun showActions(page: EnginePage?) {
        if (!fragment.isAdded) return
        val activity = context
        val actions = page?.let(manager::actions).orEmpty()
        val dialog = ViaDialog(activity).title(R.string.settings_extensions)
        if (actions.isEmpty()) {
            dialog.message(if (BrowserPreferences(activity).extensionsEnabled) R.string.extension_actions_empty else R.string.extension_actions_off)
                .positive(R.string.settings) { _, _ -> tabs.openSettings() }
                .negative(android.R.string.cancel).show()
            return
        }
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        dialog.customView(ScrollView(activity).apply { addView(list) })
        actions.forEach { action ->
            val row = actionRow(activity, action.title ?: action.extensionName, action.badgeText, action.badgeBackground, action.badgeTextColor)
            row.alpha = if (action.enabled) 1f else .5f
            row.isEnabled = action.enabled
            row.setOnClickListener { dialog.dismiss(); manager.clickAction(page!!, action.extensionId) }
            list.addView(row, LinearLayout.LayoutParams(-1, activity.dp(56f)))
            fragment.lifecycleScope.launch {
                action.loadIcon(activity.dp(24f))?.let { (row.getChildAt(0) as ImageView).setImageBitmap(it) }
            }
        }
        dialog.negative(R.string.settings) { tabs.openSettings() }.show()
    }

    private fun actionRow(context: Context, title: String, badge: String?, badgeBackground: Int?, badgeText: Int?) =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(20f), 0, context.dp(20f), 0)
            background = ContextCompat.getDrawable(context, R.drawable.flat_ripple)
            addView(ImageView(context).apply {
                setImageDrawable(SkinResources.drawable(context, R.drawable.puzzle)?.mutate()?.apply {
                    setTint(color(context, R.attr.viaSecondaryTextColor, Color.GRAY))
                })
            }, LinearLayout.LayoutParams(context.dp(24f), context.dp(24f)))
            addView(TextView(context).apply {
                text = title; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
                setTextColor(color(context, R.attr.viaPrimaryTextColor, Color.BLACK))
                typeface = BrowserPreferences(context).selectedTypeface()
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = context.dp(16f) })
            if (!badge.isNullOrEmpty()) addView(TextView(context).apply {
                text = badge; textSize = 11f; gravity = Gravity.CENTER
                setTextColor(badgeText ?: Color.WHITE)
                setPadding(context.dp(6f), context.dp(1f), context.dp(6f), context.dp(1f))
                background = GradientDrawable().apply { cornerRadius = context.dp(8f).toFloat(); setColor(badgeBackground ?: 0xff5a5a5a.toInt()) }
            }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = context.dp(8f) })
        }

    private fun toast(resource: Int) = ViaToast.show(fragment.requireContext().applicationContext, resource)
    private fun toast(text: String) = ViaToast.makeText(fragment.requireContext().applicationContext, text, ViaToast.LENGTH_SHORT).show()

    companion object {
        internal fun color(context: Context, attribute: Int, fallback: Int): Int {
            val attributes = context.obtainStyledAttributes(intArrayOf(attribute))
            return try { attributes.getColor(0, fallback) } finally { attributes.recycle() }
        }

        fun errorMessage(context: Context, error: ExtensionInstallError): String = context.getString(when (error.kind) {
            ExtensionInstallError.Kind.NETWORK -> R.string.extension_error_network
            ExtensionInstallError.Kind.CORRUPT -> R.string.extension_error_corrupt
            ExtensionInstallError.Kind.UNSIGNED -> R.string.extension_error_unsigned
            ExtensionInstallError.Kind.INCOMPATIBLE -> R.string.extension_error_incompatible
            ExtensionInstallError.Kind.BLOCKLISTED -> R.string.extension_error_blocklisted
            else -> R.string.extension_install_failed
        })

        /** Installs a picked document. The copy is removed once Gecko has read it. */
        suspend fun installFile(context: Context, manager: ExtensionManager, uri: android.net.Uri) =
            withContext(Dispatchers.IO) { ExtensionFiles.copy(context, uri) }.let { file ->
                try { manager.install(file.toURI().toString().replaceFirst("file:/", "file:///"), InstallSource.FILE) }
                finally { applicationIoScope.launch { file.delete() } }
            }
    }
}
