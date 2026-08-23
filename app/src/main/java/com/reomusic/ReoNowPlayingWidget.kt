package com.reomusic

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Lightweight local now-playing widget. State is written by MainActivity only. */
class ReoNowPlayingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context, manager, ids)
    }

    companion object {
        private const val PREFS = "reo_widget_state"
        private const val TITLE = "title"
        private const val ARTIST = "artist"

        fun publish(context: Context, title: String, artist: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(TITLE, title).putString(ARTIST, artist).apply()
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ReoNowPlayingWidget::class.java))
            if (ids.isNotEmpty()) updateAll(context, manager, ids)
        }

        private fun updateAll(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val title = prefs.getString(TITLE, "REO Music") ?: "REO Music"
            val artist = prefs.getString(ARTIST, "Tap to open player") ?: "Tap to open player"
            val open = PendingIntent.getActivity(
                context, 8103, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_now_playing)
                views.setTextViewText(R.id.widget_title, title)
                views.setTextViewText(R.id.widget_artist, artist)
                views.setOnClickPendingIntent(R.id.widget_root, open)
                manager.updateAppWidget(id, views)
            }
        }
    }
}
