package dev.ujhhgtg.via.browser

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.browser.script.ScriptStore
import dev.ujhhgtg.via.browser.script.UserScript
import dev.ujhhgtg.via.settings.ScriptDetailsFragment
import dev.ujhhgtg.via.settings.ScriptEditorFragment
import dev.ujhhgtg.via.settings.ScriptSettingsFragment
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialogFragment
import dev.ujhhgtg.via.common.launchIo
import org.json.JSONArray
import org.json.JSONObject

/** ua.y/k8.a: a floating Via dialog containing ua.t and ua.i as separate fragments. */
class PageScriptsDialogFragment : ViaDialogFragment() {
    internal lateinit var model: PageScriptsModel
        private set
    private lateinit var pager: ViewPager2

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        model = ViewModelProvider(this)[PageScriptsModel::class.java]
        model.initialize(requireContext(), arguments?.getString("url"))
        if (state != null) model.restoreSelection(state.getInt("selected_id"), state.getInt("page"))
        arguments?.getString("menus")?.let(model::setMenus)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View = ViewPager2(requireContext()).apply {
        id = R.id.page_scripts_pager
        layoutParams = FrameLayout.LayoutParams(-1, -1)
        isUserInputEnabled = false
        adapter = object : FragmentStateAdapter(this@PageScriptsDialogFragment) {
            override fun getItemCount() = 2
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> PageScriptsListFragment()
                else -> PageScriptActionsFragment()
            }
        }
        pager = this
        setCurrentItem(model.page, false)
    }

    override fun onStart() {
        super.onStart()
        // ua.y.R1 -> z8.n0.b/g6.y.A: DPAD navigates only the visible page.
        dialog?.setOnKeyListener { _, key, event ->
            if (event.action != KeyEvent.ACTION_DOWN || key !in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT) false
            else {
                val page = childFragmentManager.findFragmentByTag("f${pager.currentItem}")?.view as? ViewGroup
                if (page == null) false else {
                    val focused = page.findFocus()
                    if (focused == null) { page.requestFocus(); true }
                    else if (focused is EditText && key in arrayOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) false
                    else {
                        val direction = when (key) {
                            KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
                            KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
                            KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
                            else -> View.FOCUS_RIGHT
                        }
                        focused.focusSearch(direction)?.let { it.requestFocus(); true } ?: false
                    }
                }
            }
        }
    }

    internal fun select(script: PageScriptEntry) {
        model.selected.value = script
        showPage(1)
    }

    internal fun showPage(page: Int) {
        model.page = page
        pager.setCurrentItem(page, true)
    }

    internal fun perform(action: Int, command: String? = null) {
        when (action) {
            0, 1, 3 -> {
                val shell = activity as? Shell ?: return
                val destination = when (action) {
                    0 -> ScriptEditorFragment.forUrl(model.url.orEmpty())
                    1 -> ScriptDetailsFragment.newInstance(model.selected.value?.script?.id ?: 0)
                    else -> ScriptSettingsFragment().apply { arguments = Bundle().apply { putInt("id", model.selected.value?.script?.id ?: 0) } }
                }
                dismiss()
                shell.navigate(destination)
            }
            2 -> model.toggleSelected()
            4 -> {
                // ua.y.j3 -> c0.p runs its database update on the IO scheduler.
                viewLifecycleOwner.launchIo({ model.excludeSite() }, { success ->
                        if (success) {
                            dismiss()
                            context?.let { ViaToast.show(it, it.getString(R.string.exclude_domain_from_script_toast,
                                model.selected.value?.script?.name, AddressTitleFormatter.domain(model.url))) }
                        }
                    }, { Log.w("ViaScripts", "Cannot exclude script from site", it) })
            }
            -1 -> {
                val identity = model.selected.value?.script?.scriptId ?: return
                if (command == null) return
                val manager = parentFragmentManager
                dismiss()
                manager.setFragmentResult(RESULT, Bundle().apply { putString("script_id", identity); putString("name", command) })
            }
        }
    }

    override fun onSaveInstanceState(out: Bundle) {
        out.putInt("selected_id", model.selected.value?.script?.id ?: 0); out.putInt("page", model.page)
        super.onSaveInstanceState(out)
    }

    companion object {
        const val RESULT = "page_scripts_result"
        fun newInstance(url: String?, menus: String = "{}") = PageScriptsDialogFragment().apply {
            arguments = Bundle().apply { putString("url", url); putString("menus", menus) }
        }
    }
}

