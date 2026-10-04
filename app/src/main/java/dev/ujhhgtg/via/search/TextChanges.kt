package dev.ujhhgtg.via.search

import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

/** u8.a/d: emit initial text after installing the watcher; detach the watcher on the main thread. */
internal fun TextChanges(view: TextView) = callbackFlow {
    val listener = object : TextWatcher {
        override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun afterTextChanged(text: Editable?) = Unit
        override fun onTextChanged(text: CharSequence, start: Int, before: Int, count: Int) {
            trySend(text.toString())
        }
    }
    view.addTextChangedListener(listener)
    trySend(view.text.toString())
    awaitClose { view.removeTextChangedListener(listener) }
}
