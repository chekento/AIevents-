package cloud.kosch.aievents

object EventMerger {
    fun merge(events: List<EventItem>): List<EventItem> {
        val groups = linkedMapOf<String, MutableList<EventItem>>()
        for (event in events) {
            groups.getOrPut(mergeKey(event)) { mutableListOf() }.add(event)
        }
        return groups.values.map { group ->
            val best = group.maxByOrNull { richness(it) } ?: group.first()
            val official = group.firstOrNull { it.officialProvider }
            best.copy(
                officialProvider = group.any { it.officialProvider },
                providerName = official?.providerName ?: best.providerName,
                confidence = group.maxOf { it.confidence },
                sourceCount = group.map { it.sourceName.lowercase() }.distinct().size.coerceAtLeast(1)
            )
        }
    }

    private fun mergeKey(event: EventItem): String {
        if (event.stableKey.startsWith("url|")) return event.stableKey

        val title = normalize(event.title)
        val date = event.start?.toString()?.take(16).orEmpty()
        val place = normalize(event.locality).take(60)
        return "fuzzy|" + title.take(90) + "|" + date + "|" + place
    }

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun richness(event: EventItem): Int =
        event.confidence +
            minOf(20, event.description.length / 60) +
            (if (event.geo != null) 10 else 0) +
            (if (event.locality.isNotBlank()) 8 else 0) +
            (if (event.officialProvider) 12 else 0)
}
