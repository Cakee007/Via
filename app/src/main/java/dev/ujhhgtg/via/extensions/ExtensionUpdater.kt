package dev.ujhhgtg.via.extensions

import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.engine.ExtensionManager

/** The startup pass for extensions, alongside filter subscriptions and userscripts. */
object ExtensionUpdater {
    internal const val LAST_CHECK = "updated_extensions"

    internal fun isDue(interval: Long, lastCheck: Long, now: Long): Boolean = interval > 0 && lastCheck < now - interval

    /** Main thread. Returns how many extensions were updated. */
    suspend fun updateDue(manager: ExtensionManager, preferences: BrowserPreferences, now: Long = System.currentTimeMillis()): Int {
        if (!preferences.extensionsEnabled) return 0
        if (!isDue(preferences.extensionUpdateInterval, preferences.getLong(LAST_CHECK, 0L), now)) return 0
        preferences.putLong(LAST_CHECK, now)
        return manager.list().filter { it.userEnabled }.count { runCatching { manager.update(it.id) != null }.getOrDefault(false) }
    }
}
