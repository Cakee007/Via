package dev.ujhhgtg.via.tools

import android.app.Activity
import android.os.SystemClock
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.R

/** c8.s6.Na/w9: a transient mode; the activity owns fullscreen, chrome and gesture layout. */
class GameModeController(private val activity: Activity, private val onChanged: (Boolean) -> Unit) {
    var enabled: Boolean = false
        private set
    /** w9.k.q is process-resident, distinct from s6.k1's current browser layout state. */
    val menuEnabled: Boolean get() = requestedMode
    private var lastBack = 0L

    fun toggle() = setEnabled(!requestedMode)
    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        requestedMode = value
        onChanged(value)
        ViaToast.makeText(activity, if (value) activity.getString(R.string.game_mode_is_on)
            else activity.getString(R.string.is_off, activity.getString(R.string.game_mode)), ViaToast.LENGTH_SHORT).show()
    }

    /** Invoke after dismissing video/custom views and before normal tab/page back navigation. */
    fun onBackPressed(): Boolean {
        if (!enabled) return false
        val now = SystemClock.elapsedRealtime()
        if (now - lastBack > 1500L) {
            ViaToast.makeText(activity, R.string.game_mode_exit, ViaToast.LENGTH_SHORT).show()
            lastBack = now
        } else {
            lastBack = 0L
            setEnabled(false)
        }
        return true
    }

    companion object { private var requestedMode = false }
}
