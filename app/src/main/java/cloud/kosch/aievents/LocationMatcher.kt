package cloud.kosch.aievents

object LocationMatcher {
    private val commonAliases = mapOf(
        "new york" to listOf("nyc", "new york city"),
        "new york city" to listOf("nyc", "new york"),
        "san francisco" to listOf("sf"),
        "los angeles" to listOf("la"),
        "washington" to listOf("dc", "washington dc"),
        "washington dc" to listOf("dc", "washington"),
        "hong kong" to listOf("hk"),
        "kuala lumpur" to listOf("kl")
    )

    fun searchAliases(selectedPlace: String): List<String> {
        val primary = normalize(selectedPlace.substringBefore(","))
        val aliases = commonAliases[primary].orEmpty()
        val acronym = primary.split(" ").filter { it.isNotBlank() }.joinToString("") { it.take(1) }
        return (listOf(primary) + aliases + listOf(acronym).filter { it.length in 2..5 })
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun textMatches(value: String, selectedPlace: String): Boolean {
        if (value.isBlank() || selectedPlace.isBlank()) return false
        val haystack = normalize(value)
        val primary = normalize(selectedPlace.substringBefore(","))
        if (primary.length >= 2 && primary in haystack) return true

        val primaryParts = primary.split(" ").filter { it.isNotBlank() }
        val aliases = searchAliases(selectedPlace)
        if (aliases.any { alias ->
                alias.length >= 2 &&
                    (alias in haystack ||
                        Regex("(^|\\s)" + Regex.escape(alias) + "($|\\s)").containsMatchIn(haystack))
            }
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
