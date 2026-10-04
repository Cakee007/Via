package dev.ujhhgtg.via.sync

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.common.LocalNetworkAccess
import dev.ujhhgtg.via.R
import java.util.concurrent.Executors

/** pa.r/pa.c.k's resident pb.a, shared by nb.n and c8.ua.Y1. */
object WebDavSyncRuntime {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var synchronizer: WebDavSync? = null

    /** Called from the browser's normal onResume path (c8.s6.R1). */
    fun onBrowserResumed(context: Context) {
        val configuration = SyncConfigurationStore(context).read() ?: return
        if (!configuration.autoSync || !configuration.isConfigured) return
        run(context, WebDavSync.Operation.SYNC, forced = false) { result ->
            result.onSuccess { errors -> errors.forEach { Log.d("ViaSync", it.description) } }
                .onFailure { Log.d("ViaSync", "Cannot sync data", it) }
        }
    }

    fun lastSynced(context: Context): Long = instance(context).lastSynced()

    /** All entry points are invoked on the main thread; the original worker work stays asynchronous. */
    fun run(context: Context, operation: WebDavSync.Operation, forced: Boolean = true,
        completed: (Result<List<WebDavSync.Failure>>) -> Unit) {
        val configuration = SyncConfigurationStore(context).read()
        if (configuration == null || !configuration.isConfigured || configuration.sections == 0) {
            runPermitted(context, operation, forced, completed)
            return
        }
        LocalNetworkAccess.check(context, configuration.baseUrl, resolveHost = forced, allowPrompt = forced) { allowed ->
            if (allowed) runPermitted(context, operation, forced, completed)
            else completed(Result.success(listOf(WebDavSync.Failure("", 0, context.getString(R.string.title_permission_denied)))))
        }
    }

    private fun runPermitted(context: Context, operation: WebDavSync.Operation, forced: Boolean,
        completed: (Result<List<WebDavSync.Failure>>) -> Unit) {
        val current = instance(context)
        worker.execute {
            val result = runCatching { current.run(operation, forced) }
            main.post { completed(result) }
        }
    }

    private fun instance(context: Context): WebDavSync = synchronizer ?: WebDavSync(
        context.applicationContext, BrowserDatabase.shared(context)
    ).also { synchronizer = it }
}
