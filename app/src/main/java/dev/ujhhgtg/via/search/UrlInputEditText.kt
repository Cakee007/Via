package dev.ujhhgtg.via.search

import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText

/** tb.e: an IME backspace at position zero clears the active engine shortcut. */
class UrlInputEditText(context: Context) : EditText(context) {
    var onDeleteAtStart: Runnable? = null
    private fun deleteAtStart(before: Int): Boolean {
        val action = onDeleteAtStart
        if (before <= 0 || selectionStart != 0 || selectionEnd != 0 || action == null) return false
        post(action)
        return true
    }
    override fun onCreateInputConnection(info: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(info) ?: return null
        return object : InputConnectionWrapper(connection, false) {
            override fun deleteSurroundingText(before: Int, after: Int): Boolean =
                deleteAtStart(before) || super.deleteSurroundingText(before, after)
            override fun deleteSurroundingTextInCodePoints(before: Int, after: Int): Boolean =
                deleteAtStart(before) || super.deleteSurroundingTextInCodePoints(before, after)
        }
    }
}
