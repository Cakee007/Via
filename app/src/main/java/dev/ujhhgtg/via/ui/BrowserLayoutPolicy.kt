package dev.ujhhgtg.via.ui

/** The layout arithmetic from c8.s6.Ka/ub, without Android view dependencies. */
object BrowserLayoutPolicy {
    data class Geometry(val mode: Int, val tabBar: Boolean, val topHeight: Int, val bottomHeight: Int)

    fun resolve(
        storedMode: Int,
        tabBar: Boolean,
        landscape: Boolean,
        preferTop: Boolean,
        customTab: Boolean,
        addressHeight: Int,
        navigationHeight: Int,
        tabHeight: Int,
    ): Geometry {
        var mode = storedMode.takeIf { it in 0..4 } ?: if (preferTop) 1 else 0
        var tabs = tabBar
        if (customTab) { mode = 4; tabs = false }
        if (mode == 0 || mode == 3) {
            if (landscape) mode = if (mode == 3) 2 else 1
            tabs = false
        }
        val strip = if (tabs) tabHeight else 0
        val top = if (mode == 2 || mode == 3) 0 else addressHeight + strip
        val bottom = when (mode) {
            3 -> navigationHeight + addressHeight + strip
            1, 4 -> 0
            else -> navigationHeight + if (mode == 0 || !tabs) 0 else tabHeight
        }
        return Geometry(mode, tabs, top, bottom)
    }

    fun overlayViewport(customTab: Boolean, appFullscreen: Boolean, fullscreenPreference: Boolean, hideMode: Int): Boolean =
        !customTab && (appFullscreen || fullscreenPreference || hideMode > 0)
}
