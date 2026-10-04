package dev.ujhhgtg.via.platform

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import dev.ujhhgtg.via.BuildConfig
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.downloads.DownloadCoordinator
import dev.ujhhgtg.via.downloads.DownloadFiles
import dev.ujhhgtg.via.downloads.DownloadRepository
import dev.ujhhgtg.via.downloads.DownloadRequest
import dev.ujhhgtg.via.downloads.DownloadState
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import dev.ujhhgtg.via.common.launchIo
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** mark.via.Shell.c0 and y8.h: CN update metadata, downloads and checksum gate. */
object AppUpdates {
    private data class Update(val version: Int = 0, val url: String = "", val md5: String = "", val changelog: String = "", val taskId: Long = -1) {
        val available get() = version > BuildConfig.VERSION_CODE && url.isNotEmpty() && md5.isNotEmpty() && changelog.isNotEmpty()
        fun json(): JSONObject = JSONObject().put("task_id", taskId).put("version", version).put("url", url).put("md5", md5).put("changelog", changelog)
        fun same(other: Update) = version == other.version && url == other.url && md5.equals(other.md5, true)
    }
    private fun decode(value: String): Update = runCatching {
        val json = JSONObject(value)
        Update(json.optInt("version"), json.optString("url"), json.optString("md5"), json.optString("changelog"), json.optLong("task_id", -1))
    }.getOrDefault(Update())

    @SuppressLint("RequestInstallPackagesPolicy")
    fun check(activity: Shell) {
        activity.launchIo({
            val connection = URL("https://res.viayoo.com/v1/latest_cn.json").openConnection() as HttpURLConnection
            val update = try {
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "*/*")
                connection.setRequestProperty("Charset", "UTF-8")
                connection.setRequestProperty("Connection", "Keep-Alive")
                if (connection.responseCode == 200) connection.inputStream.bufferedReader(Charsets.UTF_8).use { decode(it.readLines().joinToString("")) } else Update()
            } catch (_: Exception) { Update() } finally { connection.disconnect() }
            update to completed(activity, update)
        }, { (update, uri) ->
                if (!update.available) ViaToast.makeText(activity, R.string.latest_version_already_installed, ViaToast.LENGTH_SHORT).show()
                else ViaDialog(activity).title(R.string.new_version_available).message(update.changelog).canceledOnTouchOutside(false)
                    .positive(if (uri == null) R.string.action_download_file else R.string.install) { _, _ ->
                        if (uri != null) runCatching { activity.startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                        else enqueue(activity, update)
                    }.negative(android.R.string.cancel).show()
        }, { android.util.Log.w("ViaUpdates", "Update check failed", it) })
    }

    private fun metadata(activity: Shell) = File(activity.filesDir, "update/update.json")
    private fun saved(activity: Shell) = runCatching { decode(metadata(activity).readText()) }.getOrNull()
    private fun completed(activity: Shell, update: Update): Uri? {
        val saved = saved(activity)?.takeIf { it.available && it.same(update) } ?: return null
        val record = DownloadCoordinator.get(activity).get(saved.taskId)?.takeIf { it.url == saved.url && it.isComplete } ?: return null
        val uri = DownloadFiles.uri(activity, record) ?: return null
        val checksum = runCatching {
            val digest = MessageDigest.getInstance("MD5")
            activity.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(8192)
                while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            } ?: return null
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
        if (saved.md5.equals(checksum, true)) return uri
        DownloadRepository(activity).update(record.copy(state = DownloadState.FAILED, errorMessage = "Update package checksum mismatch"))
        return null
    }

    private fun enqueue(activity: Shell, update: Update) {
        activity.launchIo({
            val queue = DownloadCoordinator.get(activity)
            val prior = saved(activity)?.takeIf { it.same(update) }?.let { queue.get(it.taskId) }
                ?.takeIf { it.url == update.url && System.currentTimeMillis() / 1000 - it.createdAt <= 86400 && !it.isComplete }
            val id = if (prior != null) { queue.resume(prior.id); prior.id } else queue.enqueue(DownloadRequest(update.url,
                fileName = "via_${update.version}.apk", mimeType = "application/vnd.android.package-archive", priority = 2))
            val file = metadata(activity); file.parentFile?.mkdirs()
            file.writeText(update.copy(taskId = id).json().toString())
            id
        }, {
            ViaToast.makeText(activity, R.string.download_pending, ViaToast.LENGTH_SHORT).show()
        }, { ViaToast.makeText(activity, R.string.toast_operation_failed, ViaToast.LENGTH_SHORT).show() })
    }
}
