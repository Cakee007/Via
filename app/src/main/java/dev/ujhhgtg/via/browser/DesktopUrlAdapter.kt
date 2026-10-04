package dev.ujhhgtg.via.browser

/** i6.h.m/n/s: original ordered desktop/mobile URL adapters; null means reload the existing URL. */
object DesktopUrlAdapter {
    fun adapt(url: String?, desktop: Boolean): String? {
        if (url.isNullOrEmpty()) return null
        return if (desktop) baiduDesktop(url) ?: panDesktop(url) ?: weiboDesktop(url) ?: toutiaoDesktop(url)
            ?: hupuDesktop(url) ?: ithomeDesktop(url) ?: copyrightDesktop(url) ?: hanyuDesktop(url)
            ?: qqMail(url, true) ?: pairedSite(url, true)
        else panMobile(url) ?: weiboMobile(url) ?: toutiaoMobile(url) ?: hupuMobile(url)
            ?: ithomeMobile(url) ?: copyrightMobile(url) ?: youtubeMobile(url) ?: hanyuMobile(url)
            ?: qqMail(url, false) ?: pairedSite(url, false)
    }

    /** i6.h.l: order and duplicate entries are intentional, including the first Taobao inverse. */
    private val sitePairs = listOf(
        "://m.so.com" to "://www.so.com",
        "://m.news.so.com" to "://news.so.com",
        "://m.video.360kan.com" to "://video.360kan.com",
        "://m.image.so.com" to "://image.so.com",
        "://m.163.com" to "://www.163.com",
        "://m.thepaper.cn" to "://www.thepaper.cn",
        "://m.hao123.com" to "://www.hao123.com",
        "://m.sohu.com/a/" to "://www.sohu.com/a/",
        "://m.huanqiu.com" to "://www.huanqiu.com",
        "://m.guancha.cn" to "://www.guancha.cn",
        "://m.xs8.cn" to "://www.xs8.cn",
        "://m.autohome.com.cn" to "://www.autohome.com.cn",
        "://m.douban.com/movie/" to "://movie.douban.com/",
        "://m.douban.com/book/" to "://book.douban.com/",
        "://m.douban.com/music/" to "://music.douban.com/",
        "://m.douban.com/game/" to "://game.douban.com/",
        "://m.douban.com/people/" to "://www.douban.com/people/",
        "://m.hao123.com" to "://www.hao123.com",
        "://m.guoxuedashi.net" to "://www.guoxuedashi.net",
        "://h5.cloud.189.cn/main.html#/cloud/file/" to "://cloud.189.cn/web/main/file/folder/",
        "://new.m.taobao.com/detail.htm?" to "://item.taobao.com/item.htm?",
        "://h5.m.taobao.com/awp/core/detail.htm?" to "://item.taobao.com/item.htm?",
        "://main.m.taobao.com/security-h5-detail/home?" to "://item.taobao.com/item.htm?",
        "://main.m.taobao.com/search/index.html?" to "://s.taobao.com/search?",
        "://main.m.taobao.com" to "://www.taobao.com",
        "://item.m.jd.com/product/" to "://item.jd.com/",
        "://pro.m.jd.com" to "://pro.jd.com",
        "://m.jd.com" to "://www.jd.com",
        "://m.qidian.com" to "://www.qidian.com",
        "://gf2-bbs.sunborngame.com/m/" to "://gf2-bbs.sunborngame.com/",
        "://m.cngwzj.com/" to "://www.cngwzj.com/",
        "://m.twitch.tv/" to "://www.twitch.tv/",
        "://m.rumanhua.com/" to "://www.rumanhua.com/",
        "://m.zhtj.youth.cn/zhtj/" to "://zhtj.youth.cn/zhtj/",
        "://m.guancha.cn/" to "://www.guancha.cn/",
        "://m.cctv.com/" to "://www.cctv.com/",
    )
    private fun pairedSite(url: String, desktop: Boolean): String? {
        for ((mobile, pc) in sitePairs) {
            val source = if (desktop) mobile else pc
            val index = url.indexOf(source)
            if (index > 0) return url.substring(0, index) + (if (desktop) pc else mobile) + url.substring(index + source.length)
        }
        return null
    }

