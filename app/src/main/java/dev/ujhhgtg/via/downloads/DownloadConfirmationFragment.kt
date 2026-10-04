package dev.ujhhgtg.via.downloads

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.EditText
import android.widget.TextView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.common.LocalNetworkAccess
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialogFragment
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** mark.via.download.m: original layout, in-place HEAD metadata update and explicit copy action. */
class DownloadConfirmationFragment : ViaDialogFragment() {
    var onStarted: ((Long) -> Unit)? = null
    var onRequest: ((DownloadRequest) -> Unit)? = null
    private lateinit var request: DownloadRequest
    private var length = 0L
    private var overwritableName = true
    private lateinit var filename: EditText
    private lateinit var size: TextView
    private lateinit var copy: TextView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val source = state ?: requireArguments()
        val headers = source.getString("headers")?.let(::JSONObject)
        request = DownloadRequest(source.getString("url").orEmpty(), source.getString("userAgent"), source.getString("contentDisposition"),
            headers?.let { json -> json.keys().asSequence().associateWith { json.optString(it) } }.orEmpty(), source.getString("cookies"), source.getString("referer"),
            source.getString("fileName"), source.getString("mimeType"), source.getString("path"), source.getString("fileUri")?.let(android.net.Uri::parse),
            source.getInt("priority"), source.getString("directory"))
        length = source.getLong("contentLength")
        overwritableName = source.getBoolean("fileNameOverwrittable", request.fileName == null)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.download_confirmation, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        val data = request.url.startsWith("data:")
        if (data) {
            val mime = if (request.mimeType.isNullOrEmpty() || request.mimeType!!.contains("/*") || request.mimeType == "application/octet-stream")
                DownloadDataUrl.mime(request.url) else request.mimeType
            val extension = DownloadMimeTypes.extension(mime)
            var name = request.fileName
            if (name.isNullOrEmpty()) name = java.util.UUID.randomUUID().toString().replace("-", "") + (extension?.let { ".$it" } ?: "")
            else if (name.indexOf('.', maxOf(0, name.length - 7)) < 0 && extension != null) name += ".$extension"
            request = request.copy(mimeType = mime, fileName = name)
        }
        if (request.fileName == null) {
            val name = DownloadFiles.name(request.url, request.contentDisposition, request.mimeType)
            request = request.copy(fileName = name, mimeType = if (name.endsWith(".apk")) "application/vnd.android.package-archive" else request.mimeType)
        }
        fun font(child: View) {
            if (child is TextView) child.typeface = Typeface.create(BrowserPreferences(requireContext()).selectedTypeface(), child.typeface?.style ?: Typeface.NORMAL)
            if (child is ViewGroup) for (index in 0 until child.childCount) font(child.getChildAt(index))
        }
        font(view)
        filename = view.findViewById<EditText>(R.id.download_filename).apply {
            if (Build.VERSION.SDK_INT >= 35) isLocalePreferredLineHeightForMinimumUsed = false
            setText(request.fileName); setSelectAllOnFocus(true)
            setOnFocusChangeListener { _, focused -> if (focused) DownloadConfirmation.selectBaseName(this) }
        }
        size = view.findViewById<TextView>(R.id.download_size).apply { textDirection = View.TEXT_DIRECTION_LOCALE }
        copy = view.findViewById<TextView>(R.id.download_confirmation_copy).apply { setOnClickListener {
            (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(null, request.url))
            ViaToast.show(requireContext(), R.string.toast_copy_url_successful); dismiss()
        } }
        view.findViewById<View>(R.id.download_confirmation_cancel).setOnClickListener { dismiss() }
        view.findViewById<View>(R.id.download_confirmation_ok).setOnClickListener {
            val name = filename.text.toString().trim()
            val dot = name.lastIndexOf('.')
            val mime = if (dot >= 0) DownloadMimeTypes.mime(name.substring(dot + 1), "application/octet-stream") else request.mimeType
            val confirmed = request.copy(fileName = name, mimeType = mime, contentLength = length)
            LocalNetworkAccess.check(requireContext(), confirmed.url) { allowed ->
                if (!isAdded || this.view == null) return@check
                if (!allowed) { ViaToast.show(requireContext(), R.string.title_permission_denied); return@check }
                val submit = onRequest
                if (submit != null) submit(confirmed)
                else runCatching { DownloadCoordinator.get(requireContext()).enqueue(confirmed).also { onStarted?.invoke(it) } }
                    .onFailure { ViaToast.show(requireContext(), R.string.download_failed) }
                dismiss()
            }
        }
        refreshSize()
        if (URLUtil.isNetworkUrl(request.url) && length <= 0) probe()
        else if (data && length <= 0) { length = DownloadDataUrl.estimatedSize(request.url); copy.visibility = if (length > 1_019_904) View.GONE else View.VISIBLE; refreshSize() }
    }

    override fun onStart() { super.onStart(); dialog?.setCanceledOnTouchOutside(false) }
    private fun refreshSize() { size.text = getString(R.string.file_size, DownloadPresentation.size(length)) }
    private fun probe() {
        LocalNetworkAccess.check(requireContext(), request.url) { allowed ->
            if (allowed && isAdded && view != null) probePermitted()
        }
    }

    private fun probePermitted() {
        val activity = requireActivity()
        val initial = request
        val cookie = CookieManager.getInstance().getCookie(initial.url)
        io.execute {
            var connection: HttpURLConnection? = null
            val metadata = runCatching {
                connection = (URL(initial.url).openConnection() as HttpURLConnection).apply {
                    cookie?.let { setRequestProperty("Cookie", it) }; setRequestProperty("Referer", initial.url)
                    initial.userAgent?.let { setRequestProperty("User-Agent", it) }; requestMethod = "HEAD"; connect()
                }
                connection.takeIf { it.responseCode == 200 }?.let { Triple(it.contentLengthLong, it.contentType?.substringBefore(';'), it.getHeaderField("Content-Disposition")) }
            }.getOrNull()
            connection?.disconnect()
            if (metadata != null) activity.runOnUiThread {
                if (view == null || !isAdded) return@runOnUiThread
                length = metadata.first
                request = request.copy(mimeType = metadata.second ?: request.mimeType, contentDisposition = metadata.third ?: request.contentDisposition)
                if (overwritableName) {
                    val name = DownloadFiles.name(request.url, request.contentDisposition, request.mimeType)
                    request = request.copy(fileName = name, mimeType = if (name.endsWith(".apk")) "application/vnd.android.package-archive" else request.mimeType)
                }
                if (filename.text.toString() != request.fileName) {
                    filename.setText(request.fileName)
                    if (filename.hasFocus()) { filename.clearFocus(); filename.requestFocus() }
                }
                refreshSize(); copy.visibility = if (length > 64_198_568) View.GONE else View.VISIBLE
            }
        }
    }

    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); write(out, request, length, overwritableName) }
    companion object {
        private val io = java.util.concurrent.Executors.newCachedThreadPool()
        fun newInstance(request: DownloadRequest, length: Long) = DownloadConfirmationFragment().apply {
            arguments = Bundle().also { write(it, request, length, request.fileName == null) }
        }
        private fun write(out: Bundle, r: DownloadRequest, length: Long, overwritable: Boolean) {
            out.putString("url", r.url); out.putString("userAgent", r.userAgent); out.putString("contentDisposition", r.contentDisposition)
            out.putString("headers", JSONObject(r.headers).toString()); out.putString("cookies", r.cookies); out.putString("referer", r.referrer)
            out.putString("fileName", r.fileName); out.putString("mimeType", r.mimeType); out.putString("path", r.path); out.putString("fileUri", r.fileUri?.toString())
            out.putString("directory", r.directory); out.putInt("priority", r.priority); out.putLong("contentLength", length); out.putBoolean("fileNameOverwrittable", overwritable)
        }
    }
}
