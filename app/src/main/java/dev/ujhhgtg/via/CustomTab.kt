package dev.ujhhgtg.via

import android.content.Intent
import android.os.Bundle

class CustomTab : Shell() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val launch = intent ?: Intent(this, CustomTab::class.java)
        val scheme = launch.getIntExtra("androidx.browser.customtabs.extra.COLOR_SCHEME", 0)
        // mark.via.CustomTab selects the explicit light/dark theme before FragmentActivity.onCreate.
        if (scheme == 2) setTheme(R.style.Theme_Via_Dark)
        else if (scheme == 1) setTheme(R.style.Theme_Via)
        launch.putExtra("KEY_URL", launch.dataString)
        launch.putExtra("CUSTOM_TAB", true)
        launch.putExtra("CUSTOM_TAB_COLOR_SCHEME", scheme)
        intent = launch
        super.onCreate(savedInstanceState)
    }

    override fun onNewIntent(intent: Intent) {
        // The original forwards new targets; launch-time Custom Tab chrome arguments stay unchanged.
        super.onNewIntent(intent)
    }
}
