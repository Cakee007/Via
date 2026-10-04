package dev.ujhhgtg.via.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import dev.ujhhgtg.via.common.LocalNetworkAccess
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.SettingsPageFragment

/** Shell applies a theme without replacing its browser, retaining the original nightmode2 states. */
open class ViaActivity : FragmentActivity() {
    private var displayedNight = false
    private var displayedLanguage: String? = null
    private var localNetworkPermissionResult: ((Boolean) -> Unit)? = null
    var localNetworkPermissionDeclined = false
        private set
    fun allowLocalNetworkPermissionRetry() { localNetworkPermissionDeclined = false }
    private val localNetworkPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        localNetworkPermissionDeclined = !granted
        val completed = localNetworkPermissionResult
        localNetworkPermissionResult = null
        completed?.invoke(granted)
    }

    /** Android 17 adaptation: only an active user-facing operation may show this prompt. */
    fun requestLocalNetworkPermission(completed: (Boolean) -> Unit) {
        if (LocalNetworkAccess.hasPermission(this)) { completed(true); return }
        if (isFinishing || isDestroyed || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            localNetworkPermissionResult != null) { completed(false); return }
        localNetworkPermissionResult = completed
        localNetworkPermission.launch(android.Manifest.permission.ACCESS_LOCAL_NETWORK)
    }
    override fun attachBaseContext(newBase: Context) {
        val preferences = BrowserPreferences(newBase)
        // Original Shell.attachBaseContext only supplies a locale override. A forced
        // uiMode would hide the real system state used by w9.k.R0/m0.
        super.attachBaseContext(preferences.localizedContext(newBase))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        val preferences = BrowserPreferences(this)
        val customScheme = if (intent.getBooleanExtra("CUSTOM_TAB", false)) intent.getIntExtra("CUSTOM_TAB_COLOR_SCHEME", 0) else 0
        if (customScheme == 0) preferences.updateSystemNightMode(systemNight(resources.configuration))
        displayedLanguage = preferences.language
        applyNightTheme(if (customScheme == 0) preferences.isNightMode else customScheme == 2)
        super.onCreate(savedInstanceState)
    }
    override fun onResume() {
        super.onResume()
        val preferences = BrowserPreferences(this)
        if (preferences.language != displayedLanguage) recreate()
    }

    /** c8.s6.sb updates the live Activity theme; it never changes Android's system uiMode. */
    fun applyNightTheme(night: Boolean) {
        displayedNight = night
        setTheme(if (night) R.style.Theme_Via_Dark else R.style.Theme_Via)
    }

    protected open fun onNightThemeChanged(night: Boolean) {
        supportFragmentManager.fragments.filterIsInstance<SettingsPageFragment>().forEach { it.refreshForTheme() }
    }

    /** mark.via.Shell.onConfigurationChanged + o8.a.b0: rebind settings after a system theme change. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (intent.getBooleanExtra("CUSTOM_TAB", false)) return
        val preferences = BrowserPreferences(this)
        if (!preferences.updateSystemNightMode(systemNight(newConfig))) return
        applyNightTheme(preferences.isNightMode)
        onNightThemeChanged(displayedNight)
    }

    private fun systemNight(configuration: Configuration) =
        configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
}
