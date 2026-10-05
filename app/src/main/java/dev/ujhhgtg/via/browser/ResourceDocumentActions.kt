package dev.ujhhgtg.via.browser

import dev.ujhhgtg.via.engine.EnginePage
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.browser.filter.FilterRequest
import dev.ujhhgtg.via.common.applicationIoScope
import kotlinx.coroutines.launch
import dev.ujhhgtg.via.browser.filter.FilterStore
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.ExternalVideoPlayers
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import java.util.Locale

/** c8.s6.o9/Z5: the extra long-press actions belonging to log.html and res.html. */
class ResourceDocumentActions(private val activity: Activity, private val preferences: BrowserPreferences = BrowserPreferences(activity)) {
    private val store = FilterStore(activity)

    /** The caller appends these after Open in background / Open in new tab, before Copy / Share. */
    fun rows(url: String, mediaOnly: Boolean, filteringEnabled: Boolean, matchingRuleBlocks: Boolean): List<Pair<Int, String>> = buildList {
        if (!mediaOnly && filteringEnabled) {
            val custom = store.readCustom().lineSequence().map(String::trim).toSet()
            val domain = domainRule(url) in custom
            val link = linkRule(url) in custom
            // ua.T1 is !u4.a.r: subscription-only blocks have no custom-rule removal affordance.
            if (domain || link || !matchingRuleBlocks) {
                add((if (domain) 25 else 24) to activity.getString(if (domain) R.string.action_unblock_domain else R.string.action_block_domain))
                add((if (link) 27 else 26) to activity.getString(if (link) R.string.action_unblock_url else R.string.action_block_url))
            }
        }
        if (mediaOnly) add(19 to activity.getString(R.string.action_play_with))
        add(20 to activity.getString(R.string.menu_download))
        add(21 to activity.getString(R.string.action_delete_all))
    }

    /** Callbacks retain the browser's original download engine and originating resource tab. */
    fun perform(action: Int, url: String, title: String?, download: (String) -> Unit,
        clearLog: () -> Unit, reloadFilters: () -> Unit): Boolean {
        when (action) {
            19 -> ExternalVideoPlayers.open(activity, url, title, preferences.videoPlayer)
            20 -> download(url) // s6.Z5 supplies Content-Disposition: attachment, unknown size.
            21 -> ViaDialog(activity).title(R.string.action_delete_all).message(R.string.dialog_sure)
                .positive(android.R.string.ok) { _, _ -> clearLog() }.negative(android.R.string.cancel).show()
            24, 25, 26, 27 -> {
                val domainAction = action == 24 || action == 25
                val remove = action == 25 || action == 27
                val raw = if (domainAction) domainRule(url) else linkRule(url)
                if (remove) store.removeCustom(raw) else store.appendCustom(raw)
                reloadFilters()
                val label = if (domainAction) domain(url) else url.substring(url.indexOf("://") + 3)
                ViaToast.show(activity, activity.getString(if (remove) R.string.unblock_hint else R.string.block_hint, label))
            }
            else -> return false
        }
        return true
    }

    /** ua.u1 / s6.e0: one media URL opens the selected player; several show a URL chooser. */
    fun playSelection(entries: List<BrowserResource>, title: String?, onEmpty: () -> Unit = {}) {
        val urls = entries.filter { it.isMedia }.map { it.url }
        when (urls.size) {
            0 -> onEmpty()
            1 -> ExternalVideoPlayers.open(activity, urls[0], title, preferences.videoPlayer)
            else -> ViaDialog(activity).title(title.orEmpty()).items(urls.toTypedArray(), onClick = {
                ExternalVideoPlayers.open(activity, urls[it], title, preferences.videoPlayer)
            }, onLongClick = {
                (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText(null, urls[it]))
                ViaToast.show(activity, R.string.toast_copy_url_successful)
                true
            }).show()
        }
    }

