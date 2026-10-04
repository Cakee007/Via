package dev.ujhhgtg.via.browser.filter

import android.webkit.WebResourceRequest

/** Network and cosmetic rules; supported grammar is recovered from w4.a/d, not full uBlock. */
class FilterEngine(webViewMajorVersion: Int = Int.MAX_VALUE) {
    private val networkIndex = NetworkRuleIndex()
    private val cosmeticIndex = CosmeticRuleIndex(webViewMajorVersion)
    @Volatile private var loading = false
    internal val isLoading: Boolean get() = loading

    /** x4.a.a/e: add to the resident indexes without rebuilding earlier token assignments. */
    fun addList(text: String): Int {
        if (loading) return 0
        val network = mutableListOf<FilterRule>()
        val cosmetic = mutableListOf<CosmeticRule>()
        val added = parseList(text, network, cosmetic)
        network.forEach(networkIndex::add)
        cosmetic.forEach(cosmeticIndex::add)
        return added
    }

    internal fun addRule(rule: ParsedFilter) {
        if (loading) return
        when (rule) {
            is ParsedFilter.Network -> networkIndex.add(rule.rule)
            is ParsedFilter.Cosmetic -> cosmeticIndex.add(rule.rule)
        }
    }
    internal fun removeRule(rule: ParsedFilter) {
        if (loading) return
        when (rule) {
            is ParsedFilter.Network -> networkIndex.remove(rule.rule)
            is ParsedFilter.Cosmetic -> cosmeticIndex.remove(rule.rule)
        }
    }

    fun replaceLists(lists: List<String>) {
        // x4.a.o bypasses matching for its clear/a -> add all lists -> b rebuild.
        loading = true
        try {
            val network = mutableListOf<FilterRule>()
            val cosmetic = mutableListOf<CosmeticRule>()
            lists.forEach { parseList(it, network, cosmetic) }
            // x4.a.o rebuilds the existing index via clear -> a -> add -> b.
            networkIndex.clear()
            networkIndex.beginRebuild()
            network.forEach(networkIndex::add)
            networkIndex.endRebuild()
            cosmeticIndex.clear()
            cosmeticIndex.beginRebuild()
            cosmetic.forEach(cosmeticIndex::add)
            cosmeticIndex.endRebuild()
        } finally { loading = false }
    }

    private fun parseList(text: String, network: MutableList<FilterRule>, cosmetic: MutableList<CosmeticRule>): Int {
        var added = 0
        text.lineSequence().forEach { line ->
            when (val parsed = FilterParser.parse(line)) {
                is ParsedFilter.Network -> { network += parsed.rule; added++ }
                is ParsedFilter.Cosmetic -> { cosmetic += parsed.rule; added++ }
                null -> Unit
            }
        }
        return added
    }

    /** x4.a.c: expose the exact indexed result, including exceptions, to the warning-page caller. */
    fun matchingRule(request: FilterRequest): FilterRule? =
        if (loading || request.url.isEmpty()) null else networkIndex.match(request)

    fun shouldBlock(request: WebResourceRequest, topUrl: String? = null): Boolean =
        shouldBlock(request.toFilterRequest(topUrl))

    fun shouldBlock(request: FilterRequest): Boolean = matchingRule(request)?.exception == false

    /** Returns CSS suitable for a style tag, or null when no cosmetic rules match. */
    fun cosmeticCss(url: String): String? {
        if (loading) return null
        // x4.a.h -> b5.c.m keeps host spelling and removes only path and port.
        var host = url.substringAfter("://", url)
        val slash = host.indexOf('/')
        if (slash > 0) host = host.substring(0, slash)
        val port = host.lastIndexOf(':')
        if (port > 0) host = host.substring(0, port)
        return if (host.isEmpty()) null else cosmeticIndex.css(host)
    }

    data class CosmeticRule(val selector: String, val domainRules: List<String>?, val exception: Boolean = false) {
        internal val indexedDomains get() = domainRules?.map { if (exception && !it.startsWith('~')) "~$it" else it }
    }
}

internal sealed interface ParsedFilter {
    data class Network(val rule: FilterRule) : ParsedFilter
    data class Cosmetic(val rule: FilterEngine.CosmeticRule) : ParsedFilter

    /** w4.d/a.equals: raw spelling is ignored, but domain-array order is retained. */
    fun equivalentTo(other: ParsedFilter): Boolean = when {
        this is Network && other is Network -> rule.pattern == other.rule.pattern && rule.mask == other.rule.mask &&
            rule.thirdPartyOnly == other.rule.thirdPartyOnly && rule.thirdPartyExcluded == other.rule.thirdPartyExcluded &&
            rule.exception == other.rule.exception && rule.domainRules == other.rule.domainRules &&
            (rule.domainRules.isNotEmpty() || hasDomainOption(rule.raw) == hasDomainOption(other.rule.raw))
        this is Cosmetic && other is Cosmetic -> rule.selector == other.rule.selector &&
            rule.indexedDomains == other.rule.indexedDomains
        else -> false
    }

    private fun hasDomainOption(raw: String) = raw.substringAfter('$', "").split(',').any { it.removePrefix("~").startsWith("domain=") }
}

