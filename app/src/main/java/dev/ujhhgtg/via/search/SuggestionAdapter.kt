package dev.ujhhgtg.via.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.skins.setSkinImageResource

/** tb.k0.a with original a1.xml rows and independent select/fill actions. */
internal class SuggestionAdapter(
    private val rows: List<SearchSuggestion>, private val top: Boolean,
    private val select: (SearchSuggestion) -> Unit, private val fill: (String) -> Unit,
    private val longClick: (SearchSuggestion) -> Boolean,
) : RecyclerView.Adapter<SuggestionAdapter.Holder>() {
    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.suggestion_title)
        val url: TextView = view.findViewById(R.id.suggestion_url)
        val icon: ImageView = view.findViewById(R.id.suggestion_icon)
        val fill: ImageView = view.findViewById(R.id.suggestion_fill)
    }
    override fun getItemCount() = rows.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.search_suggestion, parent, false)).also { holder ->
            val font = BrowserPreferences(parent.context).selectedTypeface()
            holder.title.typeface = font; holder.url.typeface = font
            holder.title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, parent.resources.getDimensionPixelSize(R.dimen.search_suggestion_title_size).toFloat())
            holder.url.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, parent.resources.getDimensionPixelSize(R.dimen.search_suggestion_url_size).toFloat())
            holder.fill.setSkinImageResource(R.drawable.search_fill)
            if (!top) holder.fill.rotation = -90f
        }
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        holder.title.text = row.title.let { if (it.length > 256) it.substring(0, 256) + "..." else it }
        holder.url.text = row.url.orEmpty().let { if (it.length > 256) it.substring(0, 256) + "..." else it }
        holder.title.visibility = if (row.title.isEmpty()) View.GONE else View.VISIBLE
        holder.url.visibility = if (row.url.isNullOrEmpty()) View.GONE else View.VISIBLE
        holder.icon.setSkinImageResource(when (row.type) {
            SearchSuggestion.FAVORITE -> R.drawable.search_favorite
            SearchSuggestion.BOOKMARK -> R.drawable.star
            SearchSuggestion.TAB -> R.drawable.search_tab
            SearchSuggestion.HISTORY, SearchSuggestion.QUERY_HISTORY -> R.drawable.clock
            else -> R.drawable.search
        })
        holder.fill.setOnClickListener { if (row.input.isNotEmpty()) fill(row.input) }
        holder.itemView.setOnClickListener { select(row) }
        holder.itemView.setOnLongClickListener { longClick(row) }
    }
}
