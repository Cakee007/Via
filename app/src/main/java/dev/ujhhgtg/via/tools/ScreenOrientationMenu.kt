package dev.ujhhgtg.via.tools

import android.app.Activity
import android.os.Build
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog

/** s6.C9(30)/n6: the browser chooser additionally reports a changed selection. */
object ScreenOrientationMenu {
    fun show(activity: Activity) {
        val preferences = BrowserPreferences(activity)
        val selected = preferences.screenOrientation - 1
        val labels = arrayOf(R.string.follow_system, R.string.orientation_auto, R.string.orientation_portrait, R.string.orientation_landscape)
            .map(activity::getString).toTypedArray()
        ViaDialog(activity).title(R.string.orientation).singleChoice(labels, selected) { index ->
            if (index != selected) {
                preferences.screenOrientation = index + 1
                val orientation = preferences.resolvedScreenOrientation()
                if ((Build.VERSION.SDK_INT < 36 || activity.resources.configuration.smallestScreenWidthDp < 600) &&
                    activity.requestedOrientation != orientation) activity.requestedOrientation = orientation
                ViaToast.makeText(activity, activity.getString(R.string.key_is_set_as_value,
                    activity.getString(R.string.orientation), labels[index]), ViaToast.LENGTH_SHORT).show()
            }
        }.show()
    }
}