    /** i6.h.i: vertical Baidu searches and mobile web search (no symmetric generic reverse rule). */
    private fun baiduDesktop(url: String): String? {
        var index = url.indexOf(".baidu.com/sf/vsearch?pd=")
        if (index <= 0) {
            index = url.indexOf("://m.baidu.com")
            if (index <= 0) return null
            val tail = url.substring(index + 14).let { if (it.contains("ie=")) it else it.replace("/s?", "/s?ie=utf-8&") }
            return url.substring(0, index) + "://www.baidu.com" + tail
        }
        val categoryStart = index + 25
        val categoryEnd = url.indexOf('&', categoryStart)
        if (categoryEnd < 0) return null
        val category = url.substring(categoryStart, categoryEnd)
        val word = url.indexOf("&word=", categoryEnd)
        if (word < 0) return null
        val start = word + 6
        val value = url.substring(start, url.indexOf('&', start).let { if (it < 0) url.length else it })
        val prefix = when (category) {
            "wenda_tab" -> "https://zhidao.baidu.com/search?ie=utf-8&word="
            "image_content" -> "https://image.baidu.com/search/index?tn=baiduimage&ie=utf-8&word="
            "wenku" -> "https://wenku.baidu.com/search?word="
            "tieba" -> "https://tieba.baidu.com/f?ie=utf-8&kw="
            else -> return null
        }
        return prefix + value
    }

    private fun panDesktop(url: String): String? {
        val index = url.indexOf("://pan.baidu.com/wap/home")
        if (index <= 0) return null
        val folder = url.indexOf("/dir/", index + 25)
        val root = "https://pan.baidu.com/disk/main#/index?category=all"
        if (folder <= 0) return root
        val start = folder + 5
        return root + "&path=" + url.substring(start, url.indexOf('?', start).let { if (it < 0) url.length else it })
    }
    private fun panMobile(url: String): String? {
        val index = url.indexOf("://pan.baidu.com/disk/main")
        if (index <= 0) return null
        val folder = url.indexOf("&path=", index + 26)
        if (folder <= 0) return "https://pan.baidu.com/wap/home#/"
        val start = folder + 6
        return "https://pan.baidu.com/wap/home#/dir/" + url.substring(start, url.indexOf('&', start).let { if (it < 0) url.length else it })
    }

    private fun hanyuDesktop(url: String): String? {
        val index = url.indexOf("://hanyu.baidu.com/hanyu-page/")
        if (index <= 0) return null
        val word = queryValue(url, "wd=") ?: return null
        return when {
            url.startsWith("term/detail", index + 30) -> "https://hanyu.baidu.com/s?wd=$word&device=pc"
            url.startsWith("zici/s", index + 30) -> "https://hanyu.baidu.com/zici/s?wd=$word"
            else -> null
        }
    }
    private fun hanyuMobile(url: String): String? {
        val index = url.indexOf("://hanyu.baidu.com/")
        if (index <= 0 || url.startsWith("hanyu-page/", index + 19)) return null
        val word = queryValue(url, "wd=") ?: return null
        return when {
            url.startsWith("s", index + 19) -> "https://hanyu.baidu.com/hanyu-page/term/detail?wd=$word"
            url.startsWith("zici/s", index + 19) -> "https://hanyu.baidu.com/hanyu-page/zici/s?wd=$word"
            else -> null
        }
    }

    private fun copyrightDesktop(url: String): String? {
        val index = url.indexOf("://www.ccopyright.com.cn/mobile/")
        return if (index > 0) url.substring(0, index) + "://www.ccopyright.com.cn/" + url.substring(index + 32) else null
    }
    private fun copyrightMobile(url: String): String? {
        val index = url.indexOf("://www.ccopyright.com.cn/")
        if (index <= 0) return null
        val start = index + 25
        if (url.length >= index + 32 && url.substring(start, index + 32) == "mobile/") return null
        return url.substring(0, start) + "mobile/" + url.substring(start)
    }