/** r5.c is mutable: both source pages observe the same selected item's enable state. */
internal class PageScriptEntry(var script: UserScript)

/** ua.c0: matched metadata, selected item, enable-only notifications and sorted GM command lists. */
class PageScriptsModel : ViewModel() {
    private lateinit var store: ScriptStore
    internal var url: String? = null
        private set
    internal var page = 0
    internal val scripts = MutableLiveData<List<PageScriptEntry>>()
    internal val selected = MutableLiveData<PageScriptEntry>()
    internal val enabledChange = MutableLiveData<Boolean>()
    internal val menus = MutableLiveData<Map<String, List<String>>>()

    internal fun initialize(context: Context, url: String?) {
        if (!::store.isInitialized) store = ScriptStore(context)
        this.url = url
        // p5.a.G includes disabled matches; opening the list is the only sort pass.
        val values = store.list().filter { url.isNullOrEmpty() || it.appliesTo(url) }
            .sortedWith(compareByDescending<UserScript> { it.enabled }.thenByDescending { it.createdAt })
        scripts.value = values.map(::PageScriptEntry)
    }

    internal fun restoreSelection(id: Int, position: Int) {
        if (selected.value == null) scripts.value?.firstOrNull { it.script.id == id }?.let { selected.value = it }
        page = if (selected.value == null) 0 else position
    }

    /** c0.B updates the checkbox's shared metadata without rebuilding or sorting the list. */
    internal fun setEnabled(entry: PageScriptEntry, enabled: Boolean) {
        entry.script = entry.script.copy(enabled = enabled)
        store.setEnabled(entry.script.id, enabled)
    }

    /** c0.o notifies both pages; ua.i.b3 changes only its first row. */
    internal fun toggleSelected() {
        val entry = selected.value ?: return
        entry.script = entry.script.copy(enabled = !entry.script.enabled)
        enabledChange.value = entry.script.enabled
        store.setEnabled(entry.script.id, entry.script.enabled)
    }

    /** c0.n: strict object-of-string-arrays, natural ordering, no empty-array entries. */
    internal fun setMenus(json: String) {
        try {
            val root = JSONObject(json)
            val result = HashMap<String, List<String>>()
            root.keys().forEach { identity ->
                val array = root.getJSONArray(identity)
                if (array.length() > 0) result[identity] = List(array.length()) { array.getString(it) }.sorted()
            }
            menus.value = result
        } catch (error: Exception) { Log.w("ViaScripts", "Cannot read script commands", error) }
    }

    internal fun selectedCommands(): List<String> = selected.value?.script?.scriptId?.let { menus.value?.get(it) }.orEmpty()

    /** c0.p + r5.e/s5.e, cross-checked against smali: writes excludes, not excludeMatches. */
    internal fun excludeSite(): Boolean {
        val script = selected.value?.script ?: return false
        val source = url ?: return false
        if (script.id <= 0 || !pageScriptsNetworkUrl(source)) return false
        val scheme = source.indexOf("://")
        if (scheme < 0) return false
        val host = AddressTitleFormatter.domain(source)
        if (host.isEmpty()) return false
        val pattern = source.substring(0, scheme + 3) + host + "/*"
        val existing = store.find(script.id)?.userOverrides?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
        val excludeMatches = existing?.optString("excludeMatches")
        val exclusions = (UserScript.jsonStrings(excludeMatches) + UserScript.jsonStrings(existing?.optString("excludes"))).toMutableList()
        if (pattern in exclusions) return true
        exclusions += pattern
        val overrides = JSONObject()
        existing?.optInt("runAt", 0)?.takeIf { it != 0 }?.let { overrides.put("runAt", it) }
        // r5.e.a uses optString's empty-string default for an existing override object.
        if (existing != null) {
            overrides.put("matches", existing.optString("matches"))
            overrides.put("excludeMatches", excludeMatches)
            overrides.put("includes", existing.optString("includes"))
        }
        overrides.put("excludes", JSONArray(exclusions.filter(String::isNotEmpty)).toString())
        return store.setUserOverrides(script.id, overrides.toString())
    }

    override fun onCleared() { if (::store.isInitialized) store.close() }
}

/** i6.i0.s uses case-insensitive http/https prefix matching. */
internal fun pageScriptsNetworkUrl(url: String) = url.startsWith("http://", true) || url.startsWith("https://", true)
