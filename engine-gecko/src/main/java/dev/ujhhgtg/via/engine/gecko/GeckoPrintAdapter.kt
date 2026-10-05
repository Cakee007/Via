package dev.ujhhgtg.via.engine.gecko

import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import org.mozilla.geckoview.GeckoResult
import java.io.FileOutputStream
import java.io.InputStream

/** Prints the PDF Gecko renders for the page; the PDF is produced asynchronously, so writing waits for it. */
internal class GeckoPrintAdapter(private val title: String, private val pdf: GeckoResult<InputStream>) : PrintDocumentAdapter() {
    private val main = Handler(Looper.getMainLooper())

    override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback, extras: Bundle?) {
        if (cancellationSignal?.isCanceled == true) { callback.onLayoutCancelled(); return }
        val info = PrintDocumentInfo.Builder(title.ifEmpty { "page" } + ".pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()
        callback.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback) {
        pdf.accept({ stream ->
            if (stream == null) { callback.onWriteFailed(null); return@accept }
            Thread {
                val written = runCatching {
                    stream.use { input -> FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) } }
                }
                main.post {
                    if (cancellationSignal?.isCanceled == true) callback.onWriteCancelled()
                    else written.fold({ callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) }, { callback.onWriteFailed(it.message) })
                }
            }.start()
        }, { error -> callback.onWriteFailed(error?.message) })
    }
}
