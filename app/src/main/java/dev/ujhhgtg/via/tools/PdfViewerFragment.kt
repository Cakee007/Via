package dev.ujhhgtg.via.tools

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.fragment.app.Fragment

/** za.g: PDF path/name are arguments; renderer and counter callbacks live only with this view. */
class PdfViewerFragment : Fragment() {
    private var screen: PdfViewerView? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        PdfViewerView(requireContext()) { parentFragmentManager.popBackStack() }.also { screen = it }
    override fun onViewCreated(view: View, state: Bundle?) {
        arguments?.getString("pdfPath")?.let { screen?.open(it.toUri(), arguments?.getString("pdfName")) }
    }
    override fun onPause() { screen?.onHostPause(); super.onPause() }
    override fun onHiddenChanged(hidden: Boolean) { super.onHiddenChanged(hidden); if (hidden) screen?.onHostPause() }
    override fun onDestroyView() { screen?.viewer?.setOnPageChangedListener(null); screen?.viewer?.onInteraction = null; screen?.close(); screen = null; super.onDestroyView() }

    companion object {
        fun newInstance(uri: Uri, name: String? = null) = PdfViewerFragment().apply {
            arguments = Bundle().apply { putString("pdfPath", uri.toString()); putString("pdfName", name) }
        }
    }
}
