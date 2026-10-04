package dev.ujhhgtg.via.home

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import dev.ujhhgtg.via.common.GeneratedDocumentState
import dev.ujhhgtg.via.common.WindowInsetsHelper
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.SwipeBackLayout
import dev.ujhhgtg.via.ui.dp

/** d9.q: pause/resume the preview with the fragment and destroy it with its view. */
class HomeCustomizationFragment : Fragment() {
    private var screen: HomeCustomizationView? = null
    private var pendingRequest = 7300
    private var back: OnBackPressedCallback? = null
    private var barAppearance: IntArray? = null
    private var viewState: Bundle? = null
    private var appliedNight = false
    private val document = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        screen?.onActivityResult(pendingRequest, result.resultCode, result.data)
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        GeneratedDocumentState.initialize(BrowserPreferences(requireContext()))
        GeneratedDocumentState.mark(GeneratedDocumentState.HOME_CONTENT or GeneratedDocumentState.HOME_STYLE)
        pendingRequest = state?.getInt("pendingRequest", 7300) ?: 7300
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        barAppearance = WindowInsetsHelper.saveBarAppearance(requireContext())
        appliedNight = night()
        val content = HomeCustomizationView(requireActivity(), { if (!parentFragmentManager.isStateSaved) parentFragmentManager.popBackStack() }, {
            parentFragmentManager.setFragmentResult("home_customization_changed", Bundle())
        }, { intent, request -> pendingRequest = request; document.launch(intent) }, this).also {
            screen = it
            it.restoreState(state ?: viewState)
        }
        return SwipeBackLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            attach(this@HomeCustomizationFragment, content)
            setEdgeSize(-2)
            setScrollThresholdSize(context.dp(200f).toFloat())
        }
    }
    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        back = object : OnBackPressedCallback(!isHidden) {
            private var predictive = false
            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                if (Build.VERSION.SDK_INT >= 35 && !BrowserPreferences(requireContext()).disablePredictiveBack &&
                    listOf(Build.BRAND, Build.MANUFACTURER).none { it.equals("HONOR", true) || it.equals("HUAWEI", true) }) {
                    predictive = (view as SwipeBackLayout).startPredictiveBack()
                }
            }
            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                if (predictive) (view as SwipeBackLayout).updatePredictiveBack(backEvent.progress)
            }
            override fun handleOnBackCancelled() {
                if (predictive) { (view as SwipeBackLayout).cancelPredictiveBack(); predictive = false }
            }
            override fun handleOnBackPressed() {
                if (predictive) { predictive = false; (view as SwipeBackLayout).finishPredictiveBack() }
                else screen?.onBack()
            }
        }.also { requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it) }
    }
    override fun onResume() {
        super.onResume()
        view?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        if (!refreshTheme() && !isHidden) screen?.onHostResume()
    }
    override fun onPause() {
        view?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        screen?.onHostPause(); super.onPause()
    }
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        back?.isEnabled = !hidden
        if (hidden) screen?.onHostPause() else if (isResumed) screen?.onHostResume()
    }
    override fun onSaveInstanceState(out: Bundle) {
        screen?.saveState(out)
        out.putInt("pendingRequest", pendingRequest)
        super.onSaveInstanceState(out)
    }
    private fun night(): Boolean {
        val preferences = BrowserPreferences(requireContext())
        return preferences.isNightMode
    }
    internal fun refreshForTheme() { refreshTheme() }
    private fun refreshTheme(): Boolean {
        if (view == null || night() == appliedNight || parentFragmentManager.isStateSaved) return false
        parentFragmentManager.beginTransaction().detach(this).commit()
        parentFragmentManager.beginTransaction().attach(this).commit()
        return true
    }
    override fun onConfigurationChanged(configuration: Configuration) { super.onConfigurationChanged(configuration); refreshTheme() }
    override fun onDestroyView() {
        viewState = Bundle().also { screen?.saveState(it) }
        screen?.close(); screen = null; back = null
        // d9.q.D1 restores the host bars with the view, before a theme reattachment.
        context?.let { WindowInsetsHelper.restoreBarAppearance(it, barAppearance) }
        barAppearance = null
        super.onDestroyView()
    }
    override fun onDestroy() {
        // d9.q.B1 invalidates the live homepage after this independent preview closes.
        GeneratedDocumentState.mark(GeneratedDocumentState.HOME_CONTENT)
        super.onDestroy()
    }
}
