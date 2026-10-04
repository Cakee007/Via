package dev.ujhhgtg.via.browser.filter

import java.io.File
import java.io.IOException

/** y4.a: cached parsed custom rules, with separate single-rule and bulk-file paths. */
internal class CustomFilterRules(private val file: File) {
    private var rules: MutableList<ParsedFilter>? = null

    fun reload(text: String) {
        rules = text.lineSequence().mapNotNull(FilterParser::parse).toMutableList()
    }

    private fun entries(): MutableList<ParsedFilter> {
        if (rules == null) {
            val text = try { if (file.isFile) file.readText() else "" }
            catch (error: IOException) { error.printStackTrace(); "" }
            reload(text)
        }
        return requireNotNull(rules)
    }

    private fun prepareFile(): Boolean = file.parentFile?.let { it.exists() || it.mkdirs() } ?: true

    /** y4.a.e rejects parsed duplicates, then appends a newline followed by the unmodified raw text. */
    fun add(raw: String): ParsedFilter? {
        if (raw.isEmpty() || raw[0] == '!' || !prepareFile()) return null
        val parsed = FilterParser.parse(raw) ?: return null
        if (entries().any { it.equivalentTo(parsed) }) return null
        entries().add(parsed)
        try {
            file.appendText(System.lineSeparator() + raw)
        } catch (error: IOException) { error.printStackTrace() }
        return parsed
    }

    /** y4.a.d removes one parsed equivalent in memory, but only exact trimmed lines on disk. */
    fun remove(raw: String): ParsedFilter? {
        if (raw.isEmpty() || raw[0] == '!' || !prepareFile()) return null
        val parsed = FilterParser.parse(raw) ?: return null
        val index = entries().indexOfFirst { it.equivalentTo(parsed) }
        if (index < 0) return null
        entries().removeAt(index)
        try {
            val temporary = File(file.parentFile, ".${file.name}")
            file.bufferedReader().use { input ->
                temporary.bufferedWriter().use { output ->
                    input.lineSequence().map(String::trim).filter { it.isNotEmpty() && it != raw }.forEach {
                        output.write(it); output.newLine()
                    }
                }
            }
            temporary.renameTo(file)
        } catch (error: IOException) { error.printStackTrace() }
        return parsed
    }
}
