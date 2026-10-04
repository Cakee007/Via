package dev.ujhhgtg.via.data

import org.json.JSONObject

/** ba.c.i/h/g/j: decode current and both legacy SiteConf JSON representations in source priority. */
internal object SiteConfigurationCodec {
    fun decode(source: String?): JSONObject {
        val input = runCatching { JSONObject(source ?: "{}") }.getOrDefault(JSONObject())
        val current = input.has("flags")
        val namedUserAgent = input.has("uastring") || input.has("uachoice")
        if (!current && !namedUserAgent && !input.has("flag")) return JSONObject()
        val userAgentChoice = if (current || namedUserAgent) input.optInt("uachoice", -1000)
            else when (input.optInt("uac")) { 2 -> -3; 3 -> -4; 4 -> -6; 5 -> -8; 6 -> -999; else -> 0 }
        val userAgent = if (current || namedUserAgent) input.optString("uastring", "")
            else if (userAgentChoice > 0 || userAgentChoice <= -999) input.optString("ua", "") else ""
        val bits = if (current) intArrayOf(input.optInt("enabled"), input.optInt("flags")) else legacyFlags(input.optInt("flag"))
        return JSONObject().apply {
            if (userAgentChoice != -1000) put("uachoice", userAgentChoice)
            sanitizeUserAgent(userAgent).takeIf(String::isNotEmpty)?.let { put("uastring", it) }
            if (bits[0] != 0) put("enabled", bits[0])
            if (current && input.optInt("textsize") != 0) put("textsize", input.optInt("textsize"))
            put("flags", bits[1])
        }
    }

    /** ba.c.b: six packed two-bit values, followed by the clipboard's three-state value. */
    private fun legacyFlags(value: Int): IntArray {
        if (value == 0 || value shr 2 == 0) return intArrayOf(0, 0)
        var enabled = 0
        var flags = 0
        for (index in 0..5) {
            val pair = value shr (index * 2) and 3
            if (pair == 0) continue
            val bit = 1 shl index
            if (pair and 2 != 0) enabled = enabled or bit
            // The legacy image setting stored image blocking; the modern flag stores image loading.
            if (if (index == 2) pair and 1 == 0 else pair and 1 != 0) flags = flags or bit
        }
        val clipboard = value shr 12
        if (clipboard and 3 != 0) {
            if (clipboard == 3) { enabled = enabled or 128; flags = flags or 128 }
            else { enabled = enabled or 64; if (clipboard == 2) flags = flags or 64 }
        }
        return intArrayOf(enabled, flags)
    }

    /** ba.b.Q -> z8.b4.g; applies to both restored and edited user-agent strings. */
    fun sanitizeUserAgent(value: String): String = value.replace(Regex("[\\t\\n\\r]+"), " ").trim()
}
