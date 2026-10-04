package dev.ujhhgtg.via.common

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

/**
 * com.tuyafeng.support.crash.a + BrowserApp.g/b/c: [DERIVED FROM ORIGINAL] background threads' uncaught exceptions are
 * reported with the original's diagnostic toast without ending the process, while main-thread
 * failures are handed to the system handler and crash normally.
 */
object CrashGuard {
    private var installed = false

    @Synchronized
    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        val system = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (thread == Looper.getMainLooper().thread) system?.uncaughtException(thread, error)
            else report(app, main, thread, error)
        }
    }

    /** BrowserApp.c posts BrowserApp.b to the main thread. */
    private fun report(context: Context, main: Handler, thread: Thread, error: Throwable) {
        main.post {
            runCatching {
                val text = "Exception Happened\n$thread\n$error"
                Log.e("ViaCrash", "Uncaught exception on $thread", error)
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
