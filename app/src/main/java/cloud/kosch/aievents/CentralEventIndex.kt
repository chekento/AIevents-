package cloud.kosch.aievents

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.*

object CentralEventIndex {
    private const val INDEX_URL =
        "https://raw.githubusercontent.com/chekento/AIevents-/main/data/events-index.json"
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    @Volatile private var cachedAt: Long = 0
    @Volatile private var cachedEvents: List<IndexedEvent> = emptyList()
    @Volatile private var generatedAt: Instant? = null

    data class IndexedEvent(val event: EventItem, val regions: List<String>)
    data class Result(
        val events: List<EventItem>,
        val indexedCount: Int,
        val generatedAt: Instant?,
        val warning: String? = null,
        val featuredOfficialEvents: List<EventItem> = emptyList()
    )

    suspend fun search(config: SearchConfig): Result = withContext(Dispatchers.IO) {
        val all = runCatching { load() }.getOrElse {
            return@withContext Result(emptyList(), 0, null, "Central index unavailable: " + (it.message ?: "network error"))
        }
        val center = config.center ?: runCatching { geocode(config.place) }.getOrNull()
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val placeTokens = tokens(config.place)

        val filtered = all.mapNotNull { indexed ->
            val event = OfficialProviders.enrich(indexed.event)
            if (!EventDateRules.isVisible(
                    event, now, zone, config.futureDays, config.includeUnverifiedDates
                )
            ) return@mapNotNull null
            if (!config.includeOnline && event.online) return@mapNotNull null
            if (event.confidence < config.minConfidence) return@mapNotNull null
            if (config.officialOnly && !event.officialProvider) return@mapNotNull null
            if (!EventClassifier.matchesType(event, config.eventType)) return@mapNotNull null
            when (config.priceMode) {
                PriceMode.FREE -> if (!event.isFree()) return@mapNotNull null
                PriceMode.PAID -> if (event.isFree()) return@mapNotNull null
                PriceMode.ANY -> Unit
            }

            val d = if (center != null && event.geo != null) distanceKm(center, event.geo) else null
            val regionMatch = indexed.regions.any { region ->
                strictPlaceTextMatch(region, config.place)
            } || strictPlaceTextMatch(event.locality, config.place) ||
                strictPlaceTextMatch(event.title, config.place)

            val geographicallyRelevant = when {
                d != null -> d <= config.radiusKm + 0.5
                event.online -> regionMatch
                else -> regionMatch
            }
            if (!geographicallyRelevant) return@mapNotNull null

            val haystack = (event.title + " " + event.description + " " + event.organizer).lowercase()
            val keywords = config.keywords.split(Regex("[,; ]+"))
                .map { it.trim().lowercase() }.filter { it.length >= 2 }
            if (keywords.isNotEmpty() && keywords.none { it in haystack }) return@mapNotNull null
            if (!matchesCategory(event, config.category)) return@mapNotNull null

            event.copy(distanceKm = d)
        }.distinctBy { it.stableKey }

        val mergedLocal = EventMerger.merge(filtered)
        val sorted = EventRanker.sort(mergedLocal, config)

        val featured = EventMerger.merge(
            all.map { OfficialProviders.enrich(it.event) }
                .filter { it.officialProvider }
                .filter {
                    EventDateRules.isVisible(
                        it, now, zone, config.futureDays, false
                    )
                }
        ).map { EventRanker.rank(it, config) }
            .sortedWith(
                compareByDescending<EventItem> { it.relevanceScore }
                    .thenBy { it.start ?: Instant.MAX }
            )
            .take(12)

        Result(sorted, all.size, generatedAt, featuredOfficialEvents = featured)
    }

    @Synchronized
    private fun load(): List<IndexedEvent> {
        val now = System.currentTimeMillis()
        if (cachedEvents.isNotEmpty() && now - cachedAt < 15 * 60 * 1000) return cachedEvents
        val req = Request.Builder()
            .url(INDEX_URL)
            .header("User-Agent", "AIevents/0.3")
            .cacheControl(okhttp3.CacheControl.FORCE_NETWORK)
            .build()
        val text = http.newCall(req).execute().use { response ->
            if (!response.isSuccessful) error("HTTP " + response.code)
            response.body?.string() ?: error("empty index")
        }
        val root = JSONObject(text)
        generatedAt = root.optString("generated_at")
            .takeIf { it.isNotBlank() && it != "null" }
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }

        val arr = root.optJSONArray("events") ?: JSONArray()
        val parsed = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                parseEvent(o)?.let { event ->
                    val regions = o.optJSONArray("regions")?.let { r ->
                        (0 until r.length()).mapNotNull { idx ->
                            r.optString(idx).takeIf { it.isNotBlank() }
                        }
                    }.orEmpty()
                    add(IndexedEvent(event, regions))
                }
            }
        }
        cachedEvents = parsed
        cachedAt = now
        return parsed
    }

    private fun parseEvent(o: JSONObject): EventItem? {
        val title = o.optString("title").trim()
        if (title.isBlank()) return null
        val start = o.optString("start").takeIf { it.isNotBlank() }?.let {
            runCatching { Instant.parse(it) }.getOrNull()
        } ?: return null
        val end = o.optString("end").takeIf { it.isNotBlank() }?.let {
            runCatching { Instant.parse(it) }.getOrNull()
        }
        val geoObj = o.optJSONObject("geo")
        val geo = if (geoObj != null) {
            val lat = geoObj.optDouble("lat", Double.NaN)
            val lon = geoObj.optDouble("lon", Double.NaN)
            if (lat.isFinite() && lon.isFinite()) GeoPoint(lat, lon) else null
        } else null
        return OfficialProviders.enrich(EventItem(
            title = title,
            start = start,
            end = end,
            venue = o.optString("venue"),
            locality = o.optString("locality"),
            description = o.optString("description"),
            organizer = o.optString("organizer"),
            sourceName = o.optString("sourceName").ifBlank { "central-index" },
            sourceUrl = o.optString("sourceUrl"),
            eventUrl = o.optString("eventUrl"),
            price = o.optString("price"),
            language = o.optString("language"),
            online = o.optBoolean("online"),
            geo = geo,
            distanceKm = null,
            confidence = o.optInt("confidence", 60)
        ))
    }

    private fun geocode(place: String): GeoPoint? {
        val q = URLEncoder.encode(place, StandardCharsets.UTF_8.toString())
        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q=" + q
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "AIevents/0.3 github.com/chekento/AIevents-")
            .header("Accept-Language", Locale.getDefault().toLanguageTag())
            .build()
        val arr = http.newCall(req).execute().use { response ->
            if (!response.isSuccessful) return null
            JSONArray(response.body?.string() ?: "[]")
        }
        val o = arr.optJSONObject(0) ?: return null
        return GeoPoint(o.getString("lat").toDouble(), o.getString("lon").toDouble())
    }

    private fun strictPlaceTextMatch(value: String, place: String): Boolean {
        if (value.isBlank() || place.isBlank()) return false
        val normalizedValue = value.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val primary = place.substringBefore(",").lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (primary.length >= 2 && primary in normalizedValue) return true
        val pieces = primary.split(" ")
            .filter { it.length >= 3 && it !in setOf("city", "state", "county", "region") }
        return pieces.size >= 2 && pieces.all { it in normalizedValue }
    }

    private fun tokens(value: String): Set<String> =
        value.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .split(" ")
            .map { it.trim() }
            .filter { it.length >= 3 && it !in setOf("germany", "deutschland", "united", "states", "kingdom") }
            .toSet()

    private fun matchesCategory(event: EventItem, category: EventCategory): Boolean {
        if (category == EventCategory.ALL) return true
        val h = (event.title + " " + event.description).lowercase()
        val needles = when (category) {
            EventCategory.AGENTS -> listOf("agent", "agentic", "mcp")
            EventCategory.GENAI -> listOf("generative", "genai", "llm", "prompt")
            EventCategory.ML -> listOf("machine learning", " ml ", "deep learning")
            EventCategory.DATA -> listOf("data", "analytics")
            EventCategory.ROBOTICS -> listOf("robot", "physical ai", "computer vision")
            EventCategory.BUSINESS -> listOf("business", "enterprise", "transformation")
            EventCategory.GOVERNANCE -> listOf("governance", "responsible ai", "regulation", "ethics")
            EventCategory.DEVELOPER -> listOf("developer", "coding", "software", "engineering")
            EventCategory.RESEARCH -> listOf("research", "paper", "academic")
            EventCategory.COMMUNITY -> listOf("meetup", "community", "stammtisch", "user group")
            EventCategory.ALL -> emptyList()
        }
        return needles.any { it in h }
    }

    private fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
        val r = 6371.0088
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val la1 = Math.toRadians(a.lat)
        val la2 = Math.toRadians(b.lat)
        val h = sin(dLat / 2).pow(2) + cos(la1) * cos(la2) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }
}