    companion object {
        /** b5.c.y/m: these are separate domain and exact-link custom filter records. */
        fun domainRule(url: String) = "||${domain(url)}^"
        fun linkRule(url: String) = "|$url"
        private fun domain(url: String): String {
            var value = if (url.contains("://")) url.substringAfter("://") else url
            val slash = value.indexOf('/')
            if (slash > 0) value = value.substring(0, slash)
            val colon = value.lastIndexOf(':')
            if (colon > 0) value = value.substring(0, colon)
            return value
        }

        /** ua.T1 -> u4.a.r -> x4.a.q uses b5.c.k(url,null), not the current document's host. */
        fun filterRequest(url: String): FilterRequest {
            val filename = url.substringBefore('?').substringAfterLast('/')
            val dot = filename.indexOf('.')
            val extension = if (dot > 0) filename.substring(dot + 1).lowercase(Locale.ROOT).takeIf { it.isNotEmpty() && it.length <= 8 } else null
            val mime = when (extension) {
                "js" -> "application/javascript"
                "json" -> "application/json"
                "mht", "mhtml" -> "multipart/related"
                else -> extension?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) } ?: "application/octet-stream"
            }
            val mask = when {
                extension == "js" -> 32
                extension == "css" -> 128
                extension in listOf("otf", "ttf", "ttc", "woff", "woff2") -> 2048
                extension == "php" || mime == "application/octet-stream" -> 3312
                mime in listOf("application/javascript", "application/x-javascript", "text/javascript", "application/json") -> 32
                mime == "text/css" -> 128
                mime.startsWith("image/") -> 64
                mime.startsWith("audio/") || mime.startsWith("video/") -> 1024
                mime.startsWith("font/") -> 2048
                else -> 16
            }
            return FilterRequest(url, typeMask = mask or (if (url.startsWith("ws")) 8192 else 0))
        }
    }
}