    private val hupuSections = listOf("nba", "cba", "soccer", "gg")
    private fun hupuDesktop(url: String): String? {
        val index = url.indexOf("://m.hupu.com/bbs/")
        if (index > 0) {
            val start = index + 18
            var end = url.indexOf(".html", start)
            if (end > 0) return url.substring(0, index) + "://bbs.hupu.com/" + url.substring(start)
            if (end < 0) end = url.indexOf('?', start)
            if (end < 0) {
                if (url.indexOf('/', start) > 0) return url
                end = url.length
            }
            return url.substring(0, index) + "://bbs.hupu.com/" + url.substring(start, end) + ".html" + url.substring(end)
        }
        val host = url.indexOf("://m.hupu.com/")
        if (host <= 0) return null
        val start = host + 14
        if (start == url.length) return url.substring(0, host) + "://www.hupu.com/"
        val section = url.substring(start)
        return if (section in hupuSections) url.substring(0, host) + "://$section.hupu.com/" else null
    }
    private fun hupuMobile(url: String): String? {
        val forum = url.indexOf("://bbs.hupu.com/")
        if (forum > 0) return url.substring(0, forum) + "://m.hupu.com/bbs/" + url.substring(forum + 16)
        val host = url.indexOf(".hupu.com/")
        if (host <= 0 || host + 10 != url.length) return null
        val scheme = url.indexOf("://")
        if (scheme <= 0) return null
        val start = scheme + 3
        val section = url.substring(start, host)
        return when {
            section == "www" -> url.substring(0, start) + "m.hupu.com/"
            section in hupuSections -> url.substring(0, start) + "m.hupu.com/$section"
            else -> null
        }
    }

    private fun ithomeDesktop(url: String): String? {
        val article = url.indexOf("://m.ithome.com/html/")
        val start = article + 21
        val end = if (article > 0) url.indexOf(".htm", start) else -1
        if (article > 0 && end > 0 && end - article - 21 == 6)
            return url.substring(0, article) + "://www.ithome.com/0/" + url.substring(start, article + 24) + '/' + url.substring(end - 3)
        val host = url.indexOf("://m.ithome.com/")
        val tail = host + 16
        return if (host > 0 && (tail == url.length || tail < url.length && url[tail] == '#')) url.substring(0, host) + "://www.ithome.com/" else null
    }
    private fun ithomeMobile(url: String): String? {
        val article = url.indexOf("://www.ithome.com/0/")
        val start = article + 20
        val end = if (article > 0) url.indexOf(".htm", start) else -1
        if (article > 0 && end > 0 && end - article - 20 == 7)
            return url.substring(0, article) + "://m.ithome.com/html/" + url.substring(start, article + 23) + url.substring(end - 3)
        val host = url.indexOf("://www.ithome.com/")
        val tail = host + 18
        return if (host > 0 && (tail == url.length || tail < url.length && url[tail] == '#')) url.substring(0, host) + "://m.ithome.com/" else null
    }

    private fun qqMail(url: String, desktop: Boolean): String? {
        val source = if (desktop) "://wap.mail.qq.com/" else "://mail.qq.com/"
        return if (url.indexOf(source) > 0) if (desktop) "https://mail.qq.com/" else "https://wap.mail.qq.com/" else null
    }
    private fun toutiaoDesktop(url: String): String? =
        if (url.indexOf("://so.toutiao.com/search?") > 0 && !url.contains("dvpf=pc")) "$url&dvpf=pc" else null
    private fun toutiaoMobile(url: String): String? =
        if (url.indexOf("://so.toutiao.com/search?") > 0) removeParameter(url, "dvpf=pc") else null
    private fun youtubeMobile(url: String): String? {
        val index = url.indexOf("://www.youtube.com/")
        if (index <= 0) return null
        return removeParameter(url.substring(0, index) + "://m.youtube.com/" + url.substring(index + 19), "app=desktop")
    }

