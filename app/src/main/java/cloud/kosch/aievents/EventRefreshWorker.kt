package cloud.kosch.aievents

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class EventRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext).flow.first()
        if (!settings.notificationsEnabled) return Result.success()
        return runCatching {
            val snapshot = LiveEventSearch.search(settings.toSearchConfig())
            val prefs = applicationContext.getSharedPreferences("notification_state", Context.MODE_PRIVATE)
            val seen = prefs.getStringSet("seen_keys", emptySet()) ?: emptySet()
            val currentKeys = snapshot.events.map { it.stableKey }.toSet()
            val relevant = if (settings.notifyOnlyNew) snapshot.events.filter { it.stableKey !in seen } else snapshot.events
            if (relevant.isNotEmpty()) notify(relevant.size, relevant.first().title)
            prefs.edit().putStringSet("seen_keys", (seen + currentKeys).toList().takeLast(1000).toSet()).apply()
            Result.success()
        }.getOrElse { Result.retry() }
    }

    private fun notify(count: Int, firstTitle: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "AIevents updates", NotificationManager.IMPORTANCE_DEFAULT)
        )
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("AIevents · " + count + " upcoming")
            .setContentText(firstTitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(firstTitle))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(2001, n)
    }

    companion object {
        const val CHANNEL = "aievents_updates"
        const val WORK = "aievents_periodic_search"

        fun schedule(context: Context, enabled: Boolean, hours: Int) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(WORK)
                return
            }
            val safeHours = hours.coerceIn(1, 168)
            val request = PeriodicWorkRequestBuilder<EventRefreshWorker>(safeHours.toLong(), TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}

fun AppSettings.toSearchConfig() = SearchConfig(
    place = place,
    radiusKm = radiusKm,
    language = language,
    includeOnline = includeOnline,
    futureDays = futureDays,
    priceMode = priceMode,
    minConfidence = minConfidence,
    category = category,
    keywords = keywords,
    sortMode = sortMode,
    enabledSourceIds = enabledSources
)
