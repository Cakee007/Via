package android.print

import android.os.CancellationSignal
import android.os.ParcelFileDescriptor

/**
 * The SDK's LayoutResultCallback and WriteResultCallback constructors have package visibility,
 * so subclassing them has to happen inside this package.  Everything else lives in the app.
 */
object PrintCallbacks {
    fun layout(adapter: PrintDocumentAdapter, attributes: PrintAttributes, onFinished: () -> Unit, onFailed: () -> Unit) {
        adapter.onLayout(null, attributes, null, object : PrintDocumentAdapter.LayoutResultCallback() {
            override fun onLayoutFinished(info: PrintDocumentInfo, changed: Boolean) = onFinished()
            override fun onLayoutFailed(error: CharSequence?) = onFailed()
            override fun onLayoutCancelled() = onFailed()
        }, null)
    }

    fun write(adapter: PrintDocumentAdapter, descriptor: ParcelFileDescriptor, onResult: (Boolean) -> Unit) {
        adapter.onWrite(arrayOf(PageRange.ALL_PAGES), descriptor, CancellationSignal(), object : PrintDocumentAdapter.WriteResultCallback() {
            override fun onWriteFinished(pages: Array<out PageRange>) = onResult(true)
            override fun onWriteFailed(error: CharSequence?) = onResult(false)
            override fun onWriteCancelled() = onResult(false)
        })
    }
}
