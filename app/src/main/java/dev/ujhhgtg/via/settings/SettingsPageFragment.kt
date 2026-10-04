package dev.ujhhgtg.via.settings

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.SwipeBackLayout
import dev.ujhhgtg.via.ui.BrowserBackgrounds
import dev.ujhhgtg.via.ui.dp

/** o8.a/h: original full-screen toolbar/content fragment with swipe-back container. */
abstract class SettingsPageFragment : Fragment() {
    private var themeRefreshPending = false
    protected lateinit var toolbar: SettingsToolbar
        private set

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        toolbar = SettingsToolbar(context) { onToolbarBack() }
        configureToolbar(toolbar)
        body.addView(toolbar, LinearLayout.LayoutParams(-1, -2))
        body.addView(createContent(inflater, container), LinearLayout.LayoutParams(-1, 0, 1f))
        applyInsets(body, toolbar)
        val surface = BrowserBackgrounds.wrapSettings(context, body)
        return SwipeBackLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            attach(this@SettingsPageFragment, surface)
            setEdgeSize(-2)
            setScrollThresholdSize(context.dp(200f).toFloat())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // z8.n3.h: original bool is false by default, true in values-v35; HONOR/HUAWEI excluded.
        if (predictiveBackSupported()) {
            val swipe = view as SwipeBackLayout
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
                private var predictive = false
                override fun handleOnBackStarted(backEvent: BackEventCompat) {
                    if (!predictive && !BrowserPreferences(requireContext()).disablePredictiveBack && allowPredictiveBack() && this@SettingsPageFragment.view != null) {
                        predictive = swipe.startPredictiveBack()
                    }
                }
                override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                    if (predictive) swipe.updatePredictiveBack(backEvent.progress)
                }
                override fun handleOnBackCancelled() {
                    if (predictive) { swipe.cancelPredictiveBack(); predictive = false }
                }
                override fun handleOnBackPressed() {
                    if (predictive) { predictive = false; swipe.finishPredictiveBack() }
                    else onToolbarBack()
                }
            })
        }
    }

    protected fun predictiveBackSupported(): Boolean = Build.VERSION.SDK_INT >= 35 &&
        listOf(Build.BRAND, Build.MANUFACTURER).none { it.equals("HONOR", true) || it.equals("HUAWEI", true) }

    protected abstract fun configureToolbar(toolbar: SettingsToolbar)
    protected abstract fun createContent(inflater: LayoutInflater, container: ViewGroup?): View
    protected open fun applyInsets(body: View, toolbar: SettingsToolbar) = dev.ujhhgtg.via.common.WindowInsetsHelper.apply(body, toolbar)
    protected open fun onToolbarBack() { if (!parentFragmentManager.isStateSaved) parentFragmentManager.popBackStack() }
    protected open fun allowPredictiveBack(): Boolean = true

    /** o8.a.b0: rebuilding the page updates both themed controls and its background snapshot. */
    internal fun refreshForTheme() {
        if (view == null || isDetached) return
        val manager = parentFragmentManager
        if (manager.isStateSaved) { themeRefreshPending = true; return }
        themeRefreshPending = false
        manager.beginTransaction().detach(this).commit()
        manager.beginTransaction().attach(this).commit()
    }

    override fun onPause() {
        view?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        view?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        if (themeRefreshPending) refreshForTheme()
    }
}
