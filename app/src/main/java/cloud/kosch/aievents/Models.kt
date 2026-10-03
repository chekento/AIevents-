package cloud.kosch.aievents

import java.time.Instant

enum class SortMode { DATE, DISTANCE, CONFIDENCE }
enum class PriceMode { ANY, FREE, PAID }
enum class EventCategory { ALL, AGENTS, GENAI, ML, DATA, ROBOTICS, BUSINESS, GOVERNANCE, DEVELOPER, RESEARCH, COMMUNITY }

data class SearchConfig(
    val place: String,
    val radiusKm: Int,
    val language: String,
    val includeOnline: Boolean = true,
    val futureDays: Int = 365,
    val priceMode: PriceMode = PriceMode.ANY,
    val minConfidence: Int = 0,
    val category: EventCategory = EventCategory.ALL,
    val keywords: String = "",
    val sortMode: SortMode = SortMode.DATE,
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
    val discoveredAt: Instant = Instant.now()
) {
    val stableKey: String
        get() = (title.lowercase().replace(Regex("\\s+"), " ").trim() + "|" +
            (start?.toString()?.take(10) ?: "") + "|" + locality.lowercase()).take(300)

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
    val updatedAt: Instant = Instant.now()
)
