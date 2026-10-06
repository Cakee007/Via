package dev.ujhhgtg.via.home

/** i6/o.b CSS, resolved from both original smali and CFR where JADX duplicated branches. */
object HomeStyle {
    private data class Colors(val text: String, val tap: String, val stroke: String, val bar: String, val barText: String, val suggestion: String, val suggestionText: String)
    fun css(c: HomeDesign): String {
        val light = c.lightForegroundBackground
        val hasBackground = !c.backgroundPath.isNullOrEmpty()
        val dayText = if (light) "#1b1b1b" else "#fafafa"
        val barDay = "rgba(255, 255, 255, ${c.searchAlpha})"
        val barNight = "rgba(${if (light) "0, 0, 0" else "255, 255, 255"}, ${c.searchAlpha})"
        val textDay = if (c.searchAlpha.toDouble() < .5) dayText else "#1b1b1b"
        val textNight = if (!light && c.searchAlpha.toDouble() >= .5) "#1b1b1b" else "#afafaf"
        val opaqueAlpha = if (c.searchAlpha.toDouble() < .85) "0.85" else c.searchAlpha.toString()
        val day = Colors(dayText, if (light) "rgba(0,0,0,0.1)" else "rgba(255,255,255,0.1)",
            "rgba(${if (light) "0, 0, 0" else "255, 255, 255"}, ${c.strokeAlpha})", barDay, textDay,
            if (c.blur) barDay else "rgba(255, 255, 255, $opaqueAlpha)", if (c.blur) textDay else "#1b1b1b")
        val night = Colors("#afafaf", "rgba(255,255,255,0.1)", "rgba(233, 233, 233, ${c.strokeAlpha})", barNight, textNight,
            if (c.blur) barNight else "rgba(${if (!light && hasBackground) "255, 255, 255" else "0, 0, 0"}, $opaqueAlpha)",
            if (c.blur) textNight else if (!light && hasBackground) "#1b1b1b" else "#afafaf")
        val active = if (c.dark) night else day
        val opposite = if (c.dark) day else night
        val border = if (c.searchLine) "border-bottom" else "border"
        val blur = if (c.blur) "backdrop-filter: blur(10px);" else ""
        return buildString {
            append("""
                * { padding:0; margin:0; box-sizing:border-box; user-drag:none; -webkit-user-drag:none; }
                html { height:100%; -webkit-focus-ring-color:transparent; -webkit-tap-highlight-color:${active.tap}; font-family:ui-sans-serif,-apple-system,system-ui,Segoe UI,Helvetica,Apple Color Emoji,Arial,sans-serif,Segoe UI Emoji,Segoe UI Symbol; }
                body { min-height:100%; max-width:100%; width:600px; margin:auto; text-align:center; }
                #gesture-indicator { height:100%; width:100%; max-width:600px; position:absolute; top:0; bottom:0; z-index:0; overflow:hidden; word-break:break-all; }
                #content { position:absolute; max-width:600px; width:100%; }
                .logo { color:${active.text}; font-size:${c.logoFontSize}px; ${if (c.logoBold) "font-weight:bold;" else ""} ${if (c.logoItalic) "font-style:italic;" else ""} white-space:normal; word-wrap:break-word; overflow:auto; text-decoration:none; }
                img.smaller { ${if (c.logoWidth != 0) "width:${c.logoWidth}px;" else ""} ${if (c.logoHeight != 0) "height:${c.logoHeight}px;" else ""} border-radius:${c.logoRadius}px; object-fit:cover; }
                span { display:block; overflow:hidden; padding-left:5px; vertical-align:middle; }
                .search_part { display:table; vertical-align:middle; width:90%; max-width:600px; margin:0 auto 20px; padding:0; }
                .search.icon { width:12px; height:12px; border:solid 2px currentColor; border-radius:100%; -webkit-transform:rotate(-45deg); transform:rotate(-45deg); text-align:center; margin:auto; }
                .search.icon:before { content:''; position:absolute; top:10px; left:3px; height:5px; width:2px; background-color:currentColor; }
                .search_bar { display:table; width:100%; margin:15px auto 0; $border:${c.strokeWidth}px solid ${active.stroke}; border-radius:${c.searchRadius}px; background:${active.bar}; color:${active.barText}; $blur }
                #search_input { height:46px; padding:0 12px; width:100%; outline:none; border:none; font-size:15px; background-color:transparent; }
                #search_submit { display:none; outline:none; height:46px; width:56px; float:right; font-size:15px; font-weight:bold; border:none; background-color:transparent; padding:0 10px; }
                .search.icon, #search_input, #search_submit { color:inherit; }
                .opSug_wpr { background:${active.suggestion}; $blur border:${c.strokeWidth}px solid ${active.stroke}; border-radius:0 0 ${c.searchRadius}px ${c.searchRadius}px; overflow-y:scroll; line-height:normal; position:absolute; width:90%; max-width:600px; margin:-${c.strokeWidth}px 0 0; z-index:9999; }
                .opSug_wpr::-webkit-scrollbar { width:0; }
                .opSug_wpr table { background:none; padding:0; width:100%; border-spacing:0; }
                .opSug_wpr tr { padding:0; margin:0; display:table-row; vertical-align:inherit; border-color:inherit; }
                .opSug_wpr tr:hover { color:#FFF; background:#7B90E3; }
                .opSug_wpr td { color:${active.suggestionText}; font-size:14px; padding:10px 17px; background:none; text-align:left; vertical-align:middle; font:14px verdana; text-decoration:none; text-indent:0; }
                .search_bar_active { border-radius:${c.searchRadius}px ${c.searchRadius}px 0 0; color:${active.suggestionText}; background:${active.suggestion}; }
                #bookmark_part { text-align:center; max-width:600px; background-color:transparent; margin:0 auto; padding:0; border-radius:0; }
                #box_container { text-align:left; margin:0 auto; font-size:0; }
                .box { vertical-align:top; margin:4px 9px 4px; width:${c.favoriteWidth}px; border:0; position:relative; display:inline-block; text-align:center; }
                .box a { width:100%; height:100%; position:absolute; left:0; top:0; }
                .overlay { position:absolute; left:0; top:0; border-radius:${c.favoriteRadius}px; width:${c.favoriteWidth}px; height:${c.favoriteHeight}px; }
                .title { border-radius:${c.favoriteRadius}px; color:${if (c.favoriteColorDisabled) active.text else "#ffffff"}; width:${c.favoriteWidth}px; line-height:${c.favoriteHeight}px; height:${c.favoriteHeight}px; font-size:15px; }
                .url { color:${active.text}; margin:2px 0 0; width:${c.favoriteWidth}px; height:20px; line-height:20px; white-space:normal; word-wrap:break-word; overflow:hidden; text-overflow:clip; ms-text-overflow:clip; font-size:10px; }
            """.trimIndent())
            if (c.dark) append("img.smaller,.overlay,.title{-webkit-filter:brightness(75%);filter:brightness(75%);}")
            val itemWidth = c.favoriteWidth + 18
            for (columns in 1..540 / itemWidth) append("@media only screen and (min-width:${(columns + if (c.searchEnabled) 1 else 0) * itemWidth}px){#box_container{width:${columns * itemWidth}px}}")
            if (c.searchEnabled) {
                append("#content{top:25%;transition:.14s;}")
                listOf(250 to 62, 350 to 87, 450 to 135, 650 to 195, 850 to 255).forEach { (height, top) -> append("@media only screen and (min-height:${height}px){#content{top:${top}px}}") }
            } else append("#content{top:18px}.search_part{display:none}")
            if (c.rtl) append("html{direction:rtl}#box_container{text-align:right}#search_submit{float:left}")
            append(".sort-ghost{opacity:.3}")
            append("@media(prefers-color-scheme:${if (c.dark) "light" else "dark"}){")
            append("html{-webkit-tap-highlight-color:${opposite.tap}}.logo,.url{color:${opposite.text}}.title{color:${if (c.favoriteColorDisabled) opposite.text else "#ffffff"}}")
            append(".search_bar{$border:${c.strokeWidth}px solid ${opposite.stroke};background:${opposite.bar};color:${opposite.barText}}")
            if (!c.dark) append("img.smaller,.overlay,.title{-webkit-filter:brightness(75%);filter:brightness(75%)}")
            append(".opSug_wpr{background:${opposite.suggestion};border:${c.strokeWidth}px solid ${opposite.stroke}}.search_bar_active{color:${opposite.suggestionText};background:${opposite.suggestion}}.opSug_wpr td{color:${opposite.suggestionText}}}")
            append(c.extraCss)
        }
    }
}
