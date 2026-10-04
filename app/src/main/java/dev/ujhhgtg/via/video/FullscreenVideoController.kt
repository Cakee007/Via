package dev.ujhhgtg.via.video

import android.webkit.ValueCallback
import android.webkit.WebView

/** The c8.pb fullscreen-video bridge. */
class FullscreenVideoController(
    private val controls: FullscreenVideoControls,
    private val webView: WebView,
    private val gesturesEnabled: Boolean,
    private val toolbarEnabled: Boolean,
    private val systemOrientation: Int,
) {
    fun bind() {
        controls.setSystemOrientation(systemOrientation)
        controls.setControllerCallback(null)
        webView.evaluateJavascript(VideoScripts.metadata(), ValueCallback { raw ->
            val metadata = parseMetadata(raw) ?: run {
                controls.setVideoSize(16, 9)
                return@ValueCallback
            }

            if (metadata.duration > 0f && controls.isShown) {
                controls.setSpeedAdjustable(true)
                controls.setProgressGestureEnabled(gesturesEnabled)
                controls.setControllerCallback(VideoBridge(webView, metadata.duration, metadata.rate))
            }
            controls.setVolumeGestureEnabled(gesturesEnabled)
            controls.setBrightnessGestureEnabled(gesturesEnabled)
            controls.setVideoSize(metadata.width, metadata.height)
            controls.setToolbarEnabled(toolbarEnabled)
        })
    }

    private data class Metadata(val duration: Float, val rate: Float, val width: Int, val height: Int)

    /** Mirrors pb.a's partial-default parser, including Infinity's -1 duration. */
    private fun parseMetadata(raw: String): Metadata? {
        var value = raw
        if (value.length > 1 && value.first() == value.last() && value.first() == '"') {
            value = value.substring(1, value.length - 1)
        }
        // String.split(",") in the original Java bytecode drops trailing empty fields.
        val parts = value.split(",").dropLastWhile { it.isEmpty() }
        if (parts.size <= 2) return null

        var duration = 0f
        var rate = 1f
        var width = 16
        var height = 9
        try {
            if (parts[0].equals("Infinity", ignoreCase = true)) {
                duration = -1f
            } else {
                duration = parts[0].toFloat()
                rate = parts[1].toFloat()
            }
            width = parts[2].toInt()
            height = parts[3].toInt()
        } catch (_: Exception) {
            // pb keeps defaults for fields which were not reached by the failure.
        }
        return Metadata(duration, rate, width, height)
    }
}
