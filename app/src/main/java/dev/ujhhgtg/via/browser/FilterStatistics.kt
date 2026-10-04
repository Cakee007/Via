package dev.ujhhgtg.via.browser

import dev.ujhhgtg.via.data.BrowserPreferences
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/** c8.ua.j/k/U1/c2: one browser presenter's unflushed filter totals. */
internal class FilterStatistics(private val preferences: BrowserPreferences) {
    private var blockedRequests = 0
    private var savedKiB = 0

    fun record(url: String) {
        blockedRequests++
        savedKiB += estimateKiB(url)
    }

    fun flush() {
        if (blockedRequests <= 0) return
        // w9.k.B2/w2 perform separate preference writes, then ua.c2 clears both counters.
        preferences.adBlockedTimes += blockedRequests
        preferences.putLong("savedata", preferences.getLong("savedata") + savedKiB.toLong())
        blockedRequests = 0
        savedKiB = 0
    }

    companion object {
        /** z8.b0.C/I/h: a deterministic URL-based KiB estimate, not measured network bytes. */
        fun estimateKiB(url: String): Int {
            val hash = try {
                val digest = MessageDigest.getInstance("MD5").digest(url.toByteArray(Charsets.UTF_8))
                buildString {
                    for (byte in digest) {
                        val hex = Integer.toHexString(byte.toInt() and 0xff)
                        if (hex.length == 1) append('0')
                        append(hex)
                    }
                }
            } catch (_: NoSuchAlgorithmException) {
                url.hashCode().toString()
            }
            var size = Integer.parseInt(hash.substring(0, 3), 16) +
                Integer.parseInt(hash.substring(hash.length / 2 - 3, hash.length / 2), 16) +
                Integer.parseInt(hash.substring(hash.length - 3), 16)
            do {
                size /= 2
            } while (size > 80)
            return size
        }
    }
}
