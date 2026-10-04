package dev.ujhhgtg.via.tools

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.ui.BrowserOverlayFragment

/** f8.q: non-modal marker pane in the browser overlay container. */
class AdMarkerFragment : BrowserOverlayFragment() {
    interface Host { fun adMarkerController(): AdMarker }
    private val controller get() = (parentFragment as? Host)?.adMarkerController()
    private lateinit var selection: TextView
    override val scrimEnabled = false
    override fun paneTitle() = getString(R.string.action_mark)
    override fun createContent(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?) = inflater.inflate(R.layout.ad_marker, container, false)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selection = content.findViewById(R.id.marker_selection)
        val move = content.findViewById<View>(R.id.marker_move)
        move.rotation = if (gravity and Gravity.BOTTOM == Gravity.BOTTOM) 0f else 180f
        move.setOnClickListener {
            val destination = if (gravity and Gravity.TOP == Gravity.TOP) (gravity and Gravity.TOP.inv()) or Gravity.BOTTOM
                else (gravity and Gravity.BOTTOM.inv()) or Gravity.TOP
            moveTo(destination)
            move.animate().rotation(if (destination and Gravity.BOTTOM == Gravity.BOTTOM) 0f else 180f).setDuration(240L).start()
        }
        content.findViewById<View>(R.id.marker_expand).setOnClickListener { controller?.expand() }
        content.findViewById<View>(R.id.marker_shrink).setOnClickListener { controller?.shrink() }
        content.findViewById<View>(R.id.marker_confirm).setOnClickListener { controller?.requestRule() }
        content.findViewById<View>(R.id.marker_cancel).setOnClickListener { controller?.close() }
        selection.setOnLongClickListener { controller?.copySelection(); true }
        controller?.bindPanel(this)
    }
    override fun focusContent() {
        content.findViewById<View>(R.id.marker_move).apply { requestFocus(); performAccessibilityAction(64, null) }
    }
    fun updateSelection(value: String?) { if (::selection.isInitialized) selection.text = value?.takeIf(String::isNotEmpty) }
    override fun onDestroyView() { controller?.panelDestroyed(this); super.onDestroyView() }
    companion object {
        const val TAG = "ad_marker"
        fun newInstance(width: Int, gravity: Int) = AdMarkerFragment().apply {
            arguments = Bundle().apply { putInt("width", width); putInt("gravity", gravity) }
        }
    }
}
