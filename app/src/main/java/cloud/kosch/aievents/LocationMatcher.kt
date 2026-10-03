package cloud.kosch.aievents

object LocationMatcher {
    fun textMatches(value: String, selectedPlace: String): Boolean {
        if (value.isBlank() || selectedPlace.isBlank()) return false
        val haystack = normalize(value)
        val primary = normalize(selectedPlace.substringBefore(","))
        if (primary.length >= 2 && primary in haystack) return true

        val primaryParts = primary.split(" ").filter { it.isNotBlank() }
        val acronym = primaryParts.joinToString("") { it.take(1) }
        if (acronym.length in 2..5 &&
            Regex("(^|\\s)" + Regex.escape(acronym) + "($|\\s)").containsMatchIn(haystack)
        ) return true

        val tokens = primaryParts
            .filter { it.length >= 3 && it !in setOf("city", "state", "county", "region") }

        return tokens.size >= 2 && tokens.all { it in haystack }
    }

    fun eventTextMatches(event: EventItem, selectedPlace: String): Boolean =
        textMatches(event.locality, selectedPlace) ||
            textMatches(event.venue, selectedPlace) ||
            textMatches(event.title, selectedPlace)

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
