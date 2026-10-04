package dev.ujhhgtg.via.settings

import android.os.Bundle
import android.view.View
import dev.ujhhgtg.via.common.launchIo
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.browser.UserAgentPolicy
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.SettingsData
import dev.ujhhgtg.via.data.SettingsDataRepository
import dev.ujhhgtg.via.ui.dialog.ViaDialog

/** hb.w7: UA choices, desktop choice, reduction, editor results and custom-row menu. */
class UserAgentSettingsFragment : SettingsListFragment() {
    private lateinit var preferences: BrowserPreferences
    private lateinit var database: BrowserDatabase
    private lateinit var repository: SettingsDataRepository
    private lateinit var rows: SettingsRowsAdapter
    private var selected = -999
    private val agents = mutableListOf<SettingsData>()

    override fun configureToolbar(toolbar: SettingsToolbar) {
        toolbar.setTitle(R.string.agent)
        toolbar.addAction(R.drawable.plus, R.string.action_new) { edit(0) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = BrowserPreferences(requireContext())
        database = BrowserDatabase(requireContext())
        repository = SettingsDataRepository(database)
        rows = SettingsRowsAdapter { item ->
            when (item) {
                is SettingsChoiceRow -> if (item.id != selected) { select(agents.first { it.id == item.id }); bindRows() }
                is SettingsToggleRow -> if (item.id == 2) { preferences.webFlags2 = preferences.webFlags2 xor 1; bindRows() }
                else -> if (item.id == 1) desktopChoice()
            }
        }.apply { onLongClick = { anchor, row ->
            if (row !is SettingsChoiceRow) false
            else {
                if (row.id > 0) ViaDialog(requireActivity()).items(arrayOf(getString(R.string.action_edit), getString(R.string.action_delete)), onClick = { position ->
                    if (position == 0) edit(row.id)
                    else {
                        if (row.id == selected) select(agents[0])
                        repository.delete(row.id)
                        agents.removeAll { it.id == row.id }
                        bindRows()
                    }
                }).showAnchored(anchor)
                true
            }
        } }
        list.adapter = rows
        selected = preferences.userAgentChoice
        agents += builtins()
        agents += repository.list(SettingsData.USER_AGENT)
        bindRows()
    }

    private fun builtins(): List<SettingsData> {
        val labels = intArrayOf(R.string.default_set, R.string.agent_android_phone, R.string.agent_android_tablet, R.string.agent_windows_chrome,
            R.string.agent_windows_ie11, R.string.agent_osx, R.string.agent_iphone, R.string.agent_ipad, R.string.agent_symbian)
        return labels.mapIndexed { index, label -> SettingsData(id = -index, title = getString(label), type = SettingsData.USER_AGENT,
            content = UserAgentPolicy.resolve(-index, null, null, false, 0, null, false)) }
    }

    private fun bindRows() {
        rows.submit(buildList {
            agents.forEach { add(SettingsChoiceRow(it.id, it.title.orEmpty(), it.id == selected)) }
            add(SettingsHeadingRow(getString(R.string.settings_advanced)))
            add(SettingsRow(1, getString(R.string.user_agent_in_desktop_mode), agents.firstOrNull { it.id == preferences.duaChoice }?.title.orEmpty()))
            add(SettingsToggleRow(2, getString(R.string.user_agent_reduction), getString(R.string.user_agent_reduction_description), preferences.webFlags2 and 1 != 0))
        })
    }

    private fun desktopChoice() {
        val choices = agents.filter { it.id > 0 || it.id in intArrayOf(0, -3, -4, -5) }
        val current = choices.indexOfFirst { it.id == preferences.duaChoice }.coerceAtLeast(0)
        ViaDialog(requireActivity()).title(R.string.user_agent_in_desktop_mode)
            .singleChoice(choices.map { it.title.orEmpty() }.toTypedArray(), current) { position ->
                selectDesktop(choices[position]); bindRows()
            }.show()
    }

    private fun select(item: SettingsData) {
        selected = item.id
        preferences.userAgentChoice = selected
        if (selected > 0 || selected <= -999) preferences.userAgent = item.content.orEmpty()
    }
    private fun selectDesktop(item: SettingsData) {
        preferences.duaChoice = item.id
        if (item.id > 0 || item.id <= -999) preferences.duaString = item.content
    }

    private fun edit(id: Int) {
        parentFragmentManager.setFragmentResultListener("ua_result", this) { _, result ->
            val savedId = result.getInt("ua_result", 0)
            if (savedId > 0) refreshAgent(savedId)
            parentFragmentManager.clearFragmentResultListener("ua_result")
        }
        (requireActivity() as Shell).navigate(UserAgentEditorFragment.newInstance(id))
    }

    private fun refreshAgent(id: Int) {
        val index = agents.indexOfFirst { it.id == id }
        viewLifecycleOwner.launchIo({ repository.find(id) }, { item ->
            if (item == null) return@launchIo
                if (index < 0) { agents += item; bindRows(); list.scrollToPosition(agents.size) }
                else {
                    agents[index] = item
                    if (id == selected) select(item)
                    if (id == preferences.duaChoice) selectDesktop(item)
                    bindRows()
                }
        }) { android.util.Log.w("ViaSettings", "Unable to reload saved user agent", it) }
    }

    override fun onDestroyView() { database.close(); super.onDestroyView() }
}
