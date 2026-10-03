package cloud.kosch.aievents

import java.time.Duration
import java.time.Instant
import kotlin.math.max

object EventRanker {
    private val strongSources = setOf(
        "globalai.community", "aitinkerers.org", "meetup.com", "luma.com", "lu.ma",
        "eventbrite.com", "gdg.community.dev", "developers.google.com", "openai.com",
        "developers.openai.com", "events.openai.com", "forum.openai.com",
        "academy.openai.com", "developer.microsoft.com", "microsoft.com",
        "anthropic.com", "ai.meta.com", "aws.amazon.com", "nvidia.com", "huggingface.co"
    )

    fun rank(event: EventItem, config: SearchConfig): EventItem {
        var score = 0
        score += (event.confidence * 0.35).toInt()

        val d = event.distanceKm
        if (d != null) {
            score += when {
                d <= 2 -> 35
                d <= 10 -> 30
                d <= 25 -> 24
                d <= 50 -> 18
                d <= config.radiusKm -> 12
                else -> -50
            }
        }

        if (event.officialProvider) {
            score += 28
            val majorSignals = listOf(
                "devday", "developer day", "dev days", "summit", "gtc", "ai tour",
                "build", "i/o", "reinvent", "re:invent", "connect", "founder house",
                "developer conference", "world tour"
            )
            if (majorSignals.any { it in (event.title + " " + event.description).lowercase() }) {
                score += 15
            }
        }
        if (event.sourceCount > 1) score += minOf(15, (event.sourceCount - 1) * 5)

        val source = event.sourceName.lowercase()
        if (strongSources.any { source == it || source.endsWith("." + it) }) score += 10

        val haystack = (event.title + " " + event.description + " " + event.organizer).lowercase()
        val aiSignals = listOf(
            "artificial intelligence", " ai ", "machine learning", "generative ai", "genai",
            "llm", "rag", "agent", "robot", "computer vision", "governance", "deep learning",
            "gemini", "claude", "chatgpt", "codex", "copilot", "llama"
        )
        score += minOf(18, aiSignals.count { it in (" " + haystack + " ") } * 4)

        val keywords = config.keywords.split(Regex("[,;]+"))
            .map { it.trim().lowercase() }.filter { it.length >= 2 }
        if (keywords.isNotEmpty()) {
            val hits = keywords.count { it in haystack }
            score += hits * 15
            if (hits == 0) score -= 25
        }

        val start = event.start
        if (start != null) {
            val days = max(0, Duration.between(Instant.now(), start).toDays().toInt())
            score += when {
                days <= 7 -> 12
                days <= 30 -> 9
                days <= 90 -> 5
                else -> 1
            }
        }

        val languages = EventSearchLexicon.languagesFor(config.countryCode, config.language)
        score += EventClassifier.participationScore(event, languages)

        if (event.locality.isBlank() && !event.online) score -= 15
        if (event.geo == null && !event.online) score -= 8

        return event.copy(relevanceScore = score.coerceIn(0, 100))
    }

    fun sort(events: List<EventItem>, config: SearchConfig): List<EventItem> {
        val ranked = events.map { rank(it, config) }
        return when (config.sortMode) {
            SortMode.RELEVANCE -> ranked.sortedWith(
                compareByDescending<EventItem> { it.relevanceScore }
                    .thenByDescending { it.officialProvider }
                    .thenBy { it.start ?: Instant.MAX }
            )
            SortMode.DATE -> ranked.sortedBy { it.start ?: Instant.MAX }
            SortMode.DISTANCE -> ranked.sortedWith(
                compareBy<EventItem> { it.distanceKm == null }
                    .thenBy { it.distanceKm ?: Double.MAX_VALUE }
                    .thenByDescending { it.relevanceScore }
                    .thenBy { it.start ?: Instant.MAX }
            )
            SortMode.CONFIDENCE -> ranked.sortedWith(
                compareByDescending<EventItem> { it.confidence }
                    .thenByDescending { it.relevanceScore }
                    .thenBy { it.start ?: Instant.MAX }
            )
        }
    }
}
