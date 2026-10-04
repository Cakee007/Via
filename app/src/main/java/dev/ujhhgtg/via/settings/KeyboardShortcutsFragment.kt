package dev.ujhhgtg.via.settings

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.ui.dp

/** ra.i and original layout/a3: keyboard chord and right-aligned action columns. */
class KeyboardShortcutsFragment : SettingsListFragment() {
    override fun configureToolbar(toolbar: SettingsToolbar) = toolbar.setTitle(R.string.keyboard_shortcuts)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val shortcuts = listOf(
            "Ctrl + t" to R.string.action_new_tab, "Ctrl + Tab" to R.string.operation_nexttab,
            "Ctrl + Shift + Tab" to R.string.operation_lasttab, "Alt + ←" to R.string.operation_goback,
            "Alt + →" to R.string.operation_goforward, "Ctrl + w" to R.string.operation_closetab,
            "Alt + f" to R.string.desc_menu, "Ctrl + Shift + b" to R.string.action_bookmarks,
            "Ctrl + h" to R.string.action_history, "Ctrl + f" to R.string.action_find,
            "Ctrl + l" to R.string.operation_input, "F5 / Ctrl + r" to R.string.operation_reload,
            "Ctrl + u" to R.string.action_source, "Ctrl + d" to R.string.action_add_bookmark,
        )
        list.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = shortcuts.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val context = parent.context
                val row = LinearLayout(context).apply {
                    gravity = Gravity.CENTER; background = controlRipple(context)
                    setPadding(context.dp(16f), context.dp(12f), context.dp(16f), context.dp(12f))
                    layoutParams = RecyclerView.LayoutParams(-1, -2)
                }
                repeat(2) { column -> row.addView(TextView(context).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
                    setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, 0xff000000.toInt())); textDirection = View.TEXT_DIRECTION_LOCALE
                    if (column == 0) typeface = Typeface.DEFAULT_BOLD else gravity = Gravity.END
                }, LinearLayout.LayoutParams(if (column == 0) -2 else -1, -2)) }
                return object : RecyclerView.ViewHolder(row) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val row = holder.itemView as LinearLayout
                (row.getChildAt(0) as TextView).text = shortcuts[position].first
                (row.getChildAt(1) as TextView).setText(shortcuts[position].second)
            }
        }
    }
}
