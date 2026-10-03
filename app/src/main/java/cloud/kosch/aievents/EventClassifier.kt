package cloud.kosch.aievents

object EventClassifier {
    fun matchesType(event: EventItem, type: EventType): Boolean {
        if (type == EventType.ALL) return true
        val h = (event.title + " " + event.description).lowercase()
        return when (type) {
            EventType.CONFERENCE -> listOf("conference", "summit", "congress", "forum", "symposium", "expo").any { it in h }
            EventType.MEETUP -> listOf("meetup", "community", "stammtisch", "user group", "networking", "roundtable").any { it in h }
            EventType.WORKSHOP -> listOf("workshop", "lab", "hands-on", "bootcamp", "masterclass", "training").any { it in h }
            EventType.HACKATHON -> listOf("hackathon", "hack day", "build day", "datathon", "challenge").any { it in h }
            EventType.ALL -> true
        }
    }

    fun hasAiFocus(event: EventItem, languages: List<String>): Boolean {
        val title = normalize(event.title)
        val body = normalize(
            event.title + " " + event.description + " " + event.organizer
        )
        val terms = languages.flatMap(EventSearchLexicon::aiTerms).distinct()

        val titleHits = terms.count { containsTerm(title, it) }
        if (titleHits >= 1) return true

        val bodyHits = terms.count { containsTerm(body, it) }
        val specialistSignals = listOf(
            "llm", "genai", "rag", "mcp", "agentic", "machine learning",
            "deep learning", "computer vision", "mlops", "llmops",
            "prompt engineering", "responsible ai", "ai safety"
        ).count { containsTerm(body, it) }

        return bodyHits >= 2 || specialistSignals >= 1
    }

    fun participationScore(event: EventItem, languages: List<String>): Int {
        val text = normalize(
            event.title + " " + event.description + " " + event.venue
        )
        val participation = languages
            .flatMap(EventSearchLexicon::participationTerms)
            .distinct()
            .count { containsTerm(text, it) }
        val attendance = EventSearchLexicon.attendanceTerms()
            .count { containsTerm(text, it) }

        var score = minOf(12, participation * 2) + minOf(8, attendance * 2)
        if (event.start != null) score += 4
        if (event.eventUrl.startsWith("http")) score += 2
        if (event.online || event.locality.isNotBlank()) score += 2
        return score.coerceAtMost(25)
    }

    private fun containsTerm(text: String, term: String): Boolean {
        val normalized = normalize(term)
        if (normalized.isBlank()) return false
        if (normalized.length <= 3 && normalized.all { it.isLetterOrDigit() }) {
            return Regex("(^|\\s)" + Regex.escape(normalized) + "($|\\s)")
                .containsMatchIn(text)
        }
        return normalized in text
    }

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
