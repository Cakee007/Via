package dev.ujhhgtg.via.tools

import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.min

/** c8.yb: the QR page is a small Via dialog with an image and a save row. */
object QrCodeDialog {
    fun show(activity: FragmentActivity, content: String) {
        if (content.isEmpty()) return
        if (content.length > 1024) {
            ViaToast.show(activity, activity.getString(R.string.link_too_long_to_generate_qr_code), ViaToast.LENGTH_LONG,
                activity.getString(R.string.action_copy)) {
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText(null, content))
                ViaToast.show(activity, R.string.toast_copy_url_successful)
            }
            return
        }
        QrCodeDialogFragment.newInstance(content).show(activity.supportFragmentManager, "qrcode")
    }
}

class QrCodeDialogFragment : dev.ujhhgtg.via.ui.dialog.ViaDialogFragment() {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var bitmap: Bitmap? = null
    private lateinit var image: ImageView

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        image = ImageView(context).apply { setPadding(0, context.dp(6f), 0, 0) }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(image, LinearLayout.LayoutParams(-1, qrSize(context)).apply {
                marginStart = context.dp(6f); marginEnd = context.dp(6f)
            })
            addView(TextView(context).apply {
                setText(R.string.action_save)
                setTextColor(dev.ujhhgtg.via.settings.settingsColor(context, R.attr.viaAccentColor, 0xff6f8de1.toInt()))
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.create(dev.ujhhgtg.via.data.BrowserPreferences(context).selectedTypeface(), android.graphics.Typeface.BOLD)
                setPadding(0, context.dp(14f), 0, context.dp(14f))
                setBackgroundResource(R.drawable.flat_ripple)
                setOnClickListener { save() }
            }, LinearLayout.LayoutParams(-1, -2))
        }
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        val content = requireArguments().getString("url").orEmpty()
        if (content.isEmpty()) { dismiss(); return }
        io.execute {
            val result = runCatching { QrBitmaps.generate(content) }
            main.post {
                if (view !== this.view || !isAdded) return@post
                result.onSuccess { bitmap = it; image.setImageBitmap(it) }
                    .onFailure { android.util.Log.w("ViaQr", "QR generation failed", it) }
            }
        }
    }

    override fun onStart() { super.onStart(); dialog?.window?.setLayout(qrSize(requireContext()), ViewGroup.LayoutParams.WRAP_CONTENT) }
    override fun onDestroy() { io.shutdownNow(); main.removeCallbacksAndMessages(null); super.onDestroy() }

    private fun save() {
        val value = bitmap ?: return
        val context = requireContext()
        io.execute {
            val result = runCatching {
                val name = "qrcode-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())}.png"
                val destination = dev.ujhhgtg.via.downloads.DownloadFiles.create(context, Environment.DIRECTORY_DOWNLOADS, name, "image/png")
                val uri = requireNotNull(destination.uri)
                requireNotNull(context.contentResolver.openOutputStream(uri)).use { output ->
                    check(value.compress(Bitmap.CompressFormat.PNG, 100, output))
                    // i1.f/g treats metadata refresh as best-effort after successful compression.
                    runCatching {
                        val values = ContentValues().apply { put("width", value.width); put("height", value.height) }
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                            if (descriptor.statSize >= 0) values.put("_size", descriptor.statSize)
                        }
                        context.contentResolver.update(uri, values, null, null)
                    }
                }
            }
            main.post {
                if (!isAdded) return@post
                ViaToast.show(context, if (result.isSuccess) R.string.save_qr_code_successfully else R.string.toast_operation_failed)
                if (result.isSuccess) dismiss()
            }
        }
    }
    private fun qrSize(context: Context): Int = min(
        min(context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels) * .72f,
        context.dp(420f).toFloat()).toInt()

    companion object {
        fun newInstance(url: String) = QrCodeDialogFragment().apply { arguments = Bundle().apply { putString("url", url) } }
    }
}
