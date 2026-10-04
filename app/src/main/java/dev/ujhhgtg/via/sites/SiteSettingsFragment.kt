package dev.ujhhgtg.via.sites

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.common.WindowInsetsHelper
import dev.ujhhgtg.via.settings.SettingsPageFragment
import dev.ujhhgtg.via.settings.SettingsToolbar

/** hb.w6/jb.u4 -> o8.g: list and editor are distinct instances on the real Fragment back stack. */
class SiteSettingsFragment : SettingsPageFragment() {
    private var screen: SiteSettingsView? = null
    private var editorState: SiteSettingsView.EditorState? = null
    private var changed = 0
    private val domain get() = arguments?.getString("domain")

    override fun configureToolbar(toolbar: SettingsToolbar) {
        toolbar.setTitle(if (domain == null) R.string.all_sites else R.string.site_conf)
    }
    override fun createContent(inflater: LayoutInflater, container: ViewGroup?): View =
        SiteSettingsView(requireActivity(), toolbar, ::closePage, ::openDomain,
            { changed = changed or 1 }, domain, arguments?.getInt("flags") ?: 0,
            { mask -> changed = changed or mask }).also {
            screen = it
            it.restoreState(editorState)
        }
    override fun applyInsets(body: View, toolbar: SettingsToolbar) = WindowInsetsHelper.apply(body, toolbar, screen?.list)
    override fun onToolbarBack() { screen?.onBack() }
    private fun closePage() { super.onToolbarBack() }

    private fun openDomain(domain: String) {
        parentFragmentManager.setFragmentResultListener(RESULT, viewLifecycleOwner) { _, result ->
            if (result.getInt("changed") and 1 != 0) screen?.refreshList()
            parentFragmentManager.clearFragmentResultListener(RESULT)
        }
        (requireActivity() as Shell).navigate(newInstance(domain))
    }
    override fun onDestroyView() {
        editorState = screen?.editorState()
        screen?.close(); screen = null
        super.onDestroyView()
    }
    override fun onDestroy() {
        // V3 publishes an empty result too, so the opener clears its one-shot listener.
        if (domain != null) {
            val mask = changed or SiteSettingsView.saveState(requireContext(), editorState)
            parentFragmentManager.setFragmentResult(RESULT, Bundle().apply { if (mask != 0) putInt("changed", mask) })
        }
        super.onDestroy()
    }
    companion object {
        private const val RESULT = "site_settings_changed"
        fun newInstance(domain: String? = null, flags: Int = 0) = SiteSettingsFragment().apply {
            arguments = Bundle().apply { putString("domain", domain); putInt("flags", flags) }
        }
    }
}
