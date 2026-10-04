package dev.ujhhgtg.via.downloads

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.webkit.URLUtil
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.common.LocalNetworkAccess
import dev.ujhhgtg.via.common.launchIo
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.ExternalDownloadManagers
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog

/** c8.s6.Xa/na/f8/B4/Q4/M8. */
class DownloadDestinationController(private val fragment: Fragment, private val onStarted: (Long) -> Unit) {
    /** s6.Xa's page-side downloads (blob: and wormhole streams); returns true when the page takes over. */
    var interceptPageDownload: ((DownloadRequest, ExternalDownloadManagers.Manager?) -> Boolean)? = null
    private data class Pending(val request: DownloadRequest, val length: Long)
    private var pending: Pending? = null
    private val notifications = fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val tree = fragment.registerForActivityResult(object : ActivityResultContracts.OpenDocumentTree() {
        override fun createIntent(context: Context, input: Uri?): Intent = super.createIntent(context, input)
            .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }) { uri ->
        if (uri != null) {
            fragment.requireContext().contentResolver.takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            BrowserPreferences(fragment.requireContext()).downloadDirectory = uri.toString()
            val previous = pending
            pending = null
            // Original M8 re-enters Xa, so permission selection returns to the confirmation dialog.
            if (previous != null) show(previous.request, previous.length)
        }
    }

    fun show(request: DownloadRequest, length: Long = request.contentLength) {
        if (!fragment.isAdded) return
        val context = fragment.requireContext()
        val manager = selectedManager(context)
        // Xa delegates only actual third-party editor activities before showing Via's dialog.
        if (manager != null && manager.packageName != "system" && manager.packageName != "rpc" &&
            ExternalDownloadManagers.sendLink(context, manager, request.url)) return
        // The confirmation dialog may issue HEAD immediately, before the Download button.
        LocalNetworkAccess.check(context, request.url) { allowed ->
            if (!fragment.isAdded || !fragment.isVisible || fragment.parentFragmentManager.isStateSaved) return@check
            if (allowed) showPrepared(request, length) else ViaToast.show(context, R.string.title_permission_denied)
        }
    }

    private fun showPrepared(request: DownloadRequest, length: Long) {
        val context = fragment.requireContext()
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            runCatching { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
        // Xa asks for notifications first, then hands blob/wormhole downloads to the page.
        if (interceptPageDownload?.invoke(request, selectedManager(context)) == true) return
        val directory = BrowserPreferences(context).downloadDirectory
        if (directory.startsWith("content://") && !context.contentResolver.persistedUriPermissions.any {
                it.uri == directory.toUri() && it.isWritePermission
            }) {
            reselect(Pending(request, length))
            return
        }
        DownloadConfirmation.show(fragment.requireActivity(), DownloadCoordinator.get(context), request, length,
            onRequest = ::submit, onStarted = onStarted)
    }

    private fun submit(request: DownloadRequest) {
        val context = fragment.requireContext()
        LocalNetworkAccess.check(context, request.url) { allowed ->
            if (!fragment.isAdded) return@check
            if (allowed) submitPrepared(request) else ViaToast.show(context, R.string.title_permission_denied)
        }
    }

    private fun submitPrepared(request: DownloadRequest) {
        val context = fragment.requireContext()
        val manager = selectedManager(context)
        if (manager?.packageName == "system") {
            if (!URLUtil.isNetworkUrl(request.url)) { ViaToast.show(context, R.string.cannot_download); return }
            val id = ExternalDownloadManagers.downloadWithSystem(context, request.url, request.fileName.orEmpty(), request.userAgent, request.mimeType)
            if (id > 0) ViaToast.show(context, R.string.download_pending, actionText = R.string.view_downloads) {
                ExternalDownloadManagers.openDownloads(context, "system")
            } else ViaToast.show(context, R.string.download_failed)
            return
        }
        // j1.g/b is retained as its original empty result; z1 currently registers no rpc choice.
        if (manager?.packageName == "rpc") {
            if (!URLUtil.isNetworkUrl(request.url)) ViaToast.show(context, R.string.cannot_download)
            return
        }
        val directory = BrowserPreferences(context).downloadDirectory
        val original = DownloadRecord(name = request.fileName.orEmpty(), url = request.url, mimeType = request.mimeType,
            path = request.directory ?: request.path ?: directory, fileUri = request.fileUri, totalSize = request.contentLength)
        fragment.viewLifecycleOwner.launchIo({ DownloadFiles.prepare(context.applicationContext, original, directory) }, { prepared ->
                when (prepared.status) {
                    2 -> ViaToast.show(context, R.string.download_failed)
                    3 -> ViaToast.show(context, R.string.title_permission_denied, actionText = R.string.grant) {
                        reselect(Pending(request, request.contentLength))
                    }
                    else -> {
                        val destination = prepared.record
                        val id = DownloadCoordinator.get(context).enqueue(request.copy(fileName = destination.name,
                            directory = destination.path, path = destination.path, fileUri = destination.fileUri))
                        if (id > 0) onStarted(id)
                    }
                }
            }, { android.util.Log.w("ViaDownloads", "Download destination preparation failed", it) })
    }

    private fun selectedManager(context: Context) = ExternalDownloadManagers.managers.firstOrNull {
        it.packageName == BrowserPreferences(context).downloadManager
    }

    private fun reselect(request: Pending) {
        pending = null
        val directory = BrowserPreferences(fragment.requireContext()).downloadDirectory
        if (!directory.startsWith("content://")) return
        pending = request
        ViaDialog(fragment.requireActivity()).title(R.string.title_permission_denied)
            .message(R.string.message_reselect_download_location)
            .positive(android.R.string.ok) { _, _ ->
                pending = request
                runCatching { tree.launch(directory.toUri()) }
            }.negative(android.R.string.cancel).show()
    }
}
