package dev.ujhhgtg.via.settings

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.util.TypedValue
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.ujhhgtg.via.ui.ViaToast
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.behavior.BehaviorPreferences
import dev.ujhhgtg.via.ui.behavior.OriginalActions
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import dev.ujhhgtg.via.ui.behavior.ToolbarSwipeLayout
import dev.ujhhgtg.via.ui.NavigationBar
import dev.ujhhgtg.via.ui.dp
import dev.ujhhgtg.via.Shell

/** hb.u4: gesture toggles and source action picker in a full settings page. */
class GesturesSettingsFragment : SettingsListFragment() {
    private lateinit var behavior: BehaviorPreferences
    private lateinit var rows: SettingsRowsAdapter
    override fun configureToolbar(toolbar: SettingsToolbar) = toolbar.setTitle(R.string.settings_operation)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        behavior = BehaviorPreferences(BrowserPreferences(requireContext()))
        rows = SettingsRowsAdapter { row ->
            when (row.id) {
                1 -> { behavior.backForwardGesture = !behavior.backForwardGesture; bindRows() }
                2 -> { behavior.volumeScroll = !behavior.volumeScroll; bindRows() }
                4 -> { behavior.videoGestures = !behavior.videoGestures; bindRows() }
                3 -> (requireActivity() as Shell).navigate(KeyboardShortcutsFragment())
            }
        }
        list.itemAnimator = null
        list.adapter = ConcatAdapter(rows, object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = 1
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = object : RecyclerView.ViewHolder(toolbarPreview()) {}
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
        })
        bindRows()
    }
    private fun bindRows() = rows.submit(listOf(
        SettingsToggleRow(1, getString(R.string.back_forward_gesture), checked = behavior.backForwardGesture),
        SettingsToggleRow(2, getString(R.string.volume), checked = behavior.volumeScroll),
        SettingsToggleRow(4, getString(R.string.enable_video_player_gestures), getString(R.string.enable_video_player_gestures_description), behavior.videoGestures),
        SettingsRow(3, getString(R.string.keyboard_shortcuts)),
    ))
    private fun chooseAction(slot: Int) {
        parentFragmentManager.setFragmentResultListener("hb.l6", this) { _, result ->
            behavior.setLongPressAction(slot, result.getInt("id"))
            parentFragmentManager.clearFragmentResultListener("hb.l6")
        }
        (requireActivity() as Shell).navigate(GestureActionFragment.newInstance(behavior.longPressAction(slot)))
    }
    /** ib.m + mark.via.common.widget.g0. The preview buttons configure their long-press action. */
    private fun toolbarPreview(): View {
        val context = requireContext()
        // ib.m embeds the same g0 used by the browser, including its counter baseline and ripple.
        val controls = NavigationBar(context).apply {
            onItemClick = ::chooseAction
            // hb.u4.k3: the current action's name, with a "Modify" action that opens the picker (l3).
            onItemLongClick = { slot ->
                ViaToast.show(context, OriginalActions.entries[behavior.longPressAction(slot)]?.title(context).orEmpty(),
                    actionText = getString(R.string.action_modify)) { chooseAction(slot) }
                true
            }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; layoutParams = RecyclerView.LayoutParams(-1, -2)
            addView(TextView(context).apply {
                setText(R.string.toolbar_settings_hint); typeface = BrowserPreferences(context).selectedTypeface()
                setTextColor(settingsColor(context, R.attr.viaSecondaryTextColor, 0xff444444.toInt()))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_summary_size).toFloat())
                setPadding(context.dp(16f), context.dp(10f), context.dp(16f), context.dp(10f))
            }, LinearLayout.LayoutParams(-1, -2))
            addView(ToolbarSwipeLayout(context) { fraction, released ->
                if (released && kotlin.math.abs(fraction).toDouble() >= .8) chooseAction(if (fraction > 0f) 5 else 6)
            }.apply { interceptParentTouch = true; addView(controls, FrameLayout.LayoutParams(-1, context.dp(48f))) }, LinearLayout.LayoutParams(-1, -2))
        }
    }
}
