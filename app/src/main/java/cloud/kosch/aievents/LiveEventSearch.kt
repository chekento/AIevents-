package cloud.kosch.aievents

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

object LiveEventSearch {
    private val geoCache = ConcurrentHashMap<String, GeoPoint?>()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private const val UA = "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36 AIevents/0.3"

    suspend fun search(config: SearchConfig): SearchSnapshot = withContext(Dispatchers.IO) {
        val warnings = mutableListOf<String>()
        val center = config.center ?: runCatching { geocode(config.place) }.getOrNull()
        if (center == null) warnings += "Center coordinates unavailable; location matching falls back to verified place text."

        val queries = SourceRegistry.queries(config)
        val links = linkedSetOf<String>()
        val querySemaphore = Semaphore(4)
        coroutineScope {
            queries.map { query ->
                async {
                    querySemaphore.withPermit {
                        runCatching { discover(query) }
                            .onSuccess { found -> synchronized(links) { links += found } }
                            .onFailure { error -> synchronized(warnings) { warnings += "A discovery query failed: " + (error.message ?: "network error") } }
                    }
                }
            }.awaitAll()
        }

        val candidateLinks = links
            .filter { it.startsWith("https://") }
            .filterNot { it.contains("duckduckgo.com") }
            .take(200)

        val semaphore = Semaphore(6)
        val events = coroutineScope {
            candidateLinks.map { url ->
                async {
                    semaphore.withPermit {
                        runCatching { extractEvents(url) }.getOrDefault(emptyList())
                    }
                }
            }.awaitAll().flatten()
        }

        val zone = ZoneId.systemDefault()
        val now = Instant.now()

        val geoEnriched = enrichMissingGeo(events)
            .map(OfficialProviders::enrich)

        val dated = geoEnriched.filter { event ->
            EventDateRules.isVisible(
                event = event,
                now = now,
                zone = zone,
                futureDays = config.futureDays,
                includeUnverifiedDates = config.includeUnverifiedDates
            )
        }

        val featuredOfficial = EventMerger.merge(
            dated.filter { it.officialProvider }
        ).map { event ->
            val d = if (center != null && event.geo != null) distanceKm(center, event.geo) else null
            EventRanker.rank(event.copy(distanceKm = d), config)
        }.sortedWith(
            compareByDescending<EventItem> { it.relevanceScore }
                .thenBy { it.start ?: Instant.MAX }
        ).take(12)

        val localCandidates = dated.map { event ->
            val d = if (center != null && event.geo != null) distanceKm(center, event.geo) else null
            event.copy(distanceKm = d)
        }.filter { config.includeOnline || !it.online }
            .filter {
                when (config.priceMode) {
                    PriceMode.ANY -> true
                    PriceMode.FREE -> it.isFree()
                    PriceMode.PAID -> !it.isFree()
                }
            }
            .filter { it.confidence >= config.minConfidence }
            .filter { !config.officialOnly || it.officialProvider }
            .filter { EventClassifier.matchesType(it, config.eventType) }
            .filter { event ->
                when {
                    center != null && event.geo != null ->
                        event.distanceKm != null && event.distanceKm <= config.radiusKm + 0.5
                    event.online ->
                        LocationMatcher.eventTextMatches(event, config.place)
                    else ->
                        textualPlaceMatch(event, config.place)
                }
            }

        val filtered = EventRanker.sort(
            EventMerger.merge(localCandidates),
            config
        )

        SearchSnapshot(
            config = config,
            events = filtered,
            searchedSources = queries.size,
            discoveredPages = candidateLinks.size,
            warnings = warnings.distinct(),
            featuredOfficialEvents = featuredOfficial
        )
    }

    suspend fun searchProvider(
        provider: ProviderEntry,
        language: String = "en"
    ): List<EventItem> = withContext(Dispatchers.IO) {
        val year = Year.now().value
        val domainQueries = provider.domains.take(3).map { domain ->
            "site:" + domain + " \"" + provider.name +
                "\" event webinar conference meetup workshop livestream " + year
        }
        val socialQueries = listOf(
            "site:linkedin.com/events \"" + provider.name + "\" AI event " + year,
            "site:x.com \"" + provider.name + "\" event webinar " + year,
            "site:youtube.com \"" + provider.name + "\" livestream event " + year
        )
        val broadQueries = listOf(
            "\"" + provider.name + "\" AI event webinar conference meetup " + year,
            "\"" + provider.name + "\" online event livestream workshop " + year
        )
        val links = linkedSetOf<String>()
        val semaphore = Semaphore(4)
        coroutineScope {
            (domainQueries + socialQueries + broadQueries).map { query ->
                async {
                    semaphore.withPermit {
                        runCatching { discover(query) }
                            .onSuccess { found -> synchronized(links) { links += found } }
                    }
                }
            }.awaitAll()
        }

        val events = coroutineScope {
            links.take(80).map { url ->
                async {
                    semaphore.withPermit {
                        runCatching { extractEvents(url) }.getOrDefault(emptyList())
                    }
                }
            }.awaitAll().flatten()
        }

        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val enriched = EventMerger.merge(
            events.map { ProviderCatalog.enrich(OfficialProviders.enrich(it)) }
                .filter { event ->
                    event.providerName.equals(provider.name, ignoreCase = true) ||
                        ProviderCatalog.detect(event)?.id == provider.id
                }
                .filter {
                    EventDateRules.isVisible(
                        it, now, zone, 730, false
                    )
                }
        )
        EventRanker.sort(
            enriched,
            SearchConfig(
                place = "",
                radiusKm = 500,
                language = language,
                futureDays = 730,
                includeOnline = true,
                sortMode = SortMode.RELEVANCE
            )
        )
    }

