package dev.ujhhgtg.via.browser.filter

/**
 * v4.c with b5.c.g/h and w4.d.h: ordered five-character indexes, domain shortcut and exception priority.
 * Patterns are already normalized by the caller. Construct a replacement before publishing it to readers.
 */
class NetworkRuleIndex(rules: List<FilterRule> = emptyList()) {
    private val blockingUrls = HashMap<String, Entry>()       // v4.c.a
    private val exceptionUrls = HashMap<String, Entry>()      // v4.c.b
    private val blockingDomains = HashMap<String, Entry>()    // v4.c.c
    private val exceptionDomains = HashMap<String, Entry>()   // v4.c.d
    private val hosts = HashSet<String>()                     // v4.c.e
    private var pending: LinkedHashSet<Entry>? = null         // v4.c.f

    init { beginRebuild(); rules.forEach(::add); endRebuild() }

    /** v4.c.a collects exception URL, blocking URL, exception domain, blocking domain in that order. */
    fun beginRebuild() {
        if (pending != null) return
        val collected = LinkedHashSet<Entry>()
        for (index in listOf(exceptionUrls, blockingUrls, exceptionDomains, blockingDomains)) {
            collected.addAll(index.values)
            index.clear()
        }
        pending = collected
    }

    /** v4.c.b chooses the first free token for each staged rule, preserving the staged order. */
    fun endRebuild() {
        pending?.forEach { entry ->
            val index = if (entry.rule.exception) exceptionUrls else blockingUrls
            val token = firstFreeToken(entry.rule.pattern, index.keys)
            if (token == null) addDomain(entry) else index[token] = entry
        }
        pending?.clear()
        pending = null
    }

    /** v4.c.d: incremental insertion does not overwrite occupied tokens or rescue colliding rules. */
    fun add(rule: FilterRule) {
        val entry = Entry(rule)
        fastHost(entry)?.let { hosts.add(it); return }
        pending?.let { it.add(entry); return }
        val tokens = tokens(rule.pattern)
        if (tokens.isEmpty()) { addDomain(entry); return }
        addAtFreeToken(if (rule.exception) exceptionUrls else blockingUrls, tokens, entry)
    }

    /** v4.c.e/h: remove exactly the indexed equivalent rule, including domain-only split entries. */
    fun remove(rule: FilterRule) {
        val entry = Entry(rule)
        fastHost(entry)?.let { hosts.remove(it); return }
        pending?.let { it.remove(entry); return }
        val tokens = tokens(rule.pattern)
        if (tokens.isEmpty()) { removeDomain(entry); return }
        removeAtToken(if (rule.exception) exceptionUrls else blockingUrls, tokens, entry)
    }

    /** v4.c.clear does not discard a pending a/b rebuild. */
    fun clear() {
        exceptionUrls.clear(); blockingUrls.clear(); exceptionDomains.clear(); blockingDomains.clear(); hosts.clear()
    }

    fun match(request: FilterRequest): FilterRule? {
        val url = request.url
        val targetHost = literalHost(url)
        var match = if (targetHost in hosts) hostRule(targetHost!!) else null
        if (match == null && targetHost != null) {
            val base = baseDomain(targetHost)
            if (base != targetHost && base in hosts) match = hostRule(base)
        }
        // Deliberately retained: v4.c.c smali lines 88–104 returns the host shortcut when BOTH
        // exception indexes are empty, even if ordinary URL/domain index entries exist.
        if (exceptionUrls.isEmpty() && exceptionDomains.isEmpty()) return match

        val pageHost = literalHost(request.topUrl)
        val thirdParty = targetHost != null && pageHost != null && targetHost != pageHost && !targetHost.contains(baseDomain(pageHost))
        var start = url.indexOf("://").let { if (it < 0) 0 else it + 3 }
        var candidates = 0
        while (start <= url.length - 5 && candidates < 32) {
            val token = url.substring(start, start + 5)
            exceptionUrls[token]?.let { if (matches(it.rule, url, pageHost, request.typeMask, thirdParty)) return it.rule }
            if (match == null) {
                blockingUrls[token]?.let {
                    candidates++
                    if (matches(it.rule, url, pageHost, request.typeMask, thirdParty)) {
                        match = it.rule
                        if (exceptionUrls.isEmpty()) break
                    }
                }
            }
            start++
        }
        if (pageHost == null || exceptionDomains.isEmpty() && blockingDomains.isEmpty()) return match
        start = 0; candidates = 0
        while (start <= pageHost.length - 5 && candidates < 32) {
            val token = pageHost.substring(start, start + 5)
            exceptionDomains[token]?.let { if (matches(it.rule, url, pageHost, request.typeMask, thirdParty)) return it.rule }
            if (match == null) {
                blockingDomains[token]?.let {
                    candidates++
                    if (matches(it.rule, url, pageHost, request.typeMask, thirdParty)) {
                        match = it.rule
                        if (exceptionDomains.isEmpty()) return match
                    }
                }
            }
            start++
        }
        return match
    }

    private fun addDomain(entry: Entry) {
        val domains = entry.rule.domainRules
        if (domains.isEmpty() || firstFreeToken(entry.rule.pattern, emptySet()) != null) return
        if (domains.size > 1) { domains.forEach { addDomain(entry.forDomain(it)) }; return }
        val tokens = tokens(domains[0])
        if (tokens.isEmpty()) return
        addAtFreeToken(if (entry.rule.exception) exceptionDomains else blockingDomains, tokens, entry)
    }
    private fun removeDomain(entry: Entry) {
        val domains = entry.rule.domainRules
        if (domains.isEmpty() || firstFreeToken(entry.rule.pattern, emptySet()) != null) return
        if (domains.size > 1) { domains.forEach { removeDomain(entry.forDomain(it)) }; return }
        val tokens = tokens(domains[0])
        if (tokens.isEmpty()) return
        removeAtToken(if (entry.rule.exception) exceptionDomains else blockingDomains, tokens, entry)
    }
    private fun addAtFreeToken(index: MutableMap<String, Entry>, tokens: List<String>, entry: Entry) {
        if (tokens.any { index[it] == entry }) return
        for (token in tokens) if (index[token] == null) { index[token] = entry; return }
    }
    private fun removeAtToken(index: MutableMap<String, Entry>, tokens: List<String>, entry: Entry) {
        for (token in tokens) if (index[token] == entry) { index.remove(token); return }
    }

