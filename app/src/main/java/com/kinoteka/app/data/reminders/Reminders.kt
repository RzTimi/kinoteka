package com.kinoteka.app.data.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kinoteka.app.KinotekaApp
import com.kinoteka.app.MainActivity
import com.kinoteka.app.R
import com.kinoteka.app.data.MediaType
import com.kinoteka.app.data.library.WatchStatus
import com.kinoteka.app.i18n.tr
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Включение/выключение фоновой проверки (раз в 12 часов). */
object Reminders {
    private const val WORK = "kinoteka_reminders"
    const val CHANNEL = "reminders"

    fun schedule(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (enabled) {
            createChannel(context)
            wm.enqueueUniquePeriodicWork(
                WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ReminderWorker>(12, TimeUnit.HOURS).build(),
            )
        } else {
            wm.cancelUniqueWork(WORK)
        }
    }

    /** Проверить прямо сейчас (кнопка в настройках). */
    fun checkNow(context: Context) {
        createChannel(context)
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ReminderWorker>().build())
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, tr("Нові серії та прем'єри", "New episodes and premieres", "Новые серии и премьеры"), NotificationManager.IMPORTANCE_DEFAULT)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

/**
 * Проверяет: у сериалов со статусом «Дивлюсь»/«Переглядаю» выходит серия сегодня или завтра,
 * у фильмов из «Хочу подивитись» сегодня премьера. Каждое событие — одно уведомление.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as KinotekaApp).container
        if (!container.ui.reminders.value) return Result.success()
        container.library.loaded.first { it }
        val prefs = applicationContext.getSharedPreferences("reminders", Context.MODE_PRIVATE)
        val notified = prefs.getStringSet("notified", emptySet()).orEmpty().toMutableSet()
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)

        val entries = container.library.entries.value.values.filter { !it.manual }
        val series = entries.filter { it.type == MediaType.TV && (it.status == WatchStatus.WATCHING || it.status == WatchStatus.REWATCHING) }
        val movies = entries.filter { it.type == MediaType.MOVIE && it.status == WatchStatus.WANT }

        for (e in (series + movies).take(60)) {
            val d = runCatching { container.repository.detailsLite(e.type, e.tmdbId) }.getOrNull() ?: continue
            if (e.type == MediaType.TV) {
                val next = d.nextEpisode ?: continue
                val date = runCatching { LocalDate.parse(next.airDate) }.getOrNull() ?: continue
                if (date != today && date != tomorrow) continue
                val key = "tv:${e.tmdbId}:${next.seasonNumber}:${next.episodeNumber}:$date"
                if (key in notified) continue
                notify(
                    e.tmdbId,
                    if (date == today) tr("Сьогодні нова серія: ${e.displayTitle}", "New episode today: ${e.displayTitle}", "Сегодня новая серия: ${e.displayTitle}")
                    else tr("Завтра нова серія: ${e.displayTitle}", "New episode tomorrow: ${e.displayTitle}", "Завтра новая серия: ${e.displayTitle}"),
                    "S${next.seasonNumber}E${next.episodeNumber}" + (next.name?.let { " · «$it»" } ?: ""),
                )
                notified += key
            } else {
                val date = runCatching { LocalDate.parse(d.releaseDate) }.getOrNull() ?: continue
                if (date != today) continue
                val key = "movie:${e.tmdbId}:$date"
                if (key in notified) continue
                notify(e.tmdbId, tr("Сьогодні прем'єра: ${e.displayTitle}", "Premiere today: ${e.displayTitle}", "Сегодня премьера: ${e.displayTitle}"),
                    tr("Фільм зі списку «Хочу подивитись» уже виходить 🎬", "A movie from your “Want to watch” list is out now 🎬", "Фильм из списка «Хочу посмотреть» уже выходит 🎬"))
                notified += key
            }
        }
        prefs.edit().putStringSet("notified", notified.toList().takeLast(300).toSet()).apply()
        return Result.success()
    }

    private fun notify(id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, Reminders.CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(id, notification)
    }
}