    private fun discover(query: String): List<String> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val found = linkedSetOf<String>()

        fun add(url: String?, label: String = "") {
            if (url.isNullOrBlank() || !url.startsWith("https://")) return
            val signal = (url + " " + label).lowercase()
            if (isLikelyEventPage(url) || listOf(
                    "event", "meetup", "conference", "summit", "workshop", "hackathon",
                    "stammtisch", "community", "artificial intelligence", " ai ", " ki "
                ).any { it in signal }
            ) found += url
        }

        // DuckDuckGo is fast when available, but may return 403 on some networks.
        runCatching {
            val url = "https://html.duckduckgo.com/html/?q=" + encoded
            val doc = Jsoup.parse(get(url), url)
            doc.select("a.result__a, a[data-testid=result-title-a]").forEach { a ->
                add(normalizeDdgUrl(a.attr("href")), a.text())
            }
        }

        // Bing is queried independently so one search engine cannot dominate the result set.
        runCatching {
            val url = "https://www.bing.com/search?q=" + encoded + "&count=20"
            val doc = Jsoup.parse(get(url), url)
            doc.select("li.b_algo h2 a, a.tilk").forEach { a ->
                add(a.absUrl("href").ifBlank { a.attr("href") }, a.text() + " " + a.parent()?.parent()?.text().orEmpty())
            }
        }

        // Google joins broad searches; for site-specific searches it is used when coverage is still thin.
        if (!query.trimStart().startsWith("site:", ignoreCase = true) || found.size < 10) runCatching {
            val url = "https://www.google.com/search?q=" + encoded + "&num=20"
            val doc = Jsoup.parse(get(url), url)
            doc.select("div.yuRUbf a, a[jsname=UWckNb]").forEach { a ->
                add(a.absUrl("href").ifBlank { a.attr("href") }, a.text())
            }
        }