    /** w4.d equality/hash ignores the source spelling; empty normalized pattern represents null. */
    private class Entry(val rule: FilterRule) {
        val flags = (rule.mask and 0x7ff0) or (if (rule.exception) 1 else 0) or
            (if (rule.thirdPartyOnly) 2 else 0) or (if (rule.thirdPartyExcluded) 4 else 0)
        override fun equals(other: Any?) = other is Entry && flags == other.flags && rule.pattern == other.rule.pattern && rule.domainRules == other.rule.domainRules
        override fun hashCode() = ((rule.pattern.takeIf(String::isNotEmpty)?.hashCode() ?: 0) * 31 + flags) * 31 +
            (if (rule.domainRules.isEmpty()) 0 else rule.domainRules.hashCode())
        fun forDomain(domain: String) = Entry(rule.copy(
            domains = if (domain.startsWith('~')) emptyList() else listOf(domain),
            excludedDomains = if (domain.startsWith('~')) listOf(domain.substring(1)) else emptyList(),
            domainRules = listOf(domain),
        ))
    }

    companion object {
        private fun hostRule(host: String) = FilterRule(raw = "||$host^", pattern = "|$host^")
        /** v4.c.g admits only plain domain blocks with no flags/domain option. */
        private fun fastHost(entry: Entry): String? {
            val pattern = entry.rule.pattern
            if (entry.flags != 0 || entry.rule.domainRules.isNotEmpty() || pattern.length < 3 ||
                pattern.first() != '|' || pattern.last() != '^' || pattern[1] == '.' || pattern[pattern.length - 2] == '.') return null
            var dot = -1
            for (index in 1 until pattern.lastIndex) {
                val char = pattern[index]
                if (char == '.') { if (dot == index - 1) return null; dot = index }
                else if (char !in 'A'..'Z' && char !in 'a'..'z' && char !in '0'..'9' && char != '-') return null
            }
            return if (dot < 0) null else pattern.substring(1, pattern.lastIndex)
        }

        /** b5.c.g starts its '$' search after the scheme; h searches '$' from the beginning. */
        private fun firstFreeToken(pattern: String, occupied: Set<String>): String? {
            if (pattern.length < 5) return null
            var start = pattern.indexOf("://").let { if (it < 0) 0 else it + 3 }
            val end = pattern.indexOf('$', start).let { if (it < 0) pattern.length else it }
            while (start <= end - 5) {
                var count = 0
                while (count < 5 && pattern[start + count] !in "@|*^") count++
                if (count < 5) { start += count + 1; continue }
                val token = pattern.substring(start, start + 5)
                if (token !in occupied) return token
                start++
            }
            return null
        }
        private fun tokens(pattern: String): List<String> {
            if (pattern.length < 5) return emptyList()
            var start = pattern.indexOf("://").let { if (it < 0) 0 else it + 3 }
            val end = pattern.indexOf('$').let { if (it < 0) pattern.length else it }
            val result = ArrayList<String>()
            while (start <= end - 5) {
                var count = 0
                while (count < 5 && pattern[start + count] !in "@|*^") count++
                if (count < 5) { start += count + 1; continue }
                result += pattern.substring(start, start + 5)
                start++
            }
            return result
        }

        /** b5.c.m/p/s use literal, case-sensitive host text, not URI normalization or a public-suffix list. */
        private fun literalHost(url: String?): String? {
            if (url.isNullOrEmpty()) return null
            var host = url.indexOf("://").let { if (it < 0) url else url.substring(it + 3) }
            val slash = host.indexOf('/')
            if (slash > 0) host = host.substring(0, slash)
            val colon = host.lastIndexOf(':')
            return if (colon > 0) host.substring(0, colon) else host
        }
        private fun baseDomain(host: String): String {
            val last = host.lastIndexOf('.')
            if (last < 0) return host
            val previous = host.lastIndexOf('.', last - 1)
            if (previous < 0) return host
            val start = if ("|com|net|org|gov|co|".contains("|${host.substring(previous + 1, last)}|"))
                host.lastIndexOf('.', previous - 1) else previous
            return host.substring(start + 1)
        }
        private fun matches(rule: FilterRule, url: String, pageHost: String?, typeMask: Int, thirdParty: Boolean): Boolean {
            val mask = rule.mask and 0x7ff0
            if (mask != 0 && typeMask and mask == 0) return false
            if ((rule.thirdPartyOnly || rule.thirdPartyExcluded) && (!rule.thirdPartyOnly) == thirdParty) return false
            if (rule.domainRules.isNotEmpty()) {
                if (!pageHost.isNullOrEmpty()) {
                    var included = false
                    for (domain in rule.domainRules) {
                        if (domain.isEmpty()) continue
                        if (domain[0] == '~') {
                            if (pageHost.contains(domain.substring(1))) return false
                            included = true
                        } else if (pageHost.contains(domain)) { included = true; break }
                    }
                    if (!included) return false
                } else if (rule.domainRules[0][0] != '~') return false
            }
            return FilterRule.wildcardMatch(rule.pattern, url)
        }
    }
}
