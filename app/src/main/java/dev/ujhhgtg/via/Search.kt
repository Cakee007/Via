package dev.ujhhgtg.via

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import dev.ujhhgtg.via.common.ViaIntents

class Search : Activity() {
    override fun onCreate(state: Bundle?) { super.onCreate(state); forward(intent) }
    override fun onNewIntent(intent: Intent?) { super.onNewIntent(intent); forward(intent) }

    private fun forward(source: Intent?) {
        val forwarded = if (source == null) Intent(this, Shell::class.java) else Intent(source).apply {
            setClass(this@Search, Shell::class.java)
            // c8.ua.Q0's forwarding contract, using this application's namespace.
            putExtra(ViaIntents.EXTRA_REFERER, referrer)
        }
        startActivity(forwarded)
        finish()
    }
}
