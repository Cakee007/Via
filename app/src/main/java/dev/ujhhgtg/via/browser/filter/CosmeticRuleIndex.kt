package dev.ujhhgtg.via.browser.filter

/** v4.b: domain/global selector sets; a single removal changes the resident set, not its input lists. */
internal class CosmeticRuleIndex(private val webViewMajorVersion: Int) {
    private val domains = HashMap<String, Array<String>>()
    private var pending: HashMap<String, HashSet<String>>? = null

    fun beginRebuild() {
        if (pending != null) return
        pending = HashMap<String, HashSet<String>>().also { staged ->
            domains.forEach { (domain, selectors) -> staged[domain] = HashSet(selectors.asList()) }
        }
        domains.clear()
    }

    fun endRebuild() {
        val staged = pending ?: return
        // The original leaves an empty staging map attached; later additions
        // remain staged until a nonempty batch is completed.
        if (staged.isEmpty()) return
        staged.forEach { (domain, selectors) -> domains[domain] = selectors.toTypedArray() }
        staged.clear()
        pending = null
    }

    fun clear() { domains.clear() }

    fun add(rule: FilterEngine.CosmeticRule) {
        val colon = rule.selector.indexOf(':')
        val parenthesis = if (colon >= 0) rule.selector.indexOf('(', colon) else -1
        if (colon in 0..<parenthesis &&
            ":has(...)".contains(rule.selector.substring(colon, parenthesis + 1)) && webViewMajorVersion < 105) return
        val keys = rule.indexedDomains
        if (keys.isNullOrEmpty()) add(rule.selector, "")
        else keys.forEach { if (it.length > 2) add(rule.selector, it) }
    }

    fun remove(rule: FilterEngine.CosmeticRule) {
        val keys = rule.indexedDomains
        if (keys.isNullOrEmpty()) remove(rule.selector, "")
        else for (key in keys) {
            // v4.b.d stops, rather than skips, at a short domain.
            if (key.length <= 2) break
            remove(rule.selector, key)
        }
    }

    private fun add(selector: String, domain: String) {
        if (selector.isEmpty()) return
        val staged = pending
        if (staged != null) staged.getOrPut(domain) { HashSet() }.add(selector)
        else domains[domain] = HashSet(domains[domain]?.asList().orEmpty()).apply { add(selector) }.toTypedArray()
    }

    private fun remove(selector: String, domain: String) {
        if (selector.isEmpty()) return
        val staged = pending
        val selectors = if (staged != null) staged[domain] ?: return
            else domains[domain]?.let { HashSet(it.asList()) } ?: return
        selectors.remove(selector)
        if (selectors.isEmpty()) { if (staged != null) staged.remove(domain) else domains.remove(domain) }
        else if (staged == null) domains[domain] = selectors.toTypedArray()
    }

    fun css(host: String): String? {
        val base = FilterRule.baseDomain(host)
        val site = HashSet<String>()
        domains[host]?.let { site.addAll(it) }
        if (base != host) domains[base]?.let { site.addAll(it) }
        val global = HashSet<String>()
        domains[""]?.let { global.addAll(it) }
        if (site.isEmpty() && global.isEmpty()) return null
        for (key in listOfNotNull("~$host", if (base != host) "~$base" else null)) {
            domains[key]?.forEach { site.remove(it); global.remove(it) }
        }
        val selectors: Set<String> = when {
            site.isEmpty() -> global
            global.isEmpty() -> site
            else -> LinkedHashSet<String>().apply { addAll(site); addAll(global) }
        }
        if (selectors.isEmpty()) return null
        return buildString {
            var count = 0
            selectors.forEach { selector ->
                append(selector)
                if (++count % 100 == 0 || count == selectors.size) append("{display:none!important}\n")
                else append(',')
            }
        }
    }
}
