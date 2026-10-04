package dev.ujhhgtg.via.records

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.settings.SettingsPageFragment
import dev.ujhhgtg.via.settings.SettingsToolbar
import dev.ujhhgtg.via.ui.dp

/** bb.i: the original three page records container (bookmarks, history, saved pages). */
class RecordsContainerFragment : SettingsPageFragment() {
    private lateinit var pager: ViewPager2
    private val pages = arrayOf(R.string.action_bookmarks, R.string.action_history, R.string.action_saved_pages)
    private lateinit var tabs: List<Tab>

    override fun configureToolbar(toolbar: SettingsToolbar) {
        toolbar.titleView.visibility = View.GONE
        val scroll = HorizontalScrollView(requireContext()).apply {
            isHorizontalScrollBarEnabled = false; isFillViewport = true
            // TabLayout's constructor casts TypedValue dimensions instead of rounding.
            setPadding(0, 0, 0, (resources.displayMetrics.density * 2f).toInt())
        }
        val tabRow = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        tabs = pages.mapIndexed { index, title ->
            Tab(requireContext(), title) { pager.setCurrentItem(index, true) }.also { tabRow.addView(it, LinearLayout.LayoutParams(-2, -1)) }
        }
        scroll.addView(tabRow, ViewGroup.LayoutParams(-2, -1))
        toolbar.removeViewAt(1)
        toolbar.addView(scroll, 1, LinearLayout.LayoutParams(-2, -1))
    }

    override fun createContent(inflater: LayoutInflater, container: ViewGroup?): View {
        val context = requireContext()
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        pager = ViewPager2(context).apply {
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            adapter = object : FragmentStateAdapter(this@RecordsContainerFragment) {
                override fun getItemCount() = pages.size
                override fun createFragment(position: Int): Fragment = when (position) {
                    0 -> RecordsBookmarksFragment.newInstance(arguments?.getString("folder").orEmpty(), if (arguments?.getInt("page") == 0) arguments?.getString("query").orEmpty() else "")
                    1 -> RecordsHistoryFragment.newInstance(if (arguments?.getInt("page") == 1) arguments?.getString("query").orEmpty() else "")
                    else -> RecordsSavedPagesFragment.newInstance(if (arguments?.getInt("page") == 2) arguments?.getString("query").orEmpty() else "")
                }
            }
        }
        body.addView(pager, LinearLayout.LayoutParams(-1, 0, 1f))
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                tabs.forEachIndexed { index, tab -> tab.setSelected(index == position) }
                updateRecordsNavigation()
            }
        })
        return body
    }
    companion object { fun newInstance(page: Int = 0, folder: String = "", query: String = "") = RecordsContainerFragment().apply { arguments = Bundle().apply { putInt("page", page); putString("folder", folder); putString("query", query) } } }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) { super.onViewCreated(view, savedInstanceState); pager.setCurrentItem((arguments?.getInt("page") ?: 0).coerceIn(0, 2), false); tabs.forEachIndexed { index, tab -> tab.setSelected(index == pager.currentItem) } }

    private fun currentPage() = childFragmentManager.findFragmentByTag("f${pager.currentItem}") as? RecordsBackHandler
    override fun onToolbarBack() { if (currentPage()?.handleRecordsBack() != true) super.onToolbarBack() }
    override fun allowPredictiveBack() = currentPage()?.recordsHasTransientState != true
    internal fun updateRecordsNavigation() {
        if (!::pager.isInitialized) return
        (view as? dev.ujhhgtg.via.ui.SwipeBackLayout)?.setGestureEnabled(pager.currentItem == 0 && currentPage()?.recordsHasTransientState != true)
    }

    /** TabLayout.n/w: each tab is one TextView; the transparent indicator lives in the scroll view. */
    private class Tab(context: Context, title: Int, click: () -> Unit) : TextView(context) {
        init {
            text = context.getString(title); gravity = android.view.Gravity.CENTER; isAllCaps = false; maxLines = 1
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_toolbar_title_size).toFloat())
            setTypeface(dev.ujhhgtg.via.data.BrowserPreferences(context).selectedTypeface(), Typeface.NORMAL)
            val padding = (resources.displayMetrics.density * 12f).toInt()
            setPadding(padding, 0, padding, 0)
            context.obtainStyledAttributes(intArrayOf(R.attr.viaTabTint)).let { colors ->
                try { setTextColor(colors.getColorStateList(0)) } finally { colors.recycle() }
            }
            isFocusable = true; setBackgroundResource(R.drawable.flat_ripple)
            setOnClickListener { click() }
        }
    }

}
