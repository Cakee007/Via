package dev.ujhhgtg.via.search

import java.net.URLDecoder

/** i6.g0.f/g and i6.u.a: editable Unicode URLs and original search-template extraction. */
internal object UrlInputText {
    fun isInternalDocument(url: String, filesPath: String): Boolean {
        val prefix = "file://$filesPath/"
        if (!url.startsWith(prefix)) return false
        var name = url.substring(prefix.length)
        val query = name.indexOf('?')
        if (query > 0) name = name.substring(0, query)
        val fragment = name.indexOf('#')
        if (fragment > 0) name = name.substring(0, fragment)
        return name in setOf("homepage.html", "homepage2.html", "bookmarks.html", "folder.html", "history.html", "catalog.html", "log.html", "res.html", "save.html", "about.html", "blank.html", "images.html")
    }
    fun decode(value: String): String = runCatching { URLDecoder.decode(value, "UTF-8").takeUnless { '\ufffd' in it } ?: value }.getOrDefault(value)
    fun display(value: String): String {
        if (value.length < 8) return value
        val separator = value.indexOf("://")
        if (separator < 4) return value
        if (separator >= 5 && !value.substring(separator - 5, separator).equals("https", true) && !value.substring(separator - 4, separator).equals("http", true)) return value
        val start = separator + 3
        val slash = value.indexOf('/', start)
        val punycode = value.indexOf("xn--", start).takeIf { it <= 0 || it < slash } ?: -1
        if (slash < 0) return if (punycode < 0) value else value.substring(0, start) + unicodeHost(value.substring(start))
        val percent = value.indexOf('%', slash + 1)
        if (punycode < 0 && percent < 0) return value
        val host = if (punycode > 0) value.substring(0, start) + unicodeHost(value.substring(start, slash)) else value.substring(0, slash)
        return host + if (percent > 0) value.substring(slash, percent) + decode(value.substring(percent)) else value.substring(slash)
    }
    fun extractSearch(url: String, template: String): String? {
        if (url.isEmpty() || template.isEmpty()) return null
        val templatePath = template.indexOf('/', 8)
        if (templatePath > 0) {
            val urlPath = url.indexOf('/', 8)
            if (urlPath < 0) return null
            // The loop bounds in JADX are reversed; original smali uses if-ge to end this loop.
            val sameHost = urlPath == templatePath && url.startsWith(template.substring(0, templatePath - 1))
            if (!sameHost && listOf(".baidu.com", ".bing.com", "/www.google.com", ".sogou.com").none {
                    template.lastIndexOf(it, templatePath) > 0 && url.lastIndexOf(it, urlPath) > 0
                }) return null
            if (url.lastIndexOf(".baidu.com", urlPath) > 0 && template.indexOf("/s?", templatePath) > 0 && url.indexOf("/s?", urlPath) < 0) return null
        }
        val marker = marker(template).takeIf { it >= 0 } ?: template.length
        if (marker == 0 && template.length == 2) return url
        var question = template.indexOf('?')
        if (templatePath < 0 && !url.startsWith(template.substring(0, if (question in 1..<marker) question else marker))) return null
        if (question !in 0..marker) {
            if (!url.startsWith(template.substring(0, marker))) return null
            val end = if (marker != template.length && marker != template.length - 2) {
                url.indexOf(template.substring(marker + 2), marker).takeIf { it >= 0 } ?: return null
            } else url.indexOf('?', marker).takeIf { it >= 0 } ?: url.indexOf('#', marker).takeIf { it >= 0 } ?: url.length
            return decode(url.substring(marker, end))
        }
        val ampersand = template.lastIndexOf('&', marker)
        if (ampersand >= 0) question = maxOf(question, ampersand)
        val keyStart = question + 1
        if (keyStart >= marker) return null
        var key = template.substring(keyStart, marker)
        var value = parameter(url, key)
        if (value == null && template.lastIndexOf(".baidu.com", templatePath) > 0) { key = "wd="; value = parameter(url, key) }
        if (value == null) return null
        if (marker == template.length || marker == template.length - 2) return decode(value)
        val shape = parameter(template, key, question) ?: return null
        if (value.length < shape.length - 2) return null
        if (shape.length == 2) return decode(value)
        val part = marker(shape)
        if (part < 0) return null
        val end = value.indexOf(shape.substring(part + 2))
        return if (end < part) null else decode(value.substring(part, end))
    }
    /** i6.v.b/c: the source decoder retains UTF-16 char insertion and literal null for invalid labels. */
    private fun unicodeHost(value: String): String {
        var at = value.indexOf("xn--")
        if (at < 0) return value
        val end = value.indexOf('/', at).takeIf { it >= 0 } ?: value.length
        val output = StringBuilder(value.substring(0, at))
        var labelEnd: Int
        while (true) {
            val from = at + 4
            labelEnd = minOf(value.indexOf('.', from), end)
            if (labelEnd < 0) labelEnd = minOf(value.indexOf(':', from), end)
            if (labelEnd < 0) labelEnd = end
            output.append(decodeLabel(value.substring(at, labelEnd)))
            at = value.indexOf("xn--", from)
            if (at !in 0..<end) break
            output.append(value.substring(labelEnd, at))
        }
        return output.append(value.substring(labelEnd)).toString()
    }
    private fun decodeLabel(label: String): String? {
        if (!label.startsWith("xn--")) return label
        val value = label.substring(4)
        val separator = value.lastIndexOf('-')
        val output = if (separator >= 0) value.substring(0, separator).toMutableList() else mutableListOf()
        var position = minOf(separator + 1, value.length)
        var codepoint = 128
        var bias = 72
        var index = 0
        while (position < value.length) {
            val oldIndex = index
            var weight = 1
            var k = 36
            var complete = false
            while (position < value.length) {
                val digit = when (val c = value[position++]) { in 'a'..'z' -> c - 'a'; in 'A'..'Z' -> c - 'A'; in '0'..'9' -> c.code - 22; else -> return null }
                index += digit * weight
                val threshold = if (k <= bias) 1 else if (k >= bias + 26) 26 else k - bias
                if (digit < threshold) { complete = true; break }
                weight *= 36 - threshold
                k += 36
            }
            if (!complete) return null
            var delta = (index - oldIndex) / if (oldIndex == 0) 700 else 2
            delta += delta / (output.size + 1)
            var adjustment = 0
            while (delta > 455) { delta /= 35; adjustment += 36 }
            bias = adjustment + delta * 36 / (delta + 38)
            codepoint += index / (output.size + 1)
            index %= output.size + 1
            output.add(index, codepoint.toChar())
            index++
        }
        return output.joinToString("")
    }
    private fun marker(value: String) = value.indexOf("%s").takeIf { it >= 0 } ?: value.indexOf("%S").takeIf { it >= 0 } ?: value.indexOf("%@")
    private fun parameter(url: String, key: String, from: Int = 0): String? {
        if (url.isEmpty() || key.isEmpty() || key.length + from >= url.length) return null
        var at = url.indexOf(key, from)
        while (at >= 0 && (at == 0 || url[at - 1] != '?' && url[at - 1] != '&')) at = url.indexOf(key, at + key.length)
        if (at < 0) return null
        val start = at + key.length
        var end = url.indexOf('&', start)
        val hash = url.indexOf('#', start)
        if (hash > 0 && (end !in 0..hash)) end = hash
        if (end < 0) end = url.length
        return url.substring(start, end)
    }
}
