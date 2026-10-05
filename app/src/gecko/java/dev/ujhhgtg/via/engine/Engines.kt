package dev.ujhhgtg.via.engine

import dev.ujhhgtg.via.engine.gecko.GeckoBackend

/** The backend compiled into this flavor. Each engine flavor provides its own copy of this file. */
object Engines {
    val backend: BrowserBackend get() = GeckoBackend
}
