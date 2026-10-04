package dev.ujhhgtg.via.search

import java.util.Locale

/** z9.a..n with the supplied viayoo APK's local r9.k.t(null) defaults, without remote configuration. */
object BuiltinSearchProviders {
    // z8.c0.i() is false in this APK; z8.v2.a selects r9.k's local ds_g="bd" in every region.
    const val DEFAULT_ID = -2
    private const val BAIDU_CHANNEL = "1022282z"
    private const val METASO_CHANNEL = "s=nyzav&referrer_s=nyzav"

    fun template(id: Int, desktop: Boolean): String? = when (id) {
        -1 -> "https://www.google.com/search?q="
        -2 -> if (desktop) "https://www.baidu.com/s?ie=utf-8&from=$BAIDU_CHANNEL&word="
            else "https://m.baidu.com/s?from=$BAIDU_CHANNEL&word="
        -3 -> "https://www.bing.com/search?q="
        -4 -> "https://yz.m.sm.cn/s?q="
        -5 -> if (desktop) "https://www.so.com/s?q=" else "https://m.so.com/s?q="
        -6 -> if (desktop) "https://www.sogou.com/web?query=" else "https://wap.sogou.com/web/sl?keyword="
        -7 -> if (desktop) "https://yandex.com/search/?text=" else "https://yandex.com/search/touch/?text="
        -8 -> "https://search.yahoo.com/search?p="
        -9 -> "https://startpage.com/do/search?query="
        -10 -> "https://duckduckgo.com/?q="
        -11 -> "https://so.toutiao.com/search?keyword="
        -12 -> "https://metaso.cn/?$METASO_CHANNEL&q="
        else -> null
    }

    /** z8.v2.c retains a custom template; an empty unknown/custom provider falls back to v2.a. */
    fun resolveTemplate(id: Int, desktop: Boolean, custom: String?): String =
        template(id, desktop) ?: custom?.takeIf(String::isNotEmpty) ?: requireNotNull(template(DEFAULT_ID, desktop))

    /** z8.v2.b uses the process locale, not the selected UI language or the provider's default ID. */
    fun ids(locale: Locale = Locale.getDefault()): List<Int> = buildList {
        val china = locale.country.equals("CN", true)
        if (china) add(-2)
        add(-1)
        if (locale.language.equals("ru", true)) add(-7)
        if (!china) add(-2)
        add(-3)
        if (china) {
            // Only the source Huawei distribution omits Metaso; the supplied APK's channel is viayoo.
            addAll(listOf(-12, -6, -11, -4, -5))
        } else addAll(listOf(-8, -9))
        add(-10)
    }

    /** tb.c.a; user-defined searchshortcuts values are overlaid by SearchProviders. */
    fun shortcuts(): LinkedHashMap<Int, String> = linkedMapOf(
        -1 to "gg", -3 to "bn", -2 to "bd", -10 to "dd", -12 to "mt", -5 to "qh",
        -4 to "sm", -6 to "sg", -11 to "tt", -9 to "sp", -8 to "yh", -7 to "yd",
    )
}
