package dev.ujhhgtg.via.browser

/** i6.v.d/e: original UTF-16-label Punycode encoder, including zero-width-space removal. */
internal object UrlPunycode {
    fun encodeHost(value: String): String {
        if (value.isEmpty()) return value
        val scheme = value.indexOf("://")
        var start = if (scheme < 0) 0 else scheme + 3
        var end = value.indexOf('/', start)
        if (end < 0) end = value.indexOf(':', start)
        if (end < 0) end = value.length
        val output = StringBuilder(if (start > 0) value.substring(0, start) else "")
        var partEnd: Int
        while (true) {
            partEnd = minOf(value.indexOf('.', start), end)
            if (partEnd < 0) partEnd = minOf(value.indexOf(':', start), end)
            if (partEnd < 0) partEnd = end
            output.append(encodeLabel(value.substring(start, partEnd)))
            val dot = value.indexOf('.', start)
            if (dot !in 0..<end) break
            start = dot + 1
            output.append(value.substring(partEnd, start))
        }
        return output.append(value.substring(partEnd)).toString()
    }
    private fun encodeLabel(label: String): String? {
        if (label.isEmpty()) return label
        val value = label.replace("\u200b", "")
        val basic = value.filter { it.code < 128 }
        if (basic.length == value.length) return value
        val output = StringBuilder(basic)
        if (basic.isNotEmpty()) output.append('-')
        val codepoints = value.map(Char::code)
        var bias = 72
        var handled = basic.length
        var delta = 0
        var n = 128
        while (handled < codepoints.size) {
            val next = codepoints.filter { it >= n }.minOrNull() ?: return null
            var count = delta + (next - n) * (handled + 1)
            for (point in codepoints) {
                if (point < next) count++
                else if (point == next) {
                    var k = 36
                    var current = count
                    while (true) {
                        val threshold = if (k <= bias) 1 else if (k >= bias + 26) 26 else k - bias
                        if (current < threshold) {
                            output.append(digit(current) ?: return null)
                            handled++
                            bias = adapt(count, handled, handled - 1 == basic.length)
                            count = 0
                            break
                        }
                        output.append(digit(threshold + (current - threshold) % (36 - threshold)) ?: return null)
                        current = (current - threshold) / (36 - threshold)
                        k += 36
                    }
                }
            }
            delta = count + 1
            n = next + 1
        }
        return "xn--$output"
    }
    private fun adapt(delta: Int, points: Int, first: Boolean): Int {
        var value = delta / if (first) 700 else 2
        value += value / points
        var k = 0
        while (value > 455) { value /= 35; k += 36 }
        return k + value * 36 / (value + 38)
    }
    private fun digit(value: Int): Char? = when (value) { in 0..25 -> (value + 97).toChar(); in 26..35 -> (value + 22).toChar(); else -> null }
}
