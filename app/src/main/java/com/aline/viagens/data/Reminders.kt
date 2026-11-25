package com.aline.viagens.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aline.viagens.MainActivity
import com.aline.viagens.R
import com.aline.viagens.widget.TripWidget
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Um aviso que vai aparecer no celular num horário. */
data class Reminder(val key: String, val atMillis: Long, val title: String, val text: String)

object Reminders {
    private const val TAG = "VamoViajar"
    private const val CHANNEL = "lembretes"
    private const val WORK_TAG = "lembrete"
    private const val PREFS = "vamoviajar"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("reminders", true)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("reminders", on).apply()
        if (on) syncNow(context) else WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Liga a atualização automática (a cada 12 horas) e faz uma agora. */
    fun start(context: Context) {
        val periodic = PeriodicWorkRequestBuilder<ReminderSyncWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork("lembretes-sync", ExistingPeriodicWorkPolicy.KEEP, periodic)
        syncNow(context)
    }

    /** Recalcula os avisos (por exemplo, depois que alguém muda uma reserva). */
    fun syncNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "lembretes-sync-agora", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderSyncWorker>().build(),
        )
    }

    private fun millis(day: LocalDate, time: LocalTime): Long =
        LocalDateTime.of(day, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun parseTime(t: String): LocalTime? = runCatching { LocalTime.parse(t) }.getOrNull()

    /** Os avisos de uma reserva. */
    fun forBooking(tripName: String, b: Booking): List<Reminder> {
        val day = Dates.parse(b.startDate) ?: return emptyList()
        val time = parseTime(b.startTime)
        val name = b.title.ifBlank { b.bookingType.label }
        val at = time?.let { " às $it" }.orEmpty()
        val list = mutableListOf<Reminder>()
        fun add(kind: String, whenMs: Long, title: String, text: String) {
            list += Reminder("${b.id}-$kind", whenMs, title, text)
        }
        when (b.bookingType) {
            BookingType.PASSAGEM -> {
                val what = listOf(name, b.number).filter { it.isNotBlank() }.joinToString(" ")
                val route = listOf(b.from, b.to).filter { it.isNotBlank() }.joinToString(" → ")
                val start = millis(day, time ?: LocalTime.of(9, 0))
                add(
                    "vespera", start - TimeUnit.HOURS.toMillis(24), "Amanhã: $what$at",
                    (if (route.isNotBlank()) "$route. " else "") +
                        if (b.mode == "aviao") "Já fez o check-in online?" else "Confira os documentos e o horário.",
                )
                if (time != null) add("3h", start - TimeUnit.HOURS.toMillis(3), "Daqui a 3 horas: $what", route.ifBlank { tripName })
            }
            BookingType.HOSPEDAGEM -> {
                val whenMs = if (time != null) millis(day, time) - TimeUnit.HOURS.toMillis(3) else millis(day, LocalTime.of(9, 0))
                add("checkin", whenMs, "Hoje tem check-in: $name$at", b.address.ifBlank { tripName })
            }
            BookingType.TRANSPORTE -> {
                val whenMs = if (time != null) millis(day, time) - TimeUnit.HOURS.toMillis(2) else millis(day, LocalTime.of(9, 0))
                add("retirada", whenMs, "Hoje: retirada de $name$at", b.from.ifBlank { tripName })
            }
            BookingType.INGRESSO -> {
                val whenMs = if (time != null) millis(day, time) - TimeUnit.HOURS.toMillis(2) else millis(day, LocalTime.of(9, 0))
                add("ingresso", whenMs, "Hoje: $name$at", listOf(b.address, b.code.takeIf { it.isNotBlank() }?.let { "Código $it" }.orEmpty())
                    .filter { it.isNotBlank() }.joinToString(" · ").ifBlank { tripName })
            }
        }
        return list
    }

    /** Agenda os avisos dos próximos 30 dias, trocando os antigos. */
    fun schedule(context: Context, reminders: List<Reminder>) {
        val wm = WorkManager.getInstance(context)
        wm.cancelAllWorkByTag(WORK_TAG)
        if (!enabled(context)) return
        val now = System.currentTimeMillis()
        val limit = now + TimeUnit.DAYS.toMillis(30)
        reminders.filter { it.atMillis in (now + 60_000)..limit }.forEach { r ->
            val req = OneTimeWorkRequestBuilder<NotifyWorker>()
                .setInitialDelay(r.atMillis - now, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("title" to r.title, "text" to r.text, "id" to r.key.hashCode()))
                .addTag(WORK_TAG)
                .build()
            wm.enqueueUniqueWork("lembrete-${r.key}", ExistingWorkPolicy.REPLACE, req)
        }
    }

    fun show(context: Context, id: Int, title: String, text: String) {
        if (!enabled(context) || !hasPermission(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Lembretes das reservas", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (e: SecurityException) {
            Log.w(TAG, "Sem permissão para notificar", e)
        }
    }
}

/** Busca as reservas de todas as viagens da pessoa e agenda os avisos. Também atualiza o widget. */
class ReminderSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        return try {
            val db = FirebaseFirestore.getInstance()
            val trips = db.collection("trips").whereArrayContains("members", uid).get().await()
                .documents.mapNotNull { it.toTrip() }
            TripWidget.saveNextTrip(applicationContext, trips)
            val reminders = mutableListOf<Reminder>()
            for (t in trips) {
                if (Dates.isPast(t.endDate)) continue
                val bookings = db.collection("trips").document(t.id).collection("bookings").get().await()
                    .documents.mapNotNull { it.toBooking() }
                bookings.forEach { reminders += Reminders.forBooking(t.name, it) }
            }
            Reminders.schedule(applicationContext, reminders)
            Result.success()
        } catch (e: Exception) {
            Log.e("VamoViajar", "Erro ao agendar lembretes", e)
            Result.retry()
        }
    }
}

class NotifyWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        Reminders.show(
            applicationContext,
            inputData.getInt("id", 1),
            inputData.getString("title").orEmpty(),
            inputData.getString("text").orEmpty(),
        )
        return Result.success()
    }
}
