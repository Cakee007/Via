package dev.ujhhgtg.via.platform

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.common.ViaIntents

/** The original widgets launch Shell's SEARCH action from the entire search bar. */
open class SearchWidgetProvider : AppWidgetProvider() {
    protected open val layout: Int get() = R.layout.widget_search
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        super.onUpdate(context, manager, ids)
        val intent = Intent(context, Shell::class.java).setAction(ViaIntents.ACTION_SEARCH).setPackage(context.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val action = PendingIntent.getActivity(context, 0, intent, flags)
        ids.filter { it != 0 }.forEach { id ->
            manager.updateAppWidget(id, RemoteViews(context.packageName, layout).apply { setOnClickPendingIntent(R.id.widget_search_bar, action) })
        }
    }
}

class TransparentSearchWidgetProvider : SearchWidgetProvider() {
    override val layout: Int get() = R.layout.widget_search_transparent
}
