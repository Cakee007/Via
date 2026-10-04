package dev.ujhhgtg.via.common

/**
 * The intent actions, markers and extras that make up this application's launch contract.
 * `res/xml/browser_shortcuts.xml` declares the same action strings and must be kept in sync.
 */
object ViaIntents {
    const val PACKAGE = "dev.ujhhgtg.via"
    const val PREFIX = "$PACKAGE."

    // Shell launch actions (c8.ua.l / z8.b0.A).
    const val ACTION_BOOKMARK = "${PREFIX}BOOKMARK"
    const val ACTION_HISTORY = "${PREFIX}HISTORY"
    const val ACTION_SCAN = "${PREFIX}SCAN"
    const val ACTION_DOWNLOADER = "${PREFIX}DOWNLOADER"
    const val ACTION_READ_ALOUD = "${PREFIX}READ_ALOUD"
    const val ACTION_SEARCH = "${PREFIX}SEARCH"
    const val ACTION_TRANSLATE = "${PREFIX}TRANSLATE"

    // Picture-in-picture remote actions.
    const val ACTION_MEDIA_REWIND = "${PREFIX}MEDIA_REWIND"
    const val ACTION_MEDIA_PLAY = "${PREFIX}MEDIA_PLAY"
    const val ACTION_MEDIA_FASTFORWARD = "${PREFIX}MEDIA_FASTFORWARD"

    /** The value of [EXTRA_INTENT] which marks an intent as a browser request. */
    const val MARKER_BROWSER = "${PREFIX}BROWSER"

    const val EXTRA_INTENT = "intent"
    const val EXTRA_OPEN = "OPEN"
    const val EXTRA_QUERY = "query"
    /** c8.ua.Q0's referrer extra, in this application's namespace. */
    const val EXTRA_REFERER = "${PREFIX}REFERER"
}
