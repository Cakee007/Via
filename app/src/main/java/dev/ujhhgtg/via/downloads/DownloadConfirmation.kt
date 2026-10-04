package dev.ujhhgtg.via.downloads

import android.app.Activity
import android.webkit.URLUtil
import android.widget.EditText
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.common.LocalNetworkAccess
import dev.ujhhgtg.via.ui.dialog.ViaDialog

/** Original mark.via.download.m/e.xml confirmation and b1.q4 manual entry. */
object DownloadConfirmation {
    fun showManual(activity: Activity, coordinator: DownloadCoordinator, onRequest: ((DownloadRequest) -> Unit)? = null): ViaDialog {
        val dialog = ViaDialog(activity).title(R.string.action_new)
            .input("https://", activity.getString(R.string.hint_url), 1, 0)
            .input("", activity.getString(R.string.file_name), 1, 1)
            .negative(android.R.string.cancel)
            .positive(R.string.action_download_file) { _, result ->
                val target = result.edit?.getOrNull(0).orEmpty().trim()
                if (!URLUtil.isNetworkUrl(target) || !dev.ujhhgtg.via.browser.UrlParser(target).isValid) {
                    ViaToast.makeText(activity, R.string.cannot_work, ViaToast.LENGTH_SHORT).show()
                    return@positive
                }
                val name = result.edit?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
                    ?: DownloadFiles.name(target, null, null)
                val mime = DownloadMimeTypes.mime(name.substringAfterLast('.', ""), "application/octet-stream")
                val request = DownloadRequest(target, fileName = name, mimeType = mime, directory = BrowserPreferences(activity).downloadDirectory)
                LocalNetworkAccess.check(activity, target) { allowed ->
                    if (activity.isFinishing || activity.isDestroyed) return@check
                    if (!allowed) ViaToast.show(activity, R.string.title_permission_denied)
                    else if (onRequest != null) onRequest(request) else enqueue(activity) { coordinator.enqueue(request) }
                }
            }
        dialog.show()
        return dialog
    }

    fun show(activity: Activity, coordinator: DownloadCoordinator, request: DownloadRequest, totalSize: Long = 0,
        onRequest: ((DownloadRequest) -> Unit)? = null, onStarted: ((Long) -> Unit)? = null): DownloadConfirmationFragment = DownloadConfirmationFragment.newInstance(request, totalSize).also { dialog ->
        dialog.onStarted = onStarted
        dialog.onRequest = onRequest
        dialog.show((activity as androidx.fragment.app.FragmentActivity).supportFragmentManager, "DownloadConfirmation")
    }

    internal fun selectBaseName(edit: EditText) {
        val name = edit.text.toString()
        val dot = name.lastIndexOf('.')
        if (dot < 0 || name.length - dot >= 7) edit.selectAll() else edit.setSelection(0, dot)
    }

    private inline fun enqueue(activity: Activity, block: () -> Long) {
        runCatching(block).onFailure { ViaToast.makeText(activity, it.message ?: activity.getString(R.string.download_failed), ViaToast.LENGTH_LONG).show() }
    }
}
