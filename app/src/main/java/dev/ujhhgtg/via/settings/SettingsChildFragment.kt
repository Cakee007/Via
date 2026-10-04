package dev.ujhhgtg.via.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.ui.SettingsScreen

/** Existing child settings handlers, retained while each original child fragment is translated. */
class SettingsChildFragment : Fragment() {
    private var controller: SettingsController? = null
    private var initialActionDelivered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialActionDelivered = savedInstanceState?.getBoolean("initial_action_delivered") ?: false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val action = arguments?.getString("action")
        val page = SettingsScreen.Page.fromAction(action) ?: if (action == null) SettingsScreen.Page.ROOT else SettingsScreen.Page.GENERAL
        return SettingsController(requireActivity(), onBack = {
            parentFragmentManager.popBackStack()
        }, openPage = { target ->
            val shell = requireActivity() as Shell
            if (SettingsScreen.Page.fromAction(target) != null) shell.navigate(newInstance(target))
            else shell.openPage(target)
        }, page = page, launchForResult = { intent, requestCode ->
            @Suppress("DEPRECATION")
            startActivityForResult(intent, requestCode)
        }, scopeOwner = this, exportScopeOwner = this).also { controller = it; it.restoreState(state) }.view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!initialActionDelivered) {
            initialActionDelivered = true
            arguments?.getString("action")?.takeIf { SettingsScreen.Page.fromAction(it) == null }?.let { controller?.route(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        controller?.onHostResume()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) controller?.onHostResume()
    }

    /** Credential fallback is launched by the Activity; Shell forwards that result here. */
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean =
        controller?.onActivityResult(requestCode, resultCode, data) == true

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        handleActivityResult(requestCode, resultCode, data)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("initial_action_delivered", initialActionDelivered)
        controller?.saveState(outState)
    }

    override fun onDestroyView() {
        controller?.close()
        controller = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(action: String? = null) = SettingsChildFragment().apply {
            arguments = Bundle().apply { action?.let { putString("action", it) } }
        }
    }
}
