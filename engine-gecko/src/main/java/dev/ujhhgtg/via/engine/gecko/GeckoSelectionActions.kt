package dev.ujhhgtg.via.engine.gecko

import android.app.Activity
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.core.view.get
import androidx.core.view.size
import dev.ujhhgtg.via.engine.SelectionAction
import org.mozilla.geckoview.BasicSelectionActionDelegate

/** Gecko's selection toolbar plus the app's entries; entries marked hidden are removed by title. */
internal class GeckoSelectionActions(
    activity: Activity,
    actions: List<SelectionAction>,
    private val onClick: ((Int, String) -> Unit)?,
) : BasicSelectionActionDelegate(activity) {
    private val added = actions.filterNot { it.hide }
    private val hidden = actions.filter { it.hide }.map { it.title }.toSet()

    override fun onPrepareActionMode(actionMode: ActionMode, menu: Menu): Boolean {
        var changed = super.onPrepareActionMode(actionMode, menu)
        for (index in menu.size - 1 downTo 0) {
            val item = menu[index]
            if (item.title?.toString() in hidden) { menu.removeItem(item.itemId); changed = true }
        }
        added.forEachIndexed { index, action ->
            if (menu.findItem(FIRST_ID + index) == null) {
                menu.add(Menu.NONE, FIRST_ID + index, FIRST_ID + index, action.title)
                changed = true
            }
        }
        return changed
    }

    override fun onActionItemClicked(actionMode: ActionMode, menuItem: MenuItem): Boolean {
        val action = added.getOrNull(menuItem.itemId - FIRST_ID) ?: return super.onActionItemClicked(actionMode, menuItem)
        onClick?.invoke(action.id, mSelection?.text.orEmpty())
        return true
    }

    fun finish() {
        clearSelection()
        mActionMode?.finish()
    }

    private companion object {
        const val FIRST_ID = 0x1000
    }
}
