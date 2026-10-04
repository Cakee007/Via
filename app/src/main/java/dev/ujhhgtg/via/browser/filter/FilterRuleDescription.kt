package dev.ujhhgtg.via.browser.filter

/** w4.d.m: serialize the parsed filter, including its original option order and commas. */
fun FilterRule.description(): String = buildString {
    // b5.c.f omits empty domain segments but keeps a non-null array for domain=.
    val filterDomains = domainRules.takeIf { it.isNotEmpty() }?.filter { it.isNotEmpty() }
    if (exception) append("@@")
    if (pattern.startsWith('|') || pattern.startsWith("http://") || pattern.startsWith("https://")) append('|')
    append(pattern)
    if (mask != 0 || thirdPartyOnly || thirdPartyExcluded || !filterDomains.isNullOrEmpty()) append('$')
    val types = mask and 0x7ff0
    val inverse = Integer.bitCount(types) > 5
    if (types != 0) {
        val bits = intArrayOf(32, 16, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384)
        val names = arrayOf("script", "other", "image", "stylesheet", "subdocument", "document", "media", "font", "popup", "websocket", "xmlhttprequest")
        for (index in bits.indices) {
            if (inverse && types and bits[index] == 0) {
                append('~')
                append(names[index])
                append(',')
            }
            if (!inverse && types and bits[index] == bits[index]) {
                append(names[index])
                append(',')
            }
        }
    }
    if (thirdPartyOnly) append("third-party,")
    if (thirdPartyExcluded) append("~third-party,")
    if (filterDomains != null) {
        append("domain=")
        append(filterDomains.joinToString("|"))
    }
}
