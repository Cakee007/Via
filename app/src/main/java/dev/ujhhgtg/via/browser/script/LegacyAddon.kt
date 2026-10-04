package dev.ujhhgtg.via.browser.script

import android.util.Base64
import org.json.JSONObject
import java.util.Scanner

/** n9.a, z8.b0.s and ia.d.c/e: the encoded payload sent by the original addon site. */
data class LegacyAddon(
    val id: Int = 0,
    val originalId: Int = 0,
    val name: String? = null,
    val author: String? = null,
    val url: String? = null,
    val info: String? = null,
    val code: String? = null,
    val flags: Int = 0,
) {
    fun toScript(): UserScript? {
        if (url.isNullOrEmpty() || code.isNullOrEmpty()) return null
        val title = name?.takeIf { it.isNotEmpty() } ?: info?.takeIf { it.isNotEmpty() }
            ?: "Untitled Script - ${maxOf(id, originalId) + 10000}"
        val source = StringBuilder("// ==UserScript==\n")
            .append("// @name         ").append(title).append('\n')
            .append("// @namespace    https://viayoo.com/\n")
            .append("// @version      0.1\n")
        if (originalId > 0) source.append("// @homepageURL  https://app.viayoo.com/addons/").append(originalId).append('\n')
        if (!author.isNullOrEmpty()) source.append("// @author       ").append(author).append('\n')
        source.append("// @run-at       document-end\n")
        for (match in url.split(',').map(String::trim)) {
            if (match.isEmpty()) continue
            source.append("// @match        ")
            if (match[0] != '*') source.append('*')
            source.append(match)
            if (match.last() != '*') source.append('*')
            source.append('\n')
        }
        source.append("// @grant        none\n// ==/UserScript==\n")
        val decoded = try { String(Base64.decode(code, Base64.DEFAULT), Charsets.UTF_8) }
            catch (error: Exception) { android.util.Log.w("ViaScripts", "Cannot decode addon", error); return null }
        // ia.d.c's closing-metadata branch jumps to reset the flag (confirmed in smali).
        var metadata = false
        Scanner(decoded).use { scanner ->
            while (scanner.hasNextLine()) {
                val line = scanner.nextLine().trim()
                when {
                    line.startsWith("// ==UserScript==") -> metadata = true
                    line.startsWith("// ==/UserScript==") -> metadata = false
                    !metadata -> source.append(line).append('\n')
                }
            }
        }
        return UserScript.parse(source.toString())?.copy(enabled = flags and 1 == 0)
    }

    companion object {
        fun decode(payload: String?): LegacyAddon? {
            if (payload == null || payload.length <= 10) return null
            return try {
                val json = JSONObject(String(Base64.decode(payload, Base64.DEFAULT), Charsets.UTF_8).trim())
                if (json.isNull("mark") || json.isNull("code")) null
                else LegacyAddon(originalId = json.optInt("mark"), name = json.optString("name"),
                    author = json.optString("author"), url = json.optString("url"), info = json.optString("info"), code = json.optString("code"))
            } catch (error: Exception) {
                android.util.Log.w("ViaScripts", "Cannot parse addon payload", error)
                null
            }
        }
    }
}
