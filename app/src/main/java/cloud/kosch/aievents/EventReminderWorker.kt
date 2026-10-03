package cloud.kosch.aievents

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

class EventReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    override fun doWork(): Result {
        val title = inputData.getString("title") ?: return Result.failure()
        val locality = inputData.getString("locality").orEmpty()
        val provider = inputData.getString("provider").orEmpty()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "AIevents reminders", NotificationManager.IMPORTANCE_HIGH)
        )

        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val subtitle = listOf(provider, locality).filter { it.isNotBlank() }.joinToString(" · ")
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(subtitle.ifBlank { "AI event reminder" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                if (subtitle.isBlank()) "AI event reminder" else subtitle
            ))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(("reminder|" + title).hashCode(), notification)
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "aievents_event_reminders"

        fun schedule(context: Context, event: EventItem, minutesBefore: Long): Boolean {
            val start = event.start ?: return false
            val target = start.minusSeconds(minutesBefore.coerceAtLeast(0) * 60)
            val delay = Duration.between(Instant.now(), target).toMillis()
            if (delay <= 0) return false

            val data = workDataOf(
                "title" to event.title,
                "locality" to event.locality,
                "provider" to event.providerName
            )

            val request = OneTimeWorkRequestBuilder<EventReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "event-reminder-" + event.stableKey.hashCode(),
                ExistingWorkPolicy.REPLACE,
                request
            )
            return true
        }
    }
}
