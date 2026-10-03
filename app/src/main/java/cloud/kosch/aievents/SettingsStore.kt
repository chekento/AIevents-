package cloud.kosch.aievents

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "aievents_settings")

data class AppSettings(
    val place: String = "Ahrensburg, Schleswig-Holstein, Germany",
    val radiusKm: Int = 45,
    val language: String = "de",
    val includeOnline: Boolean = true,
    val freeOnly: Boolean = false,
    val minConfidence: Int = 35,
    val futureDays: Int = 365,
    val category: EventCategory = EventCategory.ALL,
    val sortMode: SortMode = SortMode.DATE,
    val keywords: String = "",
    val compactCards: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val notificationHours: Int = 12,
    val notifyOnlyNew: Boolean = true,
    val enabledSources: Set<String> = SourceRegistry.sources.map { it.id }.toSet(),
    val favorites: Set<String> = emptySet()
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val place = stringPreferencesKey("place")
        val radius = intPreferencesKey("radius")
        val language = stringPreferencesKey("language")
        val online = booleanPreferencesKey("online")
        val free = booleanPreferencesKey("free")
        val confidence = intPreferencesKey("confidence")
        val futureDays = intPreferencesKey("future_days")
        val category = stringPreferencesKey("category")
        val sort = stringPreferencesKey("sort")
        val keywords = stringPreferencesKey("keywords")
        val compact = booleanPreferencesKey("compact")
        val notifications = booleanPreferencesKey("notifications")
        val notificationHours = intPreferencesKey("notification_hours")
        val notifyOnlyNew = booleanPreferencesKey("notify_only_new")
        val sources = stringSetPreferencesKey("sources")
        val favorites = stringSetPreferencesKey("favorites")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            place = p[Keys.place] ?: "Ahrensburg, Schleswig-Holstein, Germany",
            radiusKm = p[Keys.radius] ?: 45,
            language = p[Keys.language] ?: "de",
            includeOnline = p[Keys.online] ?: true,
            freeOnly = p[Keys.free] ?: false,
            minConfidence = p[Keys.confidence] ?: 35,
            futureDays = p[Keys.futureDays] ?: 365,
            category = runCatching { EventCategory.valueOf(p[Keys.category] ?: "ALL") }.getOrDefault(EventCategory.ALL),
            sortMode = runCatching { SortMode.valueOf(p[Keys.sort] ?: "DATE") }.getOrDefault(SortMode.DATE),
            keywords = p[Keys.keywords] ?: "",
            compactCards = p[Keys.compact] ?: false,
            notificationsEnabled = p[Keys.notifications] ?: false,
            notificationHours = p[Keys.notificationHours] ?: 12,
            notifyOnlyNew = p[Keys.notifyOnlyNew] ?: true,
            enabledSources = p[Keys.sources] ?: SourceRegistry.sources.map { it.id }.toSet(),
            favorites = p[Keys.favorites] ?: emptySet()
        )
    }

    suspend fun save(s: AppSettings) {
        context.dataStore.edit { p ->
            p[Keys.place] = s.place
            p[Keys.radius] = s.radiusKm
            p[Keys.language] = s.language
            p[Keys.online] = s.includeOnline
            p[Keys.free] = s.freeOnly
            p[Keys.confidence] = s.minConfidence
            p[Keys.futureDays] = s.futureDays
            p[Keys.category] = s.category.name
            p[Keys.sort] = s.sortMode.name
            p[Keys.keywords] = s.keywords
            p[Keys.compact] = s.compactCards
            p[Keys.notifications] = s.notificationsEnabled
            p[Keys.notificationHours] = s.notificationHours
            p[Keys.notifyOnlyNew] = s.notifyOnlyNew
            p[Keys.sources] = s.enabledSources
            p[Keys.favorites] = s.favorites
        }
    }
}
