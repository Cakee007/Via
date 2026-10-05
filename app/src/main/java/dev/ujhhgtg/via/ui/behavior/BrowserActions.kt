package dev.ujhhgtg.via.ui.behavior

import android.content.Context
import android.view.KeyEvent
import dev.ujhhgtg.via.R

/** hb.v4 IDs and hb.l6 picker order; these are distinct from i8.l menu IDs. */
object BrowserActions {
    data class Entry(val id: Int, val labelRes: Int) {
        fun title(context: Context) = context.getString(labelRes)
    }
    val entries = listOf(
        Entry(0, R.string.operation_none), Entry(1, R.string.operation_reload),
        Entry(2, R.string.operation_top), Entry(3, R.string.operation_bottom),
        Entry(4, R.string.operation_input), Entry(5, R.string.action_new_tab),
        Entry(6, R.string.action_add_bookmark), Entry(7, R.string.operation_openbookmark),
        Entry(8, R.string.operation_openhistory), Entry(9, R.string.operation_closetab),
        Entry(10, R.string.operation_lasttab), Entry(11, R.string.operation_nexttab),
        Entry(12, R.string.operation_goback), Entry(13, R.string.operation_goforward),
        Entry(14, R.string.action_find), Entry(15, R.string.action_translate),
        Entry(16, R.string.action_close_all_tabs), Entry(17, R.string.page_up), Entry(18, R.string.page_down),
        Entry(19, R.string.action_save), Entry(20, R.string.action_source),
        Entry(24, R.string.read_aloud), Entry(25, R.string.reader_mode),
        Entry(26, R.string.operation_opensettings), Entry(27, R.string.duplicate_tab),
        Entry(28, R.string.operation_opendownloads),
        Entry(30, R.string.operation_open_bookmarks_dialog),
    ).associateBy { it.id }
    val pickerOrder = listOf(0,5,10,11,12,13,9,16,27,7,30,8,28,26,14,15,24,25,4,19,1,20,6,18,17,2,3)

    sealed class KeyCommand {
        data class Action(val id: Int) : KeyCommand()
        data class SelectTab(val index: Int) : KeyCommand() // -1 means the last tab.
        object Menu : KeyCommand()
        object Home : KeyCommand()
        object Dismiss : KeyCommand()
    }

    /** c8.s6.z9; volume eligibility and key-up handling belong to the active browser surface. */
    fun keyCommand(event: KeyEvent, volumeScrollEnabled: Boolean, volumeAllowed: Boolean): KeyCommand? {
        if (event.action != KeyEvent.ACTION_DOWN) return null
        val key = event.keyCode
        val ctrl = event.isCtrlPressed
        val alt = event.isAltPressed
        val shift = event.isShiftPressed
        fun action(id: Int) = KeyCommand.Action(id)
        return when {
            key == 3 && alt -> KeyCommand.Home
            key == 30 && ctrl && shift -> action(7)
            key == 40 && ctrl -> action(4)
            key == 46 && ctrl -> action(1)
            key == 51 && ctrl -> action(9)
            key == 61 && ctrl -> action(if (shift) 10 else 11)
            key == 82 && alt -> KeyCommand.Menu
            key == 84 -> action(4)
            key == 111 -> KeyCommand.Dismiss
            key == 135 -> action(1)
            (key == 21 || key == 22) && alt -> action(if (key == 21) 12 else 13)
            (key == 24 || key == 25) && volumeAllowed && volumeScrollEnabled -> action(if (key == 24) 17 else 18)
            key == 48 && ctrl -> action(5)
            key == 49 && ctrl -> action(20)
            key == 92 || key == 93 -> action(if (key == 92) 17 else 18)
            key in 8..16 && ctrl -> KeyCommand.SelectTab(if (key == 16) -1 else key - 8)
            key == 32 && ctrl -> action(6)
            key == 34 && ctrl -> action(14)
            key == 34 && alt -> KeyCommand.Menu
            key == 35 && ctrl -> action(if (shift) 22 else 21) // find previous/next, not picker entries.
            key == 36 && ctrl -> action(8)
            else -> null
        }
    }
}
