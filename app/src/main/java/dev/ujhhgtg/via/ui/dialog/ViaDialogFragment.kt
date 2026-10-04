package dev.ujhhgtg.via.ui.dialog

import android.os.Bundle
import android.view.Gravity
import android.view.View
import androidx.fragment.app.DialogFragment
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.common.WindowInsetsHelper
import dev.ujhhgtg.via.ui.dp

/** k8.a: the shared base of the original custom-layout dialog fragments. */
open class ViaDialogFragment : DialogFragment() {
    protected open val blurMode: Int = 1

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setStyle(STYLE_NO_TITLE, R.style.Via_Dialog)
    }

    override fun onStart() {
        super.onStart()
        val activity = requireActivity()
        dialog?.window?.let { window ->
            val content = activity.findViewById<View>(android.R.id.content)
            val available = minOf(content.width, content.height)
            window.attributes = window.attributes.apply {
                width = minOf(activity.dp(384f), available - activity.dp(72f))
                gravity = Gravity.CENTER
            }
            if (WindowInsetsHelper.isFullscreen(activity.window)) WindowInsetsHelper.setFullscreen(window, true)
            DialogWindowBlur.apply(activity, window, blurMode)
        }
    }
}
