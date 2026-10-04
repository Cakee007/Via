package dev.ujhhgtg.via.tools

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.common.WindowInsetsHelper
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.SwipeBackLayout
import dev.ujhhgtg.via.ui.dp
import dev.ujhhgtg.via.ui.dialog.ViaDialog

/** fb.q: the camera belongs to the visible scanner fragment, including permission/gallery returns. */
class QrScannerFragment : Fragment() {
    private var scanner: QrScannerView? = null
    private var bars: IntArray? = null
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scanner?.permissionGranted()
        else if (isAdded) {
            // fb.q.S2 -> z8.z1.f uses an actionable ViaToast, not a permission dialog.
            dev.ujhhgtg.via.ui.ViaToast.show(requireContext(), R.string.check_permission, actionText = android.R.string.ok) {
                    val settings = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData("package:${requireContext().packageName}".toUri())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    runCatching { startActivity(settings) }
                }
        }
    }
    private val gallery = registerForActivityResult(ScannerImageContract()) { uri ->
        if (uri != null) scanner?.decodeImage(Intent().setData(uri))
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val scannerView = QrScannerView(requireContext(), object : QrScannerView.Callback {
            override fun onResult(value: String) {
                parentFragmentManager.setFragmentResult("qr_scan", Bundle().apply { putString("value", value) })
                parentFragmentManager.popBackStack()
            }
            override fun onRequestCameraPermission() { cameraPermission.launch(Manifest.permission.CAMERA) }
            override fun onPickImage() { try { gallery.launch(Unit) } catch (error: android.content.ActivityNotFoundException) { android.util.Log.e("QrScannerFragment", "Image picker unavailable", error) } }
            override fun onClose() { parentFragmentManager.popBackStack() }
        }).also { scanner = it }
        // fb.q is hosted in the same edge swipe container as Via's other
        // full-screen pages.  This is required for framework predictive-back:
        // the scanner surface translates with the page while the camera keeps
        // running until the gesture is committed.
        return SwipeBackLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            attach(this@QrScannerFragment, scannerView)
            setEdgeSize(-2)
            setScrollThresholdSize(requireContext().dp(200f).toFloat())
        }
    }
    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        view.isFocusableInTouchMode = true
        view.requestFocus()
        scanner?.initialize()
        if (predictiveBackSupported()) {
            val swipe = view as SwipeBackLayout
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
                private var predictive = false

                override fun handleOnBackStarted(backEvent: BackEventCompat) {
                    if (!predictive && !BrowserPreferences(requireContext()).disablePredictiveBack && viewLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                        predictive = swipe.startPredictiveBack()
                    }
                }

                override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                    if (predictive) swipe.updatePredictiveBack(backEvent.progress)
                }

                override fun handleOnBackCancelled() {
                    if (predictive) {
                        swipe.cancelPredictiveBack()
                        predictive = false
                    }
                }

                override fun handleOnBackPressed() {
                    if (predictive) {
                        predictive = false
                        swipe.finishPredictiveBack()
                    } else if (!parentFragmentManager.isStateSaved) {
                        parentFragmentManager.popBackStack()
                    }
                }
            })
        }
    }

    private fun predictiveBackSupported(): Boolean = Build.VERSION.SDK_INT >= 35 &&
        listOf(Build.BRAND, Build.MANUFACTURER).none { it.equals("HONOR", true) || it.equals("HUAWEI", true) }

    @Suppress("DEPRECATION")
    private fun resumeScanner() {
        val window = requireActivity().window
        if (bars == null) bars = WindowInsetsHelper.saveBarAppearance(requireContext())
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.navigationBarDividerColor = Color.BLACK
        WindowInsetsHelper.setLightStatusBar(requireContext(), false)
        WindowInsetsHelper.setLightNavigationBar(requireContext(), false)
        scanner?.resume()
    }
    @Suppress("DEPRECATION")
    private fun pauseScanner() {
        scanner?.pause()
        context?.let { WindowInsetsHelper.restoreBarAppearance(it, bars) }
        bars = null
    }
    override fun onResume() { super.onResume(); if (!isHidden) resumeScanner() }
    override fun onPause() { pauseScanner(); super.onPause() }
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) pauseScanner() else if (isResumed) resumeScanner()
    }
    override fun onDestroyView() { scanner?.close(); scanner = null; super.onDestroyView() }
}
