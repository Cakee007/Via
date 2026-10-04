package dev.ujhhgtg.via.browser.filter

import java.io.File
import java.util.Locale
import java.util.UUID

/** b5.d.b: exact bundled subscription catalog, selected by application language. */
internal object FilterSubscriptionCatalog {
    fun create(root: File, language: String): List<FilterStore.Subscription> {
        fun entry(url: String, title: String) = FilterStore.Subscription(url = url, title = title,
            filePath = File(root, UUID.randomUUID().toString() + ".txt").path)
        return buildList {
            add(entry("https://easylist-downloads.adblockplus.org/easylist.txt", "Easylist"))
            when (language.uppercase(Locale.ROOT)) {
                "ZH" -> { add(entry("https://easylist-downloads.adblockplus.org/easylistchina.txt", "Easylist China")); add(entry("https://fastly.jsdelivr.net/gh/cjx82630/cjxlist/cjx-annoyance.txt", "CJX's Annoyance List")) }
                "VI" -> { add(entry("https://easylist-downloads.adblockplus.org/abpvn.txt", "Vietnamese List")) }
                "TR" -> { add(entry("https://raw.githubusercontent.com/AdguardTeam/FiltersRegistry/master/filters/filter_13_Turkish/filter.txt", "Adguard filter (Turkish)")) }
                "RU" -> { add(entry("https://raw.githubusercontent.com/AdguardTeam/FiltersRegistry/master/filters/filter_1_Russian/filter.txt", "Adguard filter (Russian)")) }
                "RO" -> { add(entry("https://easylist-downloads.adblockplus.org/rolist.txt", "Romania List")) }
                "PL" -> { add(entry("https://easylist-downloads.adblockplus.org/easylistpolish.txt", "Easylist Polish")) }
                "NL" -> { add(entry("https://raw.githubusercontent.com/AdguardTeam/FiltersRegistry/master/filters/filter_8_Dutch/filter.txt", "Adguard filter (Dutch)")) }
                "LV" -> { add(entry("https://easylist-downloads.adblockplus.org/latvianlist.txt", "Easylist Latvian")) }
                "LT" -> { add(entry("https://easylist-downloads.adblockplus.org/easylistlithuania.txt", "Easylist Lithuania")) }
                "KO" -> { add(entry("https://easylist-downloads.adblockplus.org/koreanlist.txt", "Korean List")) }
                "JA" -> { add(entry("https://raw.githubusercontent.com/AdguardTeam/FiltersRegistry/master/filters/filter_7_Japanese/filter.txt", "Adguard filter (Japanese)")) }
                "IW" -> { add(entry("https://easylist-downloads.adblockplus.org/israellist.txt", "Easylist Heberw")) }
                "IT" -> { add(entry("https://easylist-downloads.adblockplus.org/easylistitaly.txt", "Easylist Italy")) }
                "IN" -> { add(entry("https://easylist-downloads.adblockplus.org/indianlist.txt", "Easylist Indian")); add(entry("https://easylist-downloads.adblockplus.org/abpindo.txt", "Easylist Indonesian")) }
                "FR" -> { add(entry("https://raw.githubusercontent.com/AdguardTeam/FiltersRegistry/master/filters/filter_16_French/filter.txt", "Adguard filter (French)")) }
                "ES", "PT" -> { add(entry("https://raw.githubusercontent.com/AdguardTeam/FiltersRegistry/master/filters/filter_9_Spanish/filter.txt", "Adguard filter (Spanish/Portuguese)")) }
                "DE" -> { add(entry("https://raw.githubusercontent.com/AdguardTeam/FiltersRegistry/master/filters/filter_6_German/filter.txt", "Adguard filter (German)")) }
                "CS", "SK" -> { add(entry("https://easylist-downloads.adblockplus.org/easylistczechslovak.txt", "Easylist Czech Slovak")) }
                "BG" -> { add(entry("https://easylist-downloads.adblockplus.org/bulgarian_list.txt", "Easylist Bulgarian")) }
                "AR" -> { add(entry("https://easylist-downloads.adblockplus.org/Liste_AR.txt", "Easylist Arbic")) }
            }
            add(entry("https://easylist-downloads.adblockplus.org/easyprivacy.txt", "Easylist Privacy"))
            add(entry("https://easylist-downloads.adblockplus.org/antiadblockfilters.txt", "Easylist Warning Removal"))
        }
    }
}
