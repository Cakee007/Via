package dev.ujhhgtg.via

import android.app.Application

class ViaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // BrowserApp.onCreate -> g(): installed in every process, before the WebView profile setup.
        dev.ujhhgtg.via.common.CrashGuard.install(this)
        val process = getProcessName()
        val main = process.isNullOrEmpty() || process == packageName
        dev.ujhhgtg.via.engine.Engines.backend.onProcessStart(this, process, main)
        if (main) {
            dev.ujhhgtg.via.data.RemovedFeatureMigration.cleanProfile(this)
            // pa.c.a -> z8.j0.c: installed expansion replacement follows the saved app flag.
            dev.ujhhgtg.via.browser.script.BuiltinExpandScript.setEnabled(this,
                dev.ujhhgtg.via.data.BrowserPreferences(this).appFlags and 1024 != 0)
        }
    }
}
