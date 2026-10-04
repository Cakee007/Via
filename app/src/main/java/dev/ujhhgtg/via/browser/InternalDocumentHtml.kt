package dev.ujhhgtg.via.browser

import android.content.Context
import android.view.View
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.home.HomeDesign

/** u9.d.b/e/g/h/i/j and z8.v0.c: literal original document markup and theme branches. */
internal object InternalDocumentHtml {
    const val BODY = "<body><div class='frosted-glass' id='gesture-indicator'></div><div id=\"content\">"
    const val END = "</div></body></html>"

    fun head(title: String?) = "<!DOCTYPE html><html><head><meta content=\"text/html; charset=utf-8\" http-equiv=\"Content-Type\"/><meta name=\"color-scheme\" content=\"light dark\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1, user-scalable=no, minimal-ui\">" +
        (if (title.isNullOrEmpty()) "" else "<title>$title</title>") + "</head>"
    fun style(value: String) = "<style>$value</style>"
    fun hint(value: String) = "<div class='hint'>${value.replace("\n", "<br>")}</div>"
    fun row(url: String, title: String, icon: String?): String {
        val cleanedTitle = title.replace(Regex("<.*?>"), "")
        return "<div class=\"box\">" +
            (if (icon.isNullOrEmpty()) "" else "<div class=\"icon_handle\"><div class=\"$icon icon\"></div></div>") +
            "<a href=\"${encodeAttribute(url)}\" title=\"$cleanedTitle\"></a><p class=\"title\">$cleanedTitle</p></div>"
    }
    private fun encodeAttribute(value: String) = buildString {
        for (character in value) {
            if (character.code > 127 || character in "\"'<>&") append("&#${character.code};") else append(character)
        }
    }

    fun css(context: Context, preferences: BrowserPreferences, about: Boolean = false): String {
        val dark = preferences.isNightMode
        val rtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val lightBackground = if (preferences.backgroundInfo and 128 != 0) preferences.backgroundInfo and 256 != 0
            else HomeDesign.isLight(preferences.urlBarColor)
        val lightText = dark || !lightBackground
        val ink = if (lightText) "#fafafa" else "#1b1b1b"
        val secondary = if (lightText) "#d5d5d5" else "#2b2b2b"
        val icon = if (lightText) "#e9e9e9" else "#313131"
        val tap = if (lightText) "rgba(255, 255, 255, 0.1)" else "rgba(0, 0, 0, 0.1)"
        if (about) return buildString {
            append("* {padding:0;margin:0;box-sizing:border-box;}")
            append("html{height:100%;-webkit-tap-highlight-color:$tap;-webkit-focus-ring-color: rgba(0, 0, 0, 0);}")
            append("body{min-height:100%;max-width:100%; width: 600px;margin: auto;text-align: center;}")
            if (rtl) append("html{direction:rtl;}")
            append("body{background: transparent;}")
            append("div{color: $ink;max-width:800px; padding-left:5%; padding-right:5%; text-align:left;}")
            append("div>h1{font-size: 20px; padding-top:55px; padding-bottom:45px; text-align: left; }")
            append("div>p{ text-align: left; line-height: 1.8em; text-indent:2em;font-size: 15px;}")
            append("a{color: #6f8de1; font-size: 15px; line-height: 1.8em; text-decoration:none; }")
            if (rtl) append("div,h1,p,div>h1,div>p{text-align:right;}")
        }
        return """* {padding:0;margin:0;box-sizing:border-box;}html{height:100%;-webkit-tap-highlight-color:$tap;-webkit-focus-ring-color: rgba(0, 0, 0, 0);}body{min-height:100%;max-width:100%; width: 600px;margin: auto;text-align: center;}${if (rtl) "html{direction:rtl;}" else ""}body{background: transparent;}#content {text-align:left;}.box {vertical-align:middle;position:relative;display: block;padding: 20px 14px;border-bottom: 0px solid $tap}.box a {width: 100%;height: 100%;position: absolute;left: 0;top: 0;}.title {padding-left: 28px;color: $ink;font-size: 15px;white-space: nowrap; overflow: hidden;text-overflow: ellipsis;margin:auto;}.url {display: none;}.hint {color: $secondary;font-size: 15px; white-space: normal; word-wrap: break-word; overflow: auto;text-overflow: ellipsis;padding: 50px 5px;text-align: center;margin: auto; line-height: 1.8em;}.tag.icon {color: $icon; position: absolute; margin-left: 3px; margin-top: 5px; width: 7px; height: 7px; border-radius: 1px 1px 1px 0; border-left: solid 1px currentColor; border-top: solid 1px currentColor; } .tag.icon:before {content: ''; position: absolute; left: 1px; top: 1px; width: 9px; height: 10px; border-radius: 1px; border-left: solid 1px currentColor; border-right: solid 1px currentColor; border-bottom: solid 1px currentColor; -webkit-transform: rotate(-45deg); transform: rotate(-45deg); } .tag.icon:after {content: ''; position: absolute; left: 3px; top: 3px; width: 2px; height: 2px; border-radius: 50%; background-color: currentColor; }.icon_handle {width:50px;/*height:100%;position:absolute;top:0;left:0;*/ z-index: 999; }.bookmark.icon{color: $icon;position:absolute;margin-left:5px;margin-top:3px;width:12px;height:15px;border-radius:1px 1px 0 0;border-top:solid 1px currentColor;border-left:solid 1px currentColor;border-right:solid 1px currentColor}.bookmark.icon:before{content:'';position:absolute;top:10px;left:1px;width:7px;height:7px;border-top:solid 1px currentColor;border-left:solid 1px currentColor;-webkit-transform:rotate(45deg);transform:rotate(45deg)}.clock.icon {color: $icon; position: absolute; margin-left: 1px; margin-top: 5px; width: 15px; height: 15px; border: solid 1px currentColor; border-radius: 8px;} .clock.icon:before {content: ''; position: absolute; top: 7px; left: 6.5px; width: 4.5px; height: 1px; background-color: currentColor; -webkit-transform-origin: 0% 0%; transform-origin: 0% 0%; transform:rotate(45deg);} .clock.icon:after {content: ''; position: absolute; top: 2px; left: 5.8px; width: 1px; height: 6px; background-color: currentColor; -webkit-transform-origin: 0% 0%; transform-origin: 0% 0%;}.btn {padding: 12px 20px; color: #ffffff; border-radius: 2px; border: 0; outline: none;font-size: 15px;; margin: 10px;}.sort-ghost {opacity: 0.3;}${if (rtl) "#content {text-align: right;}.title{padding-left:0px;padding-right: 28px;}" else ""}"""
    }
}
