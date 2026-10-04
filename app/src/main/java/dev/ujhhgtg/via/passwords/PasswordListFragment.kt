package dev.ujhhgtg.via.passwords

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.common.WindowInsetsHelper
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.records.RecordsActionBar
import dev.ujhhgtg.via.records.RecordsPageLayout
import dev.ujhhgtg.via.settings.SettingsPageFragment
import dev.ujhhgtg.via.settings.SettingsRangeSelection
import dev.ujhhgtg.via.settings.SettingsToolbar
import dev.ujhhgtg.via.settings.settingsCheckBox
import dev.ujhhgtg.via.settings.settingsColor
import dev.ujhhgtg.via.settings.settingsRipple
import dev.ujhhgtg.via.skins.SkinResources
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import dev.ujhhgtg.via.ui.dp

/** va.r0 -> o8.h: independent records page, retaining its search and selection behind the editor. */
class PasswordListFragment : SettingsPageFragment() {
    private val operations = PasswordPageOperations(this)
    private val repository get() = operations.repository
    private val authentication get() = operations.authentication
    private var searchQuery = ""
    private var passwordRows = emptyList<Any>()
    private var passwordRecords = emptyList<PasswordRecord>()
    private val selection = linkedSetOf<String>()
    private var selecting = false
    private var passwordPage: RecordsPageLayout? = null
    private var rangeSelection: SettingsRangeSelection? = null
    private val passwordAdapter = PasswordRows()
    private fun text(id: Int) = getString(id)
    private fun dp(value: Float) = requireContext().dp(value)
    private fun <T> work(block: () -> T, success: (T) -> Unit) = operations.work(block, success)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        operations.restoreState(state)
        searchQuery = state?.getString("query").orEmpty()
        selecting = state?.getBoolean("selecting") ?: false
        selection.addAll(state?.getStringArrayList("selection").orEmpty())
    }
    override fun configureToolbar(toolbar: SettingsToolbar) {
        toolbar.setTitle(R.string.passwords)
        toolbar.addAction(R.drawable.plus, R.string.action_new) { showEditor(null) }
    }
    override fun createContent(inflater: LayoutInflater, container: ViewGroup?): View {
        // va.r0 uses mark.via.common.widget.r0: search above the scrolling area,
        // a full-height shrug empty state, and a persistent bottom action bar.
        val pageLayout = RecordsPageLayout(requireContext(), searchQuery) { query -> searchQuery = query; loadPasswords(query) }
        passwordPage = pageLayout
        pageLayout.list.adapter = passwordAdapter
        rangeSelection = SettingsRangeSelection(requireContext(),
            selectable = { selecting && passwordRows.getOrNull(it) is PasswordRecord },
            checked = { (passwordRows.getOrNull(it) as? PasswordRecord)?.id?.let(selection::contains) == true },
            update = { position, checked ->
                (passwordRows.getOrNull(position) as? PasswordRecord)?.let { record ->
                    val changed = if (checked) selection.add(record.id) else selection.remove(record.id)
                    if (changed) { passwordAdapter.notifyItemChanged(position); bindPasswordActions() }
                }
            }, changed = {}).also(pageLayout.list::addOnItemTouchListener)
        return pageLayout
    }
    private fun loadPasswords(query: String, resetSelection: Boolean = true) {
        work({ repository.list(query).sortedWith { a, b -> PasswordPickerFragment.compareDomains(a.name, b.name) } }) { records ->
            if (resetSelection) { selecting = false; selection.clear() }; passwordRecords = records
            passwordRows = buildList {
                var name: String? = null
                records.forEach { record ->
                    if (record.name != name) { name = record.name; add(record.name) }
                    add(record)
                }
            }
            passwordAdapter.notifyDataSetChanged()
            passwordPage?.setEmpty(records.isEmpty())
            bindPasswordActions()
        }
    }

    private fun bindPasswordActions() {
        val actions = passwordPage?.actions ?: return
        val edit = RecordsActionBar.Action(text(if (selecting) R.string.done else R.string.action_edit), enabled = passwordRecords.isNotEmpty()) {
            selecting = !selecting; selection.clear(); passwordAdapter.notifyDataSetChanged(); bindPasswordActions()
        }
        if (!selecting) { actions.show(right = listOf(edit)); return }
        val all = passwordRecords.isNotEmpty() && selection.size == passwordRecords.size
        actions.show(right = listOf(edit), left = listOf(
            RecordsActionBar.Action(text(if (all) R.string.cancel_all else R.string.select_all)) {
                if (all) selection.clear() else selection.addAll(passwordRecords.map { it.id })
                passwordAdapter.notifyDataSetChanged(); bindPasswordActions()
            },
            RecordsActionBar.Action(if (selection.isEmpty()) text(R.string.action_delete) else requireContext().getString(R.string.delete_hint, selection.size),
                enabled = selection.isNotEmpty(), destructive = true) { deleteSelectedPasswords() },
        ))
    }

    private fun deleteSelectedPasswords() {
        val selected = passwordRecords.filter { it.id in selection }
        if (selected.isEmpty()) return
        val message = if (selected.size == 1) requireContext().getString(R.string.delete_item_message, selected[0].username)
            else requireContext().getString(R.string.delete_items_message, selected.size)
        ViaDialog(requireActivity()).title(R.string.action_delete).message(message)
            .positive(android.R.string.ok) { _, _ -> work({ selected.forEach { repository.delete(it.id) } }) { loadPasswords(passwordPage?.search?.text?.toString().orEmpty()) } }
            .negative(android.R.string.cancel).show()
    }

    private fun recordMenu(entry: PasswordRecord, anchor: View) {
        ViaDialog(requireActivity()).items(arrayOf(text(R.string.copy_username), text(R.string.copy_password), text(R.string.action_delete)), onClick = { which ->
            when (which) {
                0 -> copy(entry.username, false)
                1 -> authentication.authenticate(text(R.string.copy_password), text(R.string.unlock_device_to_copy_password)) {
                    work({ repository.get(entry.id, true)?.password }) { it?.let { password -> copy(password, true) } }
                }
                2 -> ViaDialog(requireActivity()).title(text(R.string.action_delete)).message(requireContext().getString(R.string.delete_item_message, entry.username))
                    .positive(android.R.string.ok) { _, _ -> work({ repository.delete(entry.id) }) { loadPasswords(searchQuery) } }
                    .negative(android.R.string.cancel).show()
            }
        }).showAnchored(anchor)
    }

    private fun copy(value: String, sensitive: Boolean) {
        val clip = ClipData.newPlainText("", value)
        if (sensitive) clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    }

    private inner class PasswordRows : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = passwordRows.size
        override fun getItemViewType(position: Int) = if (passwordRows[position] is String) 0 else 1
        override fun onCreateViewHolder(parent: ViewGroup, kind: Int): RecyclerView.ViewHolder {
            val label = TextView(requireContext()).apply {
                textDirection = View.TEXT_DIRECTION_LOCALE
                typeface = Typeface.create(BrowserPreferences(requireContext()).selectedTypeface(), if (kind == 0) Typeface.BOLD else Typeface.NORMAL)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(if (kind == 0) R.dimen.settings_row_summary_size else R.dimen.settings_row_title_size).toFloat())
                setTextColor(settingsColor(requireContext(), if (kind == 0) R.attr.viaAccentColor else R.attr.viaPrimaryTextColor, 0xff000000.toInt()))
                setPadding(dp(16f), dp(12f), dp(16f), dp(if (kind == 0) 4f else 12f))
                if (kind == 1) {
                    gravity = Gravity.CENTER_VERTICAL; setSingleLine(); ellipsize = null
                    isHorizontalFadingEdgeEnabled = true; setFadingEdgeLength(dp(24f)); compoundDrawablePadding = dp(16f)
                }
            }
            if (kind == 0) return object : RecyclerView.ViewHolder(label.apply { layoutParams = RecyclerView.LayoutParams(-1, -2) }) {}
            val row = LinearLayout(requireContext()).apply {
                layoutParams = RecyclerView.LayoutParams(-1, -2); gravity = Gravity.CENTER_VERTICAL
                setPaddingRelative(0, 0, dp(16f), 0); background = settingsRipple(requireContext())
                addView(label, LinearLayout.LayoutParams(0, -2, 1f))
                addView(settingsCheckBox(requireContext(), "").apply {
                    isClickable = false; isFocusable = false; background = null; setPadding(0, 0, 0, 0)
                }, LinearLayout.LayoutParams(-2, dp(16f)).apply { setMargins(dp(2f), dp(2f), dp(2f), dp(2f)) })
            }
            return object : RecyclerView.ViewHolder(row) {}
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = passwordRows[position]
            if (item is String) { (holder.itemView as TextView).text = item; return }
            val record = item as PasswordRecord
            val row = holder.itemView as LinearLayout
            val label = row.getChildAt(0) as TextView
            label.text = record.username
            val iconFile = dev.ujhhgtg.via.home.HomeIcons.file(requireContext(), record.name)
            val icon = iconFile?.let { Drawable.createFromPath(it.path) } ?: SkinResources.drawable(requireContext(), R.drawable.globe)?.mutate()?.apply {
                setTint(settingsColor(requireContext(), R.attr.viaSubtleColor, 0xff000000.toInt()))
            }
            icon?.setBounds(0, 0, dp(20f), dp(20f))
            label.setCompoundDrawablesRelative(icon, null, null, null)
            (row.getChildAt(1) as CheckBox).apply { visibility = if (selecting) View.VISIBLE else View.GONE; isChecked = record.id in selection }
            row.setOnClickListener {
                if (selecting) {
                    if (!selection.add(record.id)) selection.remove(record.id)
                    notifyItemChanged(holder.bindingAdapterPosition); bindPasswordActions()
                } else openEditor(record.id)
            }
            row.setOnLongClickListener {
                if (selecting) rangeSelection?.start(holder.bindingAdapterPosition) else recordMenu(record, row)
                true
            }
        }
    }

    override fun applyInsets(body: View, toolbar: SettingsToolbar) =
        WindowInsetsHelper.apply(body, toolbar, requireNotNull(passwordPage).actions)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadPasswords(searchQuery, resetSelection = false)
    }
    private fun openEditor(id: String) = authentication.authenticate(text(R.string.view_password), text(R.string.unlock_device_to_view_password)) {
        showEditor(id)
    }
    private fun showEditor(id: String?) {
        // va.r0.x3/Z2: only a saved editor result reloads the retained list.
        parentFragmentManager.setFragmentResultListener(PasswordEditorFragment.RESULT, this) { _, result ->
            if (result.getString("result") != null) loadPasswords("", resetSelection = false)
            parentFragmentManager.clearFragmentResultListener(PasswordEditorFragment.RESULT)
        }
        (requireActivity() as Shell).navigate(PasswordEditorFragment.newInstance(id))
    }
    override fun onSaveInstanceState(out: Bundle) {
        operations.saveState(out)
        out.putString("query", searchQuery)
        out.putBoolean("selecting", selecting)
        out.putStringArrayList("selection", ArrayList(selection))
        super.onSaveInstanceState(out)
    }
    override fun onDestroyView() {
        rangeSelection?.finish(); rangeSelection = null
        passwordPage = null
        operations.destroyView()
        super.onDestroyView()
    }
}