    private fun weiboDesktop(url: String): String? {
        var index = url.indexOf("://card.weibo.com/article/m/show/id/")
        if (index > 0) {
            val start = index + 36
            return "https://weibo.com/ttarticle/p/show?id=" + url.substring(start, url.indexOf('?', start).let { if (it < 0) url.length else it })
        }
        index = url.indexOf("://m.weibo.cn/detail/")
        if (index > 0) {
            val start = index + 21
            var end = url.indexOf('?', start)
            if (end < 0) end = url.indexOf('/', start)
            if (end < 0) end = url.length
            return "https://weibo.com/0/" + encodeWeiboId(url.substring(start, end)) + url.substring(end)
        }
        index = url.indexOf("://m.weibo.cn")
        return if (index > 0) url.substring(0, index) + "://weibo.com" + url.substring(index + 13) else null
    }
    private fun weiboMobile(url: String): String? {
        var index = url.indexOf("://weibo.com/ttarticle/p/show?id=")
        if (index > 0) {
            val start = index + 33
            return "https://card.weibo.com/article/m/show/id/" + url.substring(start, url.indexOf('&', start).let { if (it < 0) url.length else it })
        }
        if (url.indexOf("://weibo.com/newlogin?") > 0) return "https://m.weibo.cn/"
        index = url.indexOf("://weibo.com")
        if (index <= 0) return null
        // The original checks index+1 (smali y line91), then reads the numeric author at index+13.
        if (url.length > index + 1 && url[index + 1] == '/') {
            val author = index + 13
            val slash = url.indexOf('/', author)
            if (slash > 0 && url.substring(author, slash).let { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }) {
                val start = slash + 1
                var end = url.indexOf('?', start)
                if (end < 0) end = url.indexOf('/', start)
                if (end < 0) end = url.length
                return "https://m.weibo.cn/detail/" + decodeWeiboId(url.substring(start, end)) + url.substring(end)
            }
        }
        return url.substring(0, index) + "://m.weibo.cn" + url.substring(index + 12)
    }

    private const val base62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private fun encodeWeiboId(decimal: String): String {
        val groups = decimal.chunkedFromEnd(7)
        val out = StringBuilder()
        try {
            groups.forEachIndexed { index, value ->
                var number = value.toLong()
                val encoded = if (number == 0L) "0" else buildString {
                    while (number > 0) { insert(0, base62[(number % 62).toInt()]); number /= 62 }
                }
                out.append(if (index > 0) encoded.padStart(4, '0') else encoded)
            }
        } catch (_: NumberFormatException) { }
        return out.toString()
    }
    private fun decodeWeiboId(encoded: String): String = encoded.chunkedFromEnd(4).mapIndexed { group, text ->
        var number = 0L
        text.forEachIndexed { index, char -> number += (base62.indexOf(char) * Math.pow(62.0, (text.length - index - 1).toDouble())).toLong() }
        val decimal = number.toString()
        if (group > 0) decimal.padStart(7, '0') else decimal
    }.joinToString("")
    private fun String.chunkedFromEnd(width: Int): List<String> {
        val groups = ArrayList<String>()
        var end = length
        while (end > 0) { val start = maxOf(0, end - width); groups.add(0, substring(start, end)); end -= width }
        return groups
    }

    /** i6.i0.g/h with decode=false; fragments terminate the raw parameter value. */
    private fun queryValue(url: String, key: String): String? {
        if (key.length >= url.length) return null
        var index = 0
        while (true) {
            index = url.indexOf(key, index)
            if (index < 0) return null
            if (index > 0 && (url[index - 1] == '&' || url[index - 1] == '?')) break
            index += key.length
        }
        val start = index + key.length
        val ampersand = url.indexOf('&', start)
        val hash = url.indexOf('#', start)
        val end = if (hash > 0 && (hash < ampersand || ampersand < 0)) hash else ampersand
        return url.substring(start, if (end < 0) url.length else end)
    }
    /** i6.h.A preserves the original trailing '?' and returns null unless the complete token matches. */
    private fun removeParameter(url: String, parameter: String): String? {
        val index = url.indexOf(parameter)
        if (index <= 0) return null
        val end = index + parameter.length
        return when (url[index - 1]) {
            '?' -> when { end < url.length && url[end] == '&' -> url.substring(0, index) + url.substring(end + 1)
                end == url.length -> url.substring(0, index); else -> null }
            '&' -> if (url.length < end + 1 || url[end] == '&') url.substring(0, index - 1) + url.substring(end) else null
            else -> null
        }
    }
}
