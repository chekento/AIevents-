package cloud.kosch.aievents

import java.time.Instant
import java.net.URI

enum class SortMode { RELEVANCE, DATE, DISTANCE, CONFIDENCE }
enum class PriceMode { ANY, FREE, PAID }
enum class EventType { ALL, CONFERENCE, MEETUP, WORKSHOP, HACKATHON }
enum class EventCategory { ALL, AGENTS, GENAI, ML, DATA, ROBOTICS, BUSINESS, GOVERNANCE, DEVELOPER, RESEARCH, COMMUNITY }

data class SearchConfig(
    val place: String,
    val radiusKm: Int,
    val language: String,
    val includeOnline: Boolean = true,
    val futureDays: Int = 365,
    val includeUnverifiedDates: Boolean = false,
    val priceMode: PriceMode = PriceMode.ANY,
    val eventType: EventType = EventType.ALL,
    val officialOnly: Boolean = false,
    val minConfidence: Int = 0,
    val category: EventCategory = EventCategory.ALL,
    val keywords: String = "",
    val sortMode: SortMode = SortMode.RELEVANCE,
    val center: GeoPoint? = null,
    val enabledSourceIds: Set<String> = SourceRegistry.sources.map { it.id }.toSet()
)

data class GeoPoint(val lat: Double, val lon: Double)

data class EventItem(
    val title: String,
    val start: Instant?,
    val end: Instant?,
    val venue: String,
    val locality: String,
    val description: String,
    val organizer: String,
    val sourceName: String,
    val sourceUrl: String,
    val eventUrl: String,
    val price: String,
    val language: String,
    val online: Boolean,
    val geo: GeoPoint?,
    val distanceKm: Double?,
    val confidence: Int,
    val officialProvider: Boolean = false,
    val providerName: String = "",
    val relevanceScore: Int = 0,
    val sourceCount: Int = 1,
    val discoveredAt: Instant = Instant.now()
) {
    val stableKey: String
        get() {
            val urlKey = runCatching {
                if (!eventUrl.startsWith("http")) null
                else URI(eventUrl).let { uri ->
                    val host = uri.host?.lowercase()?.removePrefix("www.") ?: return@let null
                    val path = uri.path?.trimEnd('/')?.lowercase().orEmpty()
                    host + path
                }
            }.getOrNull()
            if (!urlKey.isNullOrBlank()) return "url|" + urlKey
            return (title.lowercase().replace(Regex("\\s+"), " ").trim() + "|" +
                (start?.toString() ?: "") + "|" + locality.lowercase()).take(300)
        }

    fun isFree(): Boolean {
        val p = price.lowercase()
        return p.isBlank() || p == "free" || p == "kostenlos" || p == "0" || p.startsWith("0 ")
    }
}

data class SearchSnapshot(
    val config: SearchConfig,
    val events: List<EventItem>,
    val searchedSources: Int,
    val discoveredPages: Int,
    val warnings: List<String>,
    val updatedAt: Instant = Instant.now(),
    val indexedEvents: Int = 0,
    val indexGeneratedAt: Instant? = null,
    val phase: String = "live",
    val featuredOfficialEvents: List<EventItem> = emptyList()
)
