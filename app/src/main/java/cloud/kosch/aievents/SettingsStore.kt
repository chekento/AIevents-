package cloud.kosch.aievents

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.time.Instant

private val Context.dataStore by preferencesDataStore(name = "aievents_settings")

data class AppSettings(
    val place: String = "Ahrensburg, Schleswig-Holstein, Germany",
    val radiusKm: Int = 45,
    val language: String = "de",
    val includeOnline: Boolean = true,
    val priceMode: PriceMode = PriceMode.ANY,
    val minConfidence: Int = 35,
    val futureDays: Int = 365,
    val includeUnverifiedDates: Boolean = false,
    val category: EventCategory = EventCategory.ALL,
    val sortMode: SortMode = SortMode.DATE,
    val keywords: String = "",
    val compactCards: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val notificationHours: Int = 12,
    val notifyOnlyNew: Boolean = true,
    val enabledSources: Set<String> = SourceRegistry.sources.map { it.id }.toSet(),
    val favorites: Set<String> = emptySet(),
    val favoriteEvents: List<EventItem> = emptyList()
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val place = stringPreferencesKey("place")
        val radius = intPreferencesKey("radius")
        val language = stringPreferencesKey("language")
        val online = booleanPreferencesKey("online")
        val priceMode = stringPreferencesKey("price_mode")
        val confidence = intPreferencesKey("confidence")
        val futureDays = intPreferencesKey("future_days")
        val includeUnverifiedDates = booleanPreferencesKey("include_unverified_dates")
        val category = stringPreferencesKey("category")
        val sort = stringPreferencesKey("sort")
        val keywords = stringPreferencesKey("keywords")
        val compact = booleanPreferencesKey("compact")
        val notifications = booleanPreferencesKey("notifications")
        val notificationHours = intPreferencesKey("notification_hours")
        val notifyOnlyNew = booleanPreferencesKey("notify_only_new")
        val sources = stringSetPreferencesKey("sources")
        val favorites = stringSetPreferencesKey("favorites")
        val favoriteEvents = stringSetPreferencesKey("favorite_events")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            place = p[Keys.place] ?: "Ahrensburg, Schleswig-Holstein, Germany",
            radiusKm = p[Keys.radius] ?: 45,
            language = p[Keys.language] ?: "de",
            includeOnline = p[Keys.online] ?: true,
            priceMode = runCatching { PriceMode.valueOf(p[Keys.priceMode] ?: "ANY") }.getOrDefault(PriceMode.ANY),
            minConfidence = p[Keys.confidence] ?: 35,
            futureDays = p[Keys.futureDays] ?: 365,
            includeUnverifiedDates = p[Keys.includeUnverifiedDates] ?: false,
            category = runCatching { EventCategory.valueOf(p[Keys.category] ?: "ALL") }.getOrDefault(EventCategory.ALL),
            sortMode = runCatching { SortMode.valueOf(p[Keys.sort] ?: "DATE") }.getOrDefault(SortMode.DATE),
            keywords = p[Keys.keywords] ?: "",
            compactCards = p[Keys.compact] ?: false,
            notificationsEnabled = p[Keys.notifications] ?: false,
            notificationHours = p[Keys.notificationHours] ?: 12,
            notifyOnlyNew = p[Keys.notifyOnlyNew] ?: true,
            enabledSources = p[Keys.sources] ?: SourceRegistry.sources.map { it.id }.toSet(),
            favorites = p[Keys.favorites] ?: emptySet(),
            favoriteEvents = (p[Keys.favoriteEvents] ?: emptySet()).mapNotNull { raw -> decodeFavorite(raw) }
        )
    }

    suspend fun save(s: AppSettings) {
        context.dataStore.edit { p ->
            p[Keys.place] = s.place
            p[Keys.radius] = s.radiusKm
            p[Keys.language] = s.language
            p[Keys.online] = s.includeOnline
            p[Keys.priceMode] = s.priceMode.name
            p[Keys.confidence] = s.minConfidence
            p[Keys.futureDays] = s.futureDays
            p[Keys.includeUnverifiedDates] = s.includeUnverifiedDates
            p[Keys.category] = s.category.name
            p[Keys.sort] = s.sortMode.name
            p[Keys.keywords] = s.keywords
            p[Keys.compact] = s.compactCards
            p[Keys.notifications] = s.notificationsEnabled
            p[Keys.notificationHours] = s.notificationHours
            p[Keys.notifyOnlyNew] = s.notifyOnlyNew
            p[Keys.sources] = s.enabledSources
            p[Keys.favorites] = s.favorites
            p[Keys.favoriteEvents] = s.favoriteEvents.map { event -> encodeFavorite(event) }.toSet()
        }
    }
    private fun encodeFavorite(event: EventItem): String = JSONObject().apply {
        put("title", event.title)
        put("start", event.start?.toString() ?: "")
        put("end", event.end?.toString() ?: "")
        put("venue", event.venue)
        put("locality", event.locality)
        put("description", event.description)
        put("organizer", event.organizer)
        put("sourceName", event.sourceName)
        put("sourceUrl", event.sourceUrl)
        put("eventUrl", event.eventUrl)
        put("price", event.price)
        put("language", event.language)
        put("online", event.online)
        put("confidence", event.confidence)
        event.geo?.let { geo ->
            put("lat", geo.lat)
            put("lon", geo.lon)
        }
    }.toString()

    private fun decodeFavorite(raw: String): EventItem? = runCatching {
        val o = JSONObject(raw)
        val geo = if (o.has("lat") && o.has("lon")) GeoPoint(o.getDouble("lat"), o.getDouble("lon")) else null
        EventItem(
            title = o.getString("title"),
            start = o.optString("start").takeIf { it.isNotBlank() }?.let(Instant::parse),
            end = o.optString("end").takeIf { it.isNotBlank() }?.let(Instant::parse),
            venue = o.optString("venue"),
            locality = o.optString("locality"),
            description = o.optString("description"),
            organizer = o.optString("organizer"),
            sourceName = o.optString("sourceName"),
            sourceUrl = o.optString("sourceUrl"),
            eventUrl = o.optString("eventUrl"),
            price = o.optString("price"),
            language = o.optString("language"),
            online = o.optBoolean("online"),
            geo = geo,
            distanceKm = null,
            confidence = o.optInt("confidence", 50)
        )
    }.getOrNull()
}
