package dev.ujhhgtg.via.ui.behavior

import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.BrowserMenu

/** hb.u4/l7 and i8.s/w state. UI and browser dispatch share these original keys. */
class BehaviorPreferences(private val preferences: BrowserPreferences) {
    var backForwardGesture: Boolean get() = hasFlag(32); set(value) = flag(32, value)
    var volumeScroll: Boolean get() = hasFlag(8); set(value) = flag(8, value)
    var videoGestures: Boolean get() = hasFlag(128); set(value) = flag(128, value)
    var colorToolbars: Boolean get() = hasFlag(2); set(value) = flag(2, value)
    var tabBarEnabled: Boolean get() = hasFlag(256); set(value) = flag(256, value)
    var toolbarMode: Int
        get() = preferences.appUi
        set(value) { preferences.appUi = value; if (value != 1 && value != 2) tabBarEnabled = false }
    var autoHideMode: Int get() = preferences.fullScreenMode; set(value) { preferences.fullScreenMode = value }
    var urlContent: Int get() = preferences.urlBoxMode; set(value) { preferences.urlBoxMode = value }

    fun longPressAction(slot: Int): Int = when (slot) {
        0 -> preferences.backShortcut; 1 -> preferences.forwardShortcut; 2 -> preferences.homeShortcut
        3 -> preferences.tabShortcut; 4 -> preferences.menuShortcut
        5 -> preferences.gestureToolbarLeft; 6 -> preferences.gestureToolbarRight
        else -> 0
    }
    fun setLongPressAction(slot: Int, id: Int) {
        if (id !in BrowserActions.entries) return
        when (slot) {
            0 -> preferences.backShortcut = id; 1 -> preferences.forwardShortcut = id; 2 -> preferences.homeShortcut = id
            3 -> preferences.tabShortcut = id; 4 -> preferences.menuShortcut = id
            5 -> preferences.gestureToolbarLeft = id; 6 -> preferences.gestureToolbarRight = id
        }
    }
    fun toolbarSwipeAction(fraction: Float, released: Boolean): Int? =
        if (!released || kotlin.math.abs(fraction).toDouble() <= .8) null else longPressAction(if (fraction > 0f) 5 else 6)

    /** i8.s.x3: persist the two ordered menu groups after a grid drag. */
    fun reorderMenus(displayed: List<Int>, hidden: List<Int>) {
        val shown = displayed.filter { it in BrowserMenu.entries }.distinct().toMutableList()
        if (10 !in shown) shown.add(10)
        val concealed = hidden.filter { it in BrowserMenu.entries && it !in shown }.distinct()
        preferences.displayedMenus = shown.joinToString(",")
        preferences.hiddenMenus = concealed.joinToString(",")
    }
    fun resetMenus() { preferences.displayedMenus = null; preferences.hiddenMenus = null }
    fun hiddenContextMenus(): List<Int> = parse(preferences.hiddenContextMenus) ?: listOf(6)
    fun contextMenuVisible(id: Int): Boolean = id !in hiddenContextMenus()
    fun setContextMenuVisible(id: Int, visible: Boolean) {
        val hidden = hiddenContextMenus().toMutableList().apply { remove(id); if (!visible) add(id) }
        preferences.hiddenContextMenus = hidden.joinToString(",")
    }
    fun resetContextMenus() { preferences.hiddenContextMenus = null }
    private fun parse(value: String?): List<Int>? = value?.split(',')?.mapNotNull(String::toIntOrNull)
    private fun hasFlag(mask: Int) = preferences.appFlags and mask != 0
    private fun flag(mask: Int, value: Boolean) { preferences.appFlags = if (value) preferences.appFlags or mask else preferences.appFlags and mask.inv() }
}
