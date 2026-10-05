package dev.ujhhgtg.via.engine

import android.app.Application
import android.content.Context

/**
 * Process-wide browser engine. Everything the app needs from the engine outside a page goes through
 * here, so a different backend (GeckoView) can be swapped in by build flavor.
 */
interface BrowserBackend {
    /** Stable identifier, written into persisted tab state so another backend can skip it. */
    val id: String

    val capabilities: Capabilities

    /** Major engine version, used for feature gating such as cosmetic `:has()` filters. */
    val engineMajorVersion: Int

    /** Whether the engine can run at all on this device. */
    fun isAvailable(): Boolean

    /** Label/value rows for the debugging info screen. */
    fun versionInfo(): List<Pair<String, String>>

    /** Called first in Application.onCreate of every process. */
    fun onProcessStart(app: Application, processName: String?, isMainProcess: Boolean)

    fun defaultUserAgent(context: Context): String

    /** A new page reporting to [events]. [context] should be the themed host context, for popups and pickers. */
    fun createPage(context: Context, events: PageEvents): EnginePage

    val cookies: CookieAccess

    fun clearCache(context: Context)
    fun clearFormData(context: Context)
    fun clearStorage(context: Context)
    /** May block; call off the main thread. */
    fun clearCookies(context: Context)
    fun clearLocationPermissions()
}

/** Suspending, since some engines (GeckoView) only expose cookies asynchronously. Callable from any thread. */
interface CookieAccess {
    /** Cookie header value for [url], or null. */
    suspend fun get(url: String): String?
    fun flush()
    /** Removes every cookie that would be sent to [url]'s host and its parent domains. */
    suspend fun clearForSite(context: Context, url: String): Boolean
}
