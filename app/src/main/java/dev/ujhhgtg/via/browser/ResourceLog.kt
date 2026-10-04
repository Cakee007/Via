package dev.ujhhgtg.via.browser

import java.util.Locale

/** Metadata recorded by e8.j/d8.c/n9.c; u9.c.h presents the newest 64 entries. */
data class BrowserResource(
    val url: String,
    val extension: String,
    val blocked: Boolean,
    val timestamp: Long,
    val rangeFromStart: Boolean,
) {
    val isMedia: Boolean get() = rangeFromStart || extension.length in 2..5 && mediaExtensionHashes.contains(originalHash(extension))

    companion object {
        /** i6.i0.d scans backwards; a slash in a literal query URL can end this scan too. */
        internal fun extension(url: String): String {
            var end = url.length
            var start = -1
            for (index in url.lastIndex downTo 0) {
                when (url[index]) {
                    '#', '?' -> end = index
                    '.' -> if (start == -1 || start > end) start = index + 1
                    '/' -> {
                        if (start != -1 && start <= end && (index < 2 || url[index - 1] != '/' || url[index - 2] != ':')) break
                        return ""
                    }
                }
            }
            return if (start >= 0 && start < end) url.substring(start, end).lowercase(Locale.ROOT) else ""
        }

        private fun originalHash(value: String): Int {
            var hash = 0
            for (byte in value.toByteArray(Charsets.UTF_8)) {
                val mixed = ((hash xor (byte.toInt() and 255)) * 1540483477) and Int.MAX_VALUE
                hash = mixed xor (mixed ushr 15)
            }
            return hash
        }

        // i6.i0.r matches media extensions by i6.d0.b hash. Keep the original table.
        private val mediaExtensionHashes = setOf(
            3847354, 9568862, 81264632, 81930561, 107123353, 126179678, 140994747, 172819050,
            190100404, 313906034, 324664358, 349563905, 394057630, 401942050, 414455207, 415430052,
            428139183, 479113676, 512218191, 521295666, 560628411, 608526472, 622106444, 687482999,
            718140131, 733429268, 807282548, 831243175, 838714884, 843086363, 846958475, 851561760,
            855761738, 912646323, 917017690, 930611925, 937672157, 957034884, 985168431, 994678331,
            1000779705, 1014739318, 1103121146, 1108586424, 1123614135, 1155674324, 1161994020, 1215239210,
            1234200878, 1319876930, 1321096783, 1387028997, 1413036331, 1458270773, 1558932844, 1583569252,
            1625637477, 1734170793, 1739555145, 1740189627, 1768878331, 1778226052, 1792002250, 1809528708,
            1810343100, 1876522621, 1944662444, 1990761014, 1999275047, 2015764437, 2022745637, 2061805411,
            2072937858, 2075848807, 2093577775,
        )
    }
}

internal class ResourceLog {
    private val entries = ArrayList<BrowserResource>()
    private var mediaAvailable = false

    /** d8.d.i / d8.c.b/g/h: each page starts a fresh log and captures its sniffing exclusion. */
    @Synchronized fun startPage() {
        entries.clear()
        mediaAvailable = false
    }

    /** d8.c.a retains every request; only the document generator imposes the 64-row window. */
    @Synchronized fun add(url: String, blocked: Boolean, rangeFromStart: Boolean = false): Boolean {
        val entry = BrowserResource(url, BrowserResource.extension(url), blocked, System.currentTimeMillis(), rangeFromStart)
        entries.add(entry)
        if (entry.isMedia) mediaAvailable = true
        return mediaAvailable
    }

    @Synchronized fun snapshot(): List<BrowserResource> = entries.toList()
    @Synchronized fun hasMedia(): Boolean = mediaAvailable
    @Synchronized fun clear() { entries.clear(); mediaAvailable = false }
}