/** c8.s6.sa/h8/L8/ya: image actions acquire bytes once, then save/share/scan the cached image. */
class ResourceImageActions(
    private val activity: Activity,
    private val worker: java.util.concurrent.Executor,
    private val onScan: (android.net.Uri) -> Unit,
) {
    private data class Request(val url: String, val referer: String?, val flags: Int)
    private val cached = HashMap<String, java.io.File>()
    private val pending = HashMap<String, Request>()

    fun perform(webView: EnginePage, url: String, flags: Int, secret: String) {
        if (url.isEmpty()) return
        val request = Request(url, webView.url, flags)
        if (url.startsWith("data:")) { acquire(request, url); return }
        if (url.startsWith("file://")) {
            url.toUri().path?.let { complete(request, java.io.File(it)) }
            return
        }
        cached[url]?.takeIf { it.isFile && it.lastModified() >= System.currentTimeMillis() - 86_400_000L }?.let {
            complete(request, it); return
        }
        if (webView.javaScriptEnabled) {
            pending[url] = request.copy(flags = flags or (pending[url]?.flags ?: 0))
            // Original page-context XHR retains cookies, headers and blob support; failure falls back to the native request.
            webView.evaluate(
                "(function(){var a=new XMLHttpRequest;a.open(\"GET\",__URL__,!0);a.responseType=\"blob\";a.onload=function(){if(200===a.status){var b=new FileReader;b.onloadend=function(){window.via.download(__SECRET__,__URL__,b.result)};b.readAsDataURL(a.response)}else window.via.download(__SECRET__,__URL__,\"\")};a.onerror=function(){window.via.download(__SECRET__,__URL__,\"\")};a.send()})();"
                    .replace("__URL__", org.json.JSONObject.quote(url)).replace("__SECRET__", org.json.JSONObject.quote(secret)), null)
        } else acquire(request, null)
        ViaToast.show(activity, R.string.wait_a_moment)
    }

    /** c8.s6.u.d consumes a pending image action before treating a bridge result as an ordinary download. */
    fun acceptDownload(url: String, data: String?): Boolean {
        val request = pending.remove(url) ?: return false
        acquire(request, data)
        return true
    }

    private fun acquire(request: Request, data: String?) {
        applicationIoScope.launch {
            val result = runCatching {
                val folder = java.io.File(activity.externalCacheDir ?: activity.cacheDir, "download").apply { mkdirs() }
                val hash = java.security.MessageDigest.getInstance("MD5").digest(request.url.toByteArray(Charsets.UTF_8))
                    .joinToString("") { String.format(Locale.ROOT, "%02x", it.toInt() and 255) }
                if (data?.startsWith("data:") == true) {
                    val mime = dataMime(data)
                    val suffix = dev.ujhhgtg.via.downloads.DownloadMimeTypes.extension(mime) ?: "png"
                    java.io.File(folder, "$hash.$suffix").also { file ->
                        try { file.outputStream().use { output -> writeDataUrl(data, output) } }
                        catch (failure: Exception) { file.delete(); throw failure }
                    }
                } else {
                    require(android.webkit.URLUtil.isNetworkUrl(request.url))
                    val path = android.net.Uri.decode(request.url).substringBefore('?')
                    val name = path.substringAfterLast('/')
                    val mime = dev.ujhhgtg.via.downloads.DownloadMimeTypes.mime(name.substringAfter('.', ""), "image/*")
                    val suffix = dev.ujhhgtg.via.downloads.DownloadMimeTypes.extension(mime)
                    val file = java.io.File(folder, hash + if (suffix == null) "" else ".$suffix")
                    val task = dev.ujhhgtg.via.downloads.DownloadRecord(
                        name = file.name, url = request.url,
                        headers = request.referer?.takeIf(String::isNotEmpty)?.let { mapOf("Referer" to it) }.orEmpty(),
                        mimeType = mime, fileUri = android.net.Uri.fromFile(file), chunks = 1, flags = 1,
                        state = dev.ujhhgtg.via.downloads.DownloadState.PAUSED,
                    )
                    // s6.h8/j4 executes the original download engine with its empty observer.
                    val result = dev.ujhhgtg.via.downloads.DownloadCoordinator.get(activity).downloadTransient(task)
                    if (!result.isComplete) { file.delete(); error(result.errorMessage ?: "Download failed") }
                    file
                }
            }
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                result.onSuccess { file -> if (!request.url.startsWith("data:")) cached[request.url] = file; complete(request, file) }
                    .onFailure { ViaToast.show(activity, R.string.download_failed) }
            }
        }
    }

    private fun complete(request: Request, file: java.io.File) {
        if (request.flags and SAVE != 0) worker.execute {
            val result = runCatching { saveToGallery(file, request.url.takeUnless { it.startsWith("data:") }) }
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                result.onSuccess { (uri, mime) ->
                    ViaToast.show(activity, R.string.image_saved_to_gallery, actionText = R.string.view_downloads) {
                        runCatching { activity.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW)
                            .setDataAndType(uri, mime).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                            .onFailure { ViaToast.show(activity, R.string.toast_operation_failed) }
                    }
                }.onFailure { ViaToast.show(activity, R.string.toast_operation_failed) }
            }
        }
        if (request.flags and SHARE != 0) {
            val mime = dev.ujhhgtg.via.downloads.DownloadMimeTypes.mime(file.extension, "image/*")
            val uri = dev.ujhhgtg.via.BrowserFileProvider.uri(activity, file)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).setType(mime)
                .putExtra(android.content.Intent.EXTRA_STREAM, uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { activity.startActivity(android.content.Intent.createChooser(intent, activity.getString(R.string.action_share))) }
                .onFailure { ViaToast.show(activity, R.string.toast_operation_failed) }
        }
        if (request.flags and SCAN != 0) onScan(android.net.Uri.fromFile(file))
    }

    /** c8.s6.ya (API 29+): MediaStore Images, Pictures/Via, original filename correction and failed-row cleanup. */
    private fun saveToGallery(file: java.io.File, source: String?): Pair<android.net.Uri, String> {
        check(file.isFile)
        val suffix = file.extension.takeIf(String::isNotEmpty)
        val mime = dev.ujhhgtg.via.downloads.DownloadMimeTypes.mime(suffix.orEmpty(), "image/*") ?: "image/*"
        var name = dev.ujhhgtg.via.downloads.DownloadFiles.name(source.orEmpty(), null, mime)
        val dot = name.lastIndexOf('.')
        if (suffix != null && dot > 0 && dot > name.length - suffix.length - 3 && name.substring(dot + 1) != suffix) {
            name = name.substring(0, dot + 1) + suffix
        }
        val resolver = activity.contentResolver
        val uri = requireNotNull(resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Via")
            }))
        try {
            requireNotNull(resolver.openOutputStream(uri)).use { output -> file.inputStream().use { it.copyTo(output) }; output.flush() }
            return uri to mime
        } catch (failure: Exception) {
            resolver.delete(uri, null, null)
            throw failure
        }
    }

    /** The image action and download confirmation share the same original l5.a/l5.c decoder. */
    private fun dataMime(data: String) = dev.ujhhgtg.via.downloads.DownloadDataUrl.mime(data)
    private fun writeDataUrl(data: String, output: java.io.OutputStream) {
        check(dev.ujhhgtg.via.downloads.DownloadDataUrl.write(data, output))
    }

    companion object { const val SHARE = 1; const val SCAN = 2; const val SAVE = 4 }
}
