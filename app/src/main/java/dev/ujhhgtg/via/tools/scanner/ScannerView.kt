package dev.ujhhgtg.via.tools.scanner

import android.content.Context
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.TextView
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.BarcodeView
import dev.ujhhgtg.via.R

/** t5.k: the original three-child scanner container, backed by the original library components. */
class ScannerView(context: Context) : FrameLayout(context) {
    val barcode: BarcodeView
    val finder: MyViewfinderView
    private val status: TextView
    init {
        inflate(context, R.layout.scanner_surface, this)
        barcode = findViewById(R.id.scanner_barcode)
        finder = findViewById(R.id.scanner_viewfinder)
        status = findViewById(R.id.scanner_status)
        finder.setCameraPreview(barcode)
    }
    fun decodeContinuous(callback: BarcodeCallback) = barcode.decodeContinuous(object : BarcodeCallback {
        override fun barcodeResult(result: BarcodeResult) = callback.barcodeResult(result)
        override fun possibleResultPoints(points: List<ResultPoint>) {
            points.forEach(finder::addPossibleResultPoint)
            callback.possibleResultPoints(points)
        }
    })
    fun pause() = barcode.pause()
    fun resume() = barcode.resume()
    fun torch(enabled: Boolean) = barcode.setTorch(enabled)
    fun setStatusText(value: String) { status.text = value }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> { torch(true); true }
        KeyEvent.KEYCODE_VOLUME_DOWN -> { torch(false); true }
        KeyEvent.KEYCODE_CAMERA, KeyEvent.KEYCODE_FOCUS -> true
        else -> super.onKeyDown(keyCode, event)
    }
}
