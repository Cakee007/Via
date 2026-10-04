package dev.ujhhgtg.via.video

import android.webkit.WebView

/** c8.pb.a: immutable metadata plus page-side video operations. */
class VideoBridge(
    private val webView: WebView,
    override val duration: Float,
    override val playbackRate: Float,
) : FullscreenVideoControls.Controller {
    override fun playbackState(callback: (Int) -> Unit) {
        webView.evaluateJavascript(VideoScripts.playbackState()) { raw -> callback(parseInt(raw)) }
    }

    override fun seekBy(seconds: Int) {
        evaluate(VideoScripts.seekBy(seconds))
    }

    override fun setPlaybackRate(rate: Float) {
        evaluate(VideoScripts.setPlaybackRate(rate))
    }

    private fun evaluate(script: String) {
        if (script.isEmpty()) return
        try {
            webView.evaluateJavascript(script, null)
        } catch (_: Exception) {
        }
    }

    private fun parseInt(raw: String): Int {
        var value = raw
        if (value.length > 1 && value.first() == value.last() && value.first() == '"') {
            value = value.substring(1, value.length - 1)
        }
        return try {
            value.toInt()
        } catch (_: Exception) {
            0
        }
    }
}
