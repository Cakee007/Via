package dev.ujhhgtg.via.records

import android.content.Intent
import android.view.View
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.data.BookmarkItem
import dev.ujhhgtg.via.ui.dialog.ViaDialog

/** cb.e0.X3/b3: the original seven actions, without a popup title. */
internal fun showBookmarkPopup(anchor: View, item: BookmarkItem, host: Shell, onEdit: () -> Unit, onDelete: () -> Unit, onAddToHomepage: () -> Unit) {
    ViaDialog(host).items(arrayOf(host.getString(R.string.action_open_in_background), host.getString(R.string.action_open_in_new),
        host.getString(R.string.action_edit), host.getString(R.string.action_add_to_homepage), host.getString(R.string.action_delete),
        host.getString(R.string.action_copy), host.getString(R.string.action_share)), onClick = { which -> when (which) {
            0 -> host.openRecordFromRecords(item.url, 2)
            1 -> host.openRecordFromRecords(item.url, 1)
            2 -> onEdit()
            3 -> onAddToHomepage()
            4 -> ViaDialog(host).title(R.string.action_delete).message(host.getString(R.string.delete_item_message, item.title.orEmpty()))
                .positive(android.R.string.ok) { _, _ -> onDelete() }.negative(android.R.string.cancel).show()
            5 -> host.copyRecordFromRecords(item.url)
            6 -> host.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, item.url), host.getString(R.string.action_share)))
        }
    }).showAnchored(anchor)
}
