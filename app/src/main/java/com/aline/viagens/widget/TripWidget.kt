package com.aline.viagens.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.aline.viagens.MainActivity
import com.aline.viagens.R
import com.aline.viagens.data.Dates
import com.aline.viagens.data.Trip
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Widget da tela inicial: próxima viagem e quantos dias faltam. */
class TripWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        private const val PREFS = "vamoviajar_widget"

        /** Guarda a próxima viagem (a que ainda não terminou e começa primeiro) e atualiza o widget. */
        fun saveNextTrip(context: Context, trips: List<Trip>) {
            val next = trips.filter { !Dates.isPast(it.endDate) && Dates.parse(it.startDate) != null }
                .minByOrNull { it.startDate }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("name", next?.name.orEmpty())
                .putString("destination", next?.destination.orEmpty())
                .putString("start", next?.startDate.orEmpty())
                .putString("end", next?.endDate.orEmpty())
                .apply()
            refresh(context)
        }

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TripWidget::class.java))
            if (ids.isNotEmpty()) ids.forEach { manager.updateAppWidget(it, views(context)) }
        }

        private fun views(context: Context): RemoteViews {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val name = p.getString("name", "").orEmpty()
            val destination = p.getString("destination", "").orEmpty()
            val start = Dates.parse(p.getString("start", "").orEmpty())
            val end = Dates.parse(p.getString("end", "").orEmpty()) ?: start
            val today = LocalDate.now()

            val v = RemoteViews(context.packageName, R.layout.widget_trip)
            if (name.isBlank() || start == null || (end != null && end.isBefore(today))) {
                v.setTextViewText(R.id.widget_count, "")
                v.setTextViewText(R.id.widget_label, "Nenhuma viagem marcada")
                v.setTextViewText(R.id.widget_name, "Toque para planejar a próxima")
            } else {
                val days = ChronoUnit.DAYS.between(today, start)
                when {
                    days > 1 -> { v.setTextViewText(R.id.widget_count, days.toString()); v.setTextViewText(R.id.widget_label, "dias para") }
                    days == 1L -> { v.setTextViewText(R.id.widget_count, "1"); v.setTextViewText(R.id.widget_label, "dia para") }
                    days == 0L -> { v.setTextViewText(R.id.widget_count, "Hoje!"); v.setTextViewText(R.id.widget_label, "começa") }
                    else -> { v.setTextViewText(R.id.widget_count, "🧳"); v.setTextViewText(R.id.widget_label, "acontecendo agora") }
                }
                v.setTextViewText(R.id.widget_name, listOf(name, destination).filter { it.isNotBlank() }.joinToString(" · "))
            }
            val open = PendingIntent.getActivity(
                context, 1, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.widget_root, open)
            return v
        }
    }
}