internal object FilterParser {
    fun parse(line: String): ParsedFilter? {
        val source = line.trim()
        if (source.isEmpty() || source.startsWith("!") || source.startsWith("[") || (source.startsWith("#") && !source.startsWith("##"))) return null
        val cosmeticMarker = when {
            source.contains("#@#") -> "#@#"
            source.contains("##") -> "##"
            else -> null
        }
        if (cosmeticMarker != null) {
            val index = source.indexOf(cosmeticMarker)
            val selector = source.substring(index + cosmeticMarker.length)
            if (selector.isEmpty() || selector.startsWith("^") || selector.contains("js(")) return null
            val domainRules = if (index == 0) null else source.substring(0, index).split(',').filter(String::isNotEmpty)
            if (!validSelector(selector)) return null
            if (index == 0 && cosmeticMarker == "#@#") return null
            return ParsedFilter.Cosmetic(FilterEngine.CosmeticRule(selector, domainRules, cosmeticMarker == "#@#"))
        }
        if (source.length < 5 || source.contains('\\')) return null
        if (source.contains(' ')) {
            if (!source.startsWith("127.0.0.1")) return null
            val pattern = "|" + source.substringAfter(' ').trim() + "^"
            return ParsedFilter.Network(FilterRule(source, pattern))
        }
        var value = source
        var exception = false
        if (value.startsWith("@@")) { exception = true; value = value.substring(2) }
        val optionIndex = value.indexOf('$')
        val patternPart = if (optionIndex >= 0) value.substring(0, optionIndex) else value
        var mask = 0
        var unsupported = false
        val domainRules = mutableListOf<String>()
        var thirdOnly = false
        var thirdExcluded = false
        val domains = mutableListOf<String>()
        val excluded = mutableListOf<String>()
        if (optionIndex >= 0) {
            value.substring(optionIndex + 1).split(',').forEach { option ->
                val item = option.trim().lowercase()
                when {
                    item == "third-party" -> thirdOnly = true
                    item == "~third-party" -> thirdExcluded = true
                    item.startsWith("domain=") -> {
                        // w4.d.i -> b5.c.f: the last domain option replaces earlier
                        // values; preserve case/order and omit empty separators.
                        domainRules.clear(); domains.clear(); excluded.clear()
                        option.substringAfter('=').split('|').filter(String::isNotEmpty).forEach {
                            domainRules += it
                            if (it.startsWith("~")) excluded += it.substring(1) else domains += it
                        }
                    }
                    else -> {
                        val bit = resourceMask(item.removePrefix("~"))
                        if (bit == 0) { if ('=' in item) return null; unsupported = true }
                        else if (item.startsWith('~')) { if (mask and 32752 == 0) mask = 32752; mask = mask and bit.inv() }
                        else mask = mask or bit
                    }
                }
            }
        }
        if (unsupported && mask == 0) return null
        val pattern = normalizePattern(patternPart)
        if (pattern.isEmpty() && optionIndex < 0) return null
        return ParsedFilter.Network(FilterRule(source, pattern, domains, excluded, mask, thirdOnly, thirdExcluded, exception, domainRules))
    }

    private fun normalizePattern(value: String): String {
        if (value.length <= 1) return value
        var result = when { value.startsWith("||*") -> value.drop(3); value.startsWith('|') || value.startsWith('*') -> value.drop(1); else -> value }
        if (result.endsWith('*')) result = result.dropLast(1)
        return result
    }

    /** w4.a.e balances CSS quotes/brackets and rejects unsupported action operators. */
    private fun validSelector(selector: String): Boolean {
        val unsupported = listOf(":has-text(", ":matches-attr(", ":matches-css(", ":matches-css-before(", ":matches-css-after(", ":matches-media(", ":matches-path(", ":min-text-length(", ":NOT(", ":upward(", ":watch-attr(", ":xpath(", ":remove(", ":style(", ":remove-attr(", ":remove-class(")
        if (unsupported.any(selector::contains)) return false
        var quote = '\u0000'; var brackets = 0; var parens = 0; var content = false; var index = 0
        while (index < selector.length) {
            val c = selector[index]
            if (c == '\\') { if (++index == selector.length) return false; content = true }
            else if (quote != '\u0000') { if (c == quote) quote = '\u0000'; content = true }
            else when {
                c == '"' || c == '\'' -> { quote = c; content = true }
                c in "{};!&?" -> return false
                c == '[' -> { brackets++; content = true }
                c == ']' -> { if (--brackets < 0) return false; content = true }
                c == '(' -> { parens++; content = true }
                c == ')' -> { if (--parens < 0) return false; content = true }
                c == '=' && brackets == 0 -> return false
                c == ',' && brackets == 0 && parens == 0 -> { if (!content) return false; content = false }
                !c.isWhitespace() -> content = true
            }
            index++
        }
        return quote == '\u0000' && brackets == 0 && parens == 0 && content
    }

    private fun resourceMask(value: String): Int = when (value) {
        "script" -> ResourceType.SCRIPT.mask
        "image", "background" -> ResourceType.IMAGE.mask
        "stylesheet" -> ResourceType.STYLESHEET.mask
        "subdocument" -> ResourceType.SUBDOCUMENT.mask
        "document" -> ResourceType.DOCUMENT.mask
        "media" -> ResourceType.MEDIA.mask
        "font" -> ResourceType.FONT.mask
        "xmlhttprequest" -> ResourceType.XHR.mask
        "websocket" -> ResourceType.WEBSOCKET.mask
        "other", "xbl", "dtd" -> ResourceType.OTHER.mask
        "popup" -> 4096
        else -> 0
    }
}
