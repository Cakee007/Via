package dev.ujhhgtg.via

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import dev.ujhhgtg.via.data.BrowserPreferences

class Trampoline : Activity() {
    override fun onCreate(state: Bundle?) { super.onCreate(state); dispatch(intent) }
    override fun onNewIntent(intent: Intent?) { super.onNewIntent(intent); dispatch(intent) }

    private fun dispatch(source: Intent?) {
        val custom = source?.hasExtra("android.support.customtabs.extra.SESSION") == true &&
                BrowserPreferences(this).disableCustomTabs.not()
        val forwarded = if (source == null) Intent(this, Shell::class.java) else Intent(source)
        if (custom) forwarded.setClass(this, CustomTab::class.java)
        else {
            // mark.via.Trampoline.b removes FLAG_ACTIVITY_CLEAR_TASK (0x8000) before Shell.
            forwarded.removeFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            forwarded.setClass(this, Shell::class.java)
        }
        startActivity(forwarded)
        finish()
    }
}
