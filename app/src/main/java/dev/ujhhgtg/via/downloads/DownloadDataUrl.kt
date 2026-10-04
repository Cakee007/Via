package dev.ujhhgtg.via.downloads

import android.util.Base64
import java.io.OutputStream
import java.net.URLDecoder

/** l5.a: original incremental decoder, MIME sniffing and sampled content-length estimate. */
internal object DownloadDataUrl {
    private data class Decoded(val bytes: ByteArray, val consumed: Int)
    private fun decode(value: String, base64: Boolean): Decoded {
        if (value.isEmpty()) return Decoded(byteArrayOf(), 0)
        var count = value.length
        var source = value
        var percent = value.lastIndexOf('%')
        if (percent > 0) {
            while (percent >= 1 && value[percent - 1] == '%') percent--
            if (percent > 0) { count = percent; source = value.substring(0, percent) }
        }
        if ('%' in source) source = runCatching { URLDecoder.decode(source, "UTF-8") }.getOrDefault(source).let {
            if (base64) it.replace(" ", "+").replace("\r\n", "").replace("\n", "") else it
        }
        if (!base64) return Decoded(source.toByteArray(Charsets.UTF_8), count)
        var attempts = minOf(source.length - 4, 12)
        do {
            try { return Decoded(Base64.decode(source, Base64.DEFAULT), count) }
            catch (_: IllegalArgumentException) { attempts--; count--; source = source.dropLast(1) }
        } while (attempts > 0)
        return Decoded(byteArrayOf(), count)
    }
    fun write(url: String, output: OutputStream): Boolean {
        if (!url.startsWith("data:")) return false
        var start = url.indexOf(',')
        if (start < 0) return false
        val base64 = url.indexOf(";base64") > 0
        start++
        var end = url.length
        while (start < end && url[start] == ' ') start++
        while (end > 0 && url[end - 1] == ' ') end--
        while (start < end) {
            val decoded = decode(url.substring(start, minOf(end, start + 1024)), base64)
            if (decoded.consumed == 0) break
            start += decoded.consumed
            try { output.write(decoded.bytes) } catch (_: java.io.IOException) { return false }
        }
        return true
    }
    fun estimatedSize(url: String): Long {
        val comma = url.indexOf(',')
        if (!url.startsWith("data:") || comma < 0) return 0
        val decoded = decode(url.substring(comma + 1, minOf(comma + 257, url.length)), url.indexOf(";base64") > 0)
        if (decoded.consumed <= 0) return 0
        return (decoded.bytes.size.toFloat() * ((url.length - comma - 1).toFloat() / decoded.consumed)).toLong()
    }
    fun mime(url: String): String? {
        if (!url.startsWith("data:")) return null
        val base64 = url.indexOf(";base64,")
        val comma = url.indexOf(',')
        if (comma < 0) return null
        val declared = url.substring(5, if (base64 > 0) base64 else comma).trim()
        if (declared.isNotEmpty() && declared != "application/octet-stream") return declared
        val sample = url.substring(comma + 1, minOf(comma + 145, url.length)).trim()
        val bytes = decode(sample, base64 > 0).bytes
        // l5.c.d checks exactly the first four bytes (zero-padded for a short sample).
        if (bytes.isNotEmpty()) {
            val magic = bytes.copyOf(4).joinToString("") { "%02X".format(it.toInt() and 255) }
            listOf("FFD8FF" to "image/jpeg", "89504E47" to "image/png", "47494638" to "image/gif",
                "49492A00" to "image/tiff", "424D" to "image/bmp", "504B0304" to "application/zip",
                "52617221" to "application/x-rar-compressed").firstOrNull { magic.contains(it.first) }?.let { return it.second }
        }
        return if (sample.contains("<svg ") || sample.contains("<SVG ") || sample.contains("%3Csvg") || sample.contains("%3CSVG")) "image/svg+xml" else null
    }
}
