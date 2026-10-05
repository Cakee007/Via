package dev.ujhhgtg.via.tools

import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintCallbacks
import dev.ujhhgtg.via.engine.EnginePage
import java.io.File

/** z8.v1 + a.a: write the EnginePage adapter directly to the selected offline file. */
object PdfExporter {
    fun write(view: EnginePage, file: File, completed: (Boolean) -> Unit) {
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 600, 600))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        val adapter = view.createPrintAdapter("Via Document") ?: run { completed(false); return }
        try {
            PrintCallbacks.layout(adapter, attributes, onFinished = {
                val descriptor = try {
                    file.parentFile?.mkdirs()
                    file.createNewFile()
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE)
                } catch (_: Exception) { completed(false); return@layout }
                fun finish(success: Boolean) {
                    runCatching { descriptor.close() }
                    completed(success && file.isFile && file.length() > 0)
                }
                try {
                    PrintCallbacks.write(adapter, descriptor, ::finish)
                } catch (_: Exception) { finish(false) }
            }, onFailed = { completed(false) })
        } catch (_: Exception) { completed(false) }
    }
}
