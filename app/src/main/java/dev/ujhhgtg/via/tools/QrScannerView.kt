package dev.ujhhgtg.via.tools

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import dev.ujhhgtg.via.ui.ViaToast
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.journeyapps.barcodescanner.Size
import com.journeyapps.barcodescanner.camera.CenterCropStrategy
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.skins.setSkinImageResource
import dev.ujhhgtg.via.common.WindowInsetsHelper
import dev.ujhhgtg.via.settings.SettingsToolbar
import dev.ujhhgtg.via.tools.scanner.ScannerView
import dev.ujhhgtg.via.ui.dp
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.min

/** fb.q's scanner layout and lifecycle flags, using the original JourneyApps camera/decoder components. */
@SuppressLint("ViewConstructor")
class QrScannerView(context: Context, private val callback: Callback) : FrameLayout(context), AutoCloseable {
    interface Callback {
        fun onResult(value: String)
        fun onRequestCameraPermission()
        fun onPickImage()
        fun onClose() = Unit
    }
    private val scanner = ScannerView(context)
    private val overlay = FrameLayout(context)
    private val torch = ImageView(context)
    private val gallery = ImageView(context)
    private val hint = TextView(context)
    private val imageDecoder = Executors.newSingleThreadExecutor()
    private val hasCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    private val hasFlash = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    private var ready = false
    private var flags = 0
    private var torchOn = false
    private var lastResult: String? = null
    private var closed = false