        return found.take(24)
    }

    private fun normalizeDdgUrl(href: String): String? {
        if (href.isBlank()) return null
        val absolute = when {
            href.startsWith("//") -> "https:" + href
            href.startsWith("/") -> "https://duckduckgo.com" + href
            else -> href
        }
        if (!absolute.contains("duckduckgo.com/l/")) return absolute.takeIf { it.startsWith("http") }
        return runCatching {
            val query = URI(absolute).rawQuery ?: return@runCatching null
            query.split("&").mapNotNull {
                val pair = it.split("=", limit = 2)
                if (pair.size == 2 && pair[0] == "uddg") URLDecoder.decode(pair[1], StandardCharsets.UTF_8.toString()) else null
            }.firstOrNull()
        }.getOrNull()
    }

    private fun isLikelyEventPage(url: String): Boolean {
        val u = url.lowercase()
        return listOf("meetup.", "luma.", "lu.ma", "eventbrite.", "partiful.", "bevy.", "sched.", "splashthat.", "allevents.", "globalai.", "aitinkerers.", "mlops.", "sessionize.", "pretalx.", "gdg.", "reactor.", "huggingface.", "/event", "/events", "conference", "summit", "meetup", "stammtisch", "workshop", "hackathon", "calendar", "community")
            .any { it in u }
    }

    private fun extractEvents(url: String): List<EventItem> {
        val doc = Jsoup.parse(get(url), url)
        val structured = mutableListOf<EventItem>()
        for (script in doc.select("script[type=application/ld+json]")) {
            val raw = script.data().ifBlank { script.html() }.trim()
            if (raw.isBlank()) continue
            runCatching {
                val node: Any = if (raw.startsWith("[")) JSONArray(raw) else JSONObject(raw)
                collectEventObjects(node).forEach { obj ->
                    parseEventObject(obj, doc, url)?.let { structured += it }
                }
            }
        }
        if (structured.isNotEmpty()) return structured.distinctBy { it.stableKey }
        return heuristicEvent(doc, url)?.let { listOf(it) } ?: emptyList()
    }

    private fun collectEventObjects(node: Any?): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        when (node) {
            is JSONObject -> {
                val type = node.opt("@type")
                val types = when (type) {
                    is JSONArray -> (0 until type.length()).map { type.optString(it) }
                    else -> listOf(type?.toString().orEmpty())
                }
                if (types.any { it.equals("Event", true) || it.endsWith("Event", true) }) out += node
                node.keys().forEach { key ->
                    val child = node.opt(key)
                    if (child is JSONObject || child is JSONArray) out += collectEventObjects(child)
                }
            }
            is JSONArray -> for (i in 0 until node.length()) out += collectEventObjects(node.opt(i))
        }
        return out.distinctBy { it.toString() }
    }

    private fun parseEventObject(obj: JSONObject, doc: Document, sourceUrl: String): EventItem? {
        val eventStatus = obj.optString("eventStatus")
        if (eventStatus.contains("EventCancelled", ignoreCase = true) ||
            eventStatus.contains("EventCanceled", ignoreCase = true)
        ) return null

        val title = obj.optString("name").ifBlank { meta(doc, "og:title") }.trim()
        if (title.length < 3) return null

        val start = parseInstant(obj.optString("startDate"))
        val end = parseInstant(obj.optString("endDate"))
        val location = obj.opt("location")
        val venue = when (location) {
            is JSONObject -> location.optString("name")
            is String -> location
            else -> ""
        }.trim()
        val address = if (location is JSONObject) addressText(location.opt("address")) else ""
        val locality = listOf(venue, address).filter { it.isNotBlank() }.distinct().joinToString(", ")
        val geo = if (location is JSONObject) parseGeo(location.optJSONObject("geo")) else null

        val organizerValue = obj.opt("organizer")
        val organizer = when (organizerValue) {
            is JSONObject -> organizerValue.optString("name")
            is JSONArray -> (0 until organizerValue.length()).mapNotNull {
                organizerValue.optJSONObject(it)?.optString("name")?.takeIf { name -> name.isNotBlank() }
            }.joinToString(", ")
            is String -> organizerValue
            else -> ""
        }

        val description = cleanText(
            obj.optString("description").ifBlank {
                meta(doc, "description").ifBlank { meta(doc, "og:description") }
            }
        ).take(1200)

        val eventUrl = obj.optString("url").takeIf { it.startsWith("http") } ?: sourceUrl
        val mode = obj.optString("eventAttendanceMode")
        val online = mode.contains("Online", true) || locality.contains("Online", true) ||
            (obj.opt("location")?.toString()?.contains("VirtualLocation", true) == true)
        val price = priceText(obj.opt("offers"))
        val language = obj.optString("inLanguage")
        val imageUrl = imageText(obj.opt("image")).ifBlank { meta(doc, "og:image") }
        val domain = runCatching { URI(sourceUrl).host.removePrefix("www.") }.getOrDefault("web")

        var confidence = 55
        if (start != null) confidence += 20
        if (locality.isNotBlank() || online) confidence += 10
        if (organizer.isNotBlank()) confidence += 5
        if (geo != null) confidence += 5
        if (price.isNotBlank()) confidence += 5

        return EventItem(
            title, start, end, venue, locality, description, organizer, domain,
            sourceUrl, eventUrl, price, language, online, geo, null, confidence.coerceAtMost(100)
        ).copy(imageUrl = imageUrl)
    }

    private fun heuristicEvent(doc: Document, url: String): EventItem? {
        val title = meta(doc, "og:title").ifBlank { doc.title() }.trim()
        if (title.length < 4) return null
        val haystack = (title + " " + meta(doc, "description") + " " + url).lowercase()
        val signals = listOf("event", "meetup", "conference", "summit", "workshop", "hackathon", "ai ", "artificial intelligence")
            .count { it in haystack }
        if (signals < 2) return null
        val startRaw = doc.selectFirst("meta[itemprop=startDate]")?.attr("content")
            ?: doc.selectFirst("time[datetime]")?.attr("datetime")
        val start = parseInstant(startRaw.orEmpty())
        val domain = runCatching { URI(url).host.removePrefix("www.") }.getOrDefault("web")
        return EventItem(
            title = title,
            start = start,
            end = null,
            venue = "",
            locality = "",
            description = cleanText(meta(doc, "description").ifBlank { meta(doc, "og:description") }).take(900),
            organizer = "",
            sourceName = domain,
            sourceUrl = url,
            eventUrl = url,
            price = "",
            language = doc.selectFirst("html")?.attr("lang").orEmpty(),
            online = haystack.contains("online event") || haystack.contains("virtual event"),
            geo = null,
            distanceKm = null,
            confidence = if (start != null) 55 else 35,
            imageUrl = meta(doc, "og:image")
        )
    }

    private fun addressText(value: Any?): String = when (value) {
        is String -> value
        is JSONObject -> listOf(
            value.optString("streetAddress"),
            value.optString("postalCode"),
            value.optString("addressLocality"),
            value.optString("addressRegion"),
            value.optString("addressCountry")
        ).filter { it.isNotBlank() }.distinct().joinToString(", ")
        else -> ""
    }

    private fun parseGeo(obj: JSONObject?): GeoPoint? {
        if (obj == null) return null
        val lat = obj.optDouble("latitude", Double.NaN)
        val lon = obj.optDouble("longitude", Double.NaN)
        return if (lat.isFinite() && lon.isFinite()) GeoPoint(lat, lon) else null
    }

    private fun imageText(value: Any?): String = when (value) {
        is String -> value.takeIf { it.startsWith("http") }.orEmpty()
        is JSONObject -> value.optString("url").takeIf { it.startsWith("http") }.orEmpty()
        is JSONArray -> {
            if (value.length() == 0) "" else imageText(value.opt(0))
        }
        else -> ""
    }

    private fun priceText(value: Any?): String {
        val offer = when (value) {
            is JSONObject -> value
            is JSONArray -> value.optJSONObject(0)
            else -> null
        } ?: return ""
        val price = offer.optString("price")
        val currency = offer.optString("priceCurrency")
        return when {
            price.isBlank() -> ""
            price == "0" || price == "0.0" -> "Free"
            currency.isBlank() -> price
            else -> price + " " + currency
        }
    }

    private fun meta(doc: Document, key: String): String =
        doc.selectFirst("meta[property=$key]")?.attr("content")
            ?: doc.selectFirst("meta[name=$key]")?.attr("content")
            ?: ""

    private fun cleanText(value: String): String =
        Jsoup.parse(value).text().replace(Regex("\\s+"), " ").trim()

    private fun parseInstant(raw: String): Instant? {
        val s = raw.trim()
        if (s.isBlank()) return null
        val parsers = listOf<() -> Instant?>(
            { Instant.parse(s) },
            { OffsetDateTime.parse(s).toInstant() },
            { ZonedDateTime.parse(s).toInstant() },
            { LocalDateTime.parse(s).atZone(ZoneId.systemDefault()).toInstant() },
            { LocalDate.parse(s).atStartOfDay(ZoneId.systemDefault()).toInstant() },
            {
                LocalDate.parse(s.take(10), DateTimeFormatter.ISO_LOCAL_DATE)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant()
            }
        )
        return parsers.firstNotNullOfOrNull { runCatching { it() }.getOrNull() }
    }

    private suspend fun enrichMissingGeo(events: List<EventItem>): List<EventItem> {
        val candidates = events.filter { it.geo == null && it.locality.isNotBlank() }
            .map { it.locality.trim() }.distinct().take(16)
        for (locality in candidates) {
            if (!geoCache.containsKey(locality)) {
                geoCache[locality] = runCatching {
                    LocationAutocomplete.search(locality, "en", 1).firstOrNull()?.point
                        ?: geocode(locality)
                }.getOrNull()
                delay(120)
            }
        }
        return events.map { event ->
            if (event.geo != null || event.locality.isBlank()) event
            else event.copy(geo = geoCache[event.locality.trim()])
        }
    }

    private fun geocode(place: String): GeoPoint? {
        val q = URLEncoder.encode(place, StandardCharsets.UTF_8.toString())
        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q=" + q
        val arr = JSONArray(get(url, "AIevents/0.3 github.com/chekento/AIevents-"))
        val obj = arr.optJSONObject(0) ?: return null
        return GeoPoint(obj.getString("lat").toDouble(), obj.getString("lon").toDouble())
    }

    private fun get(url: String, userAgent: String = UA): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept-Language", Locale.getDefault().toLanguageTag())
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP " + response.code)
            return response.body?.string() ?: ""
        }
    }

    private fun textualPlaceMatch(event: EventItem, place: String): Boolean {
        val primary = place.substringBefore(",").trim().lowercase()
        if (primary.length < 2) return false
        val haystack = listOf(event.locality, event.venue, event.title)
            .joinToString(" ")
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        val normalizedPrimary = primary.replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        if (normalizedPrimary in haystack) return true

        val tokens = normalizedPrimary.split(" ")
            .filter { it.length >= 3 && it !in setOf("city", "state", "county", "region") }
        return tokens.isNotEmpty() && tokens.all { it in haystack }
    }

    private fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
        val earthRadius = 6371.0088
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val la1 = Math.toRadians(a.lat)
        val la2 = Math.toRadians(b.lat)
        val h = sin(dLat / 2).pow(2) + cos(la1) * cos(la2) * sin(dLon / 2).pow(2)
        return 2 * earthRadius * asin(sqrt(h))
    }
}
