package dev.ujhhgtg.via

import android.app.Application
import android.webkit.WebView

class ViaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // BrowserApp.onCreate -> g(): installed in every process, before the WebView profile setup.
        dev.ujhhgtg.via.common.CrashGuard.install(this)
        val process = getProcessName()
        // BrowserApp.k leaves the main process on the normal app_webview profile.
        if (!process.isNullOrEmpty() && process != packageName) WebView.setDataDirectorySuffix(process)
        else {
            dev.ujhhgtg.via.data.RemovedFeatureMigration.cleanProfile(this)
            // pa.c.a -> z8.j0.c: installed expansion replacement follows the saved app flag.
            dev.ujhhgtg.via.browser.script.BuiltinExpandScript.setEnabled(this,
                dev.ujhhgtg.via.data.BrowserPreferences(this).appFlags and 1024 != 0)
            // Preserve the profile written by the earlier reconstruction's unconditional suffix.
            val previous = java.io.File(applicationInfo.dataDir, "app_webview_$packageName")
            val current = java.io.File(applicationInfo.dataDir, "app_webview")
            if (previous.isDirectory && !current.exists()) runCatching { previous.copyRecursively(current) }
        }
    }
}