    init {
        scanner.barcode.previewScalingStrategy = CenterCropStrategy()
        scanner.barcode.isUseTextureView = false
        scanner.barcode.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.CODE_39, BarcodeFormat.QR_CODE))
        scanner.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult) {
                val value = result.text ?: return
                if (value == lastResult) return
                lastResult = value
                scanner.setStatusText(value)
                deliver(value)
            }
        })
        addView(scanner, LayoutParams(-1, -1))
        addView(overlay, LayoutParams(-1, -1))
        val toolbar = SettingsToolbar(context) { callback.onClose() }.apply {
            setTitle(R.string.scan_qr_code)
            setBackgroundColor(Color.TRANSPARENT)
            setContentColor(Color.WHITE)
            setDividerColor(Color.TRANSPARENT)
        }
        overlay.addView(toolbar, LayoutParams(-1, -2))
        hint.setText(R.string.grant_camera_permission_hint)
        hint.setTextColor(0xb2ffffff.toInt())
        hint.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_toolbar_title_size).toFloat())
        hint.setPadding(context.dp(16f), 0, context.dp(16f), 0)
        hint.gravity = Gravity.CENTER
        hint.visibility = GONE
        hint.setOnClickListener {
            if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) hint.visibility = GONE
            else callback.onRequestCameraPermission()
        }
        overlay.addView(hint, LayoutParams(-1, -1).apply { topMargin = context.dp(54f) })
        torch.setSkinImageResource(R.drawable.qr_torch)
        torch.contentDescription = context.getString(R.string.switch_flash)
        gallery.setSkinImageResource(R.drawable.image)
        gallery.contentDescription = context.getString(R.string.scan_qr_code_from_image)
        listOf(torch, gallery).forEach {
            it.setColorFilter(Color.WHITE)
            it.setPadding(context.dp(10f), context.dp(10f), context.dp(10f), context.dp(10f))
            // fb.q uses x7.o.c (the 40dp #20ffffff circle selector), rather
            // than a ripple-only background.  The selector keeps both controls
            // readable over the camera surface and turns the torch circle
            // white while it is selected.
            it.background = ContextCompat.getDrawable(context, R.drawable.scanner_button_background)
            overlay.addView(it, LayoutParams(context.dp(48f), context.dp(48f)))
        }
        torch.setOnClickListener { torchOn = !torchOn; scanner.torch(torchOn); torch.isSelected = torchOn; torch.setColorFilter(if (torchOn) selectedColor() else Color.WHITE) }
        gallery.setOnClickListener { callback.onPickImage() }
        isFocusableInTouchMode = true
        setOnKeyListener { _, key, event -> event.action == KeyEvent.ACTION_DOWN && scanner.onKeyDown(key, event) }
        arrange(resources.configuration)
        WindowInsetsHelper.apply(overlay)
        overlay.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> arrange(resources.configuration) }
    }

    /** fb.q.V1 runs this after the view is created; permission is requested by tapping the hint. */
    fun initialize() {
        requestFocus()
        if (!hasCamera) { setTorchEnabled(false); hint.visibility = VISIBLE; hint.isEnabled = false; hint.setText(R.string.no_camera); return }
        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) permissionGranted()
        else { setTorchEnabled(false); hint.visibility = VISIBLE }
    }
    fun permissionGranted() {
        hint.visibility = GONE
        ready = true
        resumeCamera()
        setTorchEnabled(hasFlash)
    }
    private fun setTorchEnabled(enabled: Boolean) { torch.isEnabled = enabled; torch.alpha = if (enabled) 1f else .5f }
    private fun selectedColor(): Int {
        return context.getColor(R.color.scanner_selected)
    }
    private fun resumeCamera() {
        if (!hasCamera || closed) return
        if (flags and 2 == 0) { scanner.resume(); flags = flags or 2 }
        flags = flags and 1.inv()
    }
    private fun pauseCamera() {
        if (!hasCamera) return
        if (flags and 1 == 0) { scanner.pause(); flags = flags or 1 }
        flags = flags and 2.inv()
    }
    fun pause() {
        if (ready && flags and 2 != 0) { flags = flags or 4; pauseCamera() }
    }
    fun resume() {
        if (ready && flags and 4 != 0 && flags and 1 != 0) { flags = flags and 4.inv(); resumeCamera() }
    }
    override fun close() { if (!closed) { pauseCamera(); closed = true; imageDecoder.shutdown() } }
    override fun onConfigurationChanged(configuration: Configuration) { super.onConfigurationChanged(configuration); arrange(configuration) }
    private fun arrange(configuration: Configuration) {
        val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val side = min((min(context.dp(configuration.screenWidthDp.toFloat()), context.dp(configuration.screenHeightDp.toFloat())) * if (landscape) .6 else .75).toInt(), context.dp(320f))
        scanner.barcode.framingRectSize = Size(side, side)
        val first = torch.layoutParams as LayoutParams
        val second = gallery.layoutParams as LayoutParams
        val spacing = side / 2 - context.dp(48f) / 2
        var edge = context.dp(32f)
        if (landscape) {
            var offset = 0
            if (Build.VERSION.SDK_INT >= 30 && rootWindowInsets?.isVisible(WindowInsets.Type.statusBars()) != false) {
                rootWindowInsets?.getInsets(WindowInsets.Type.systemBars())?.let { edge += it.right; offset = abs(it.top - it.bottom) shr 1 }
            }
            first.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            second.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            first.setMargins(0, 0, edge, spacing + offset)
            second.setMargins(0, spacing - offset, edge, 0)
        } else {
            first.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            second.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            first.setMargins(0, 0, spacing, edge)
            second.setMargins(spacing, 0, 0, edge)
        }
        torch.layoutParams = first
        gallery.layoutParams = second
    }
    private fun deliver(value: String) {
        if (closed || value.isEmpty()) return
        scanner.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
        callback.onResult(value)
    }
    fun decodeImage(data: Intent) {
        val uri = data.data ?: return
        if (closed) return
        imageDecoder.execute {
            val value = runCatching { context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)?.let { bitmap -> try { QrBitmaps.decode(bitmap) } finally { bitmap.recycle() } } }.getOrNull()
            post {
                if (!closed) {
                    if (value.isNullOrEmpty()) ViaToast.makeText(context, R.string.qr_code_not_detected, ViaToast.LENGTH_LONG).show()
                    else { lastResult = value; deliver(value) }
                }
            }
        }
    }
}
