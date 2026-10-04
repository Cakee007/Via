package dev.ujhhgtg.via.common

import dev.ujhhgtg.via.data.BrowserPreferences

/** w9.n/b: pending document work, restored by w9.k.c3 and flushed by c8.ua.p1.
 * Importers mark the affected document; its writer clears that bit after regeneration.
 * This is shared by all browser tabs, not a preference-change listener or a DB schema field. */
object GeneratedDocumentState {
    const val HOME_CONTENT = 1
    const val HOME_STYLE = 4
    const val BOOKMARKS = 8
    const val HISTORY = 16
    const val ABOUT = 32
    const val CATALOG = 64
    const val PAGE_SETTINGS = 128
    // The original aliases n.g/r (blank.html) and n.m/x (s6.R7's current-page update).
    const val BLANK_AND_PAGE_STATE = 256
    const val ALL_DOCUMENTS = HOME_CONTENT or HOME_STYLE or BOOKMARKS or HISTORY or ABOUT or CATALOG or BLANK_AND_PAGE_STATE
    const val SETTINGS_IMPORT = ALL_DOCUMENTS or PAGE_SETTINGS

    private var initialized = false
    var flags: Int = 0
        private set

    /** w9.k.c3 reads datachecker2 when the resident preference object is initialized. */
    fun initialize(preferences: BrowserPreferences) {
        if (initialized) return
        flags = preferences.dataChecker
        initialized = true
    }

    fun has(mask: Int): Boolean = flags and mask == mask
    fun hasAny(mask: Int): Boolean = flags and mask != 0
    fun mark(mask: Int) { flags = flags or mask }
    fun clear(mask: Int) { flags = flags and mask.inv() }

    /** s6.M1/G1(true) -> ua.p1 -> w9.k.k2: retain unconsumed work across restarts. */
    fun flush(preferences: BrowserPreferences) { preferences.dataChecker = flags }
}
