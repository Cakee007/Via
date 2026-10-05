package dev.ujhhgtg.via.reader

import org.json.JSONArray

/** j8/u.java, with the accumulating short-clause branch verified in smali/j8/u.smali. */
object ReaderSentences {
    private const val RADICALS = "一丨丶丿乙亅二亠人儿入八冂冖冫几凵刀力勹匕匚匸十卜卩厂厶又口口土士夂夊夕大女子宀寸小尢尸屮山巛工己巾干幺广廴廾弋弓彐彡彳心戈户手支攴文斗斤方无日曰月木欠止歹殳毋比毛氏气水火爪父爻爿片牙牛犬玄玉瓜瓦甘生用田疋疒癶白皮皿目矛矢石示禸禾穴立竹米糸缶网羊羽老而耒耳聿肉臣自至臼舌舛舟艮色艸虍虫血行衣襾見角言谷豆豕豸貝赤走足身車辛辰辵邑酉采里金長門阜隶隹雨青非面革韋韭音頁風飛食首香馬骨高髟鬥鬯鬲鬼魚鳥鹵鹿麥麻黃黍黑黹黽鼎鼓鼠鼻齊齒龍龜龠"
    private val delimiters = listOf("”", "」", "。", "，", "；", "？", "！", ". ", ", ", "; ", "? ", "! ", "} ", " ", ".")

    fun parse(json: String): List<String> {
        if (json.length < 2 || json == "[]") return emptyList()
        val normalized = buildString {
            for (character in json) {
                if (character == '\u200b') continue
                append(if (character in '\u2f00'..'\u2fd5') RADICALS[character.code - 0x2f00] else character)
            }
        }
        val array = runCatching { JSONArray(normalized) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val paragraph = array.optString(index).trim()
                if (paragraph.isEmpty()) continue
                if (paragraph.length < 256) add(paragraph) else addAll(split(paragraph))
            }
        }
    }

    fun split(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val delimiter = delimiters.firstOrNull(text::contains) ?: return text.chunked(256)
            .map { it.trim() }
        val result = mutableListOf<String>()
        var scan = 0
        var start = 0
        while (scan < text.length) {
            val next = text.indexOf(delimiter, scan)
            if (next < 0) {
                while (start < text.length) {
                    val end = minOf(start + 256, text.length)
                    result.add(text.substring(start, end).trim())
                    start = end
                }
                break
            }
            if (next - start >= 256) {
                scan = start + 256
                result.add(text.substring(start, scan).trim())
                start = scan
            } else if (next - start >= 64) {
                scan = next + delimiter.length
                result.add(text.substring(start, scan).trim())
                start = scan
            } else {
                scan = next + delimiter.length
            }
        }
        return result
    }
}
