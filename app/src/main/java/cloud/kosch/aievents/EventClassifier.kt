package cloud.kosch.aievents

object EventClassifier {
    fun matchesType(event: EventItem, type: EventType): Boolean {
        if (type == EventType.ALL) return true
        val h = (event.title + " " + event.description).lowercase()
        return when (type) {
            EventType.CONFERENCE -> listOf("conference", "summit", "congress", "forum").any { it in h }
            EventType.MEETUP -> listOf("meetup", "community", "stammtisch", "user group").any { it in h }
            EventType.WORKSHOP -> listOf("workshop", "lab", "hands-on", "bootcamp").any { it in h }
            EventType.HACKATHON -> listOf("hackathon", "hack day", "build day").any { it in h }
            EventType.ALL -> true
        }
    }
}
