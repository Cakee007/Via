package dev.ujhhgtg.via.data

import android.content.Context
import androidx.core.content.edit

/** Compatibility cleanup after the user-requested removal of the built-in AI assistant. */
internal object RemovedFeatureMigration {
    private val shortcuts = setOf("keyback", "keyforward", "keyhome", "keytab", "keymenu", "gesturetoolbarleft", "gesturetoolbarright")
    private val menus = setOf("displayedmenus", "hiddenmenus")

    /** Action 29 was the assistant; the original action 0 means no action. Other IDs retain their meaning. */
    fun normalizeInt(key: String, value: Int): Int = if (key in shortcuts && value == 29) 0 else value

    /** Browser menu ID 39 is retired; context-menu ID 39 and the remaining menu order are unaffected. */
    fun normalizeString(key: String, value: String?): String? = if (key in menus && value != null)
        value.split(',').filterNot { it.trim() == "39" }.joinToString(",") else value

    fun cleanProfile(context: Context) {
        // This dedicated database contains only assistant providers, tokens, messages, threads and prompts.
        if (context.getDatabasePath("copilot").exists()) context.deleteDatabase("copilot")
        val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        preferences.edit {
            remove("aibackend")
            remove("aimodel")
            shortcuts.filter(preferences::contains).forEach { key ->
                val before = preferences.getInt(key, 0)
                val after = normalizeInt(key, before)
                if (after != before) putInt(key, after)
            }
            menus.filter(preferences::contains).forEach { key ->
                val before = preferences.getString(key, null)
                val after = normalizeString(key, before)
                if (after != before) putString(key, after)
            }
        }
    }
}
