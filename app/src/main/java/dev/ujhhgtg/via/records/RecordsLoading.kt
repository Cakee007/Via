package dev.ujhhgtg.via.records

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The record pages' data flow mirrors the source's Rx chain
 * (o.g(...).l(g7.a.c()).j(w6.b.b()).m(u8.b.a(b1()))): queries run on a shared
 * IO executor, results land on the main thread, and a stale token is dropped
 * so a superseded render never touches the view.
 */
internal object RecordsLoading {
    private val io: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "records-io") }
    private val main = Handler(Looper.getMainLooper())

    fun <T> load(token: Long, compute: () -> T, consume: (T) -> Unit) {
        io.execute {
            val value = runCatching(compute).getOrNull() ?: return@execute
            main.post { consume(value) }
        }
    }
}
