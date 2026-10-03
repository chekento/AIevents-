package cloud.kosch.aievents

import java.time.Instant
import java.time.ZoneId

object EventDateRules {
    fun isVisible(
        event: EventItem,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        futureDays: Int,
        includeUnverifiedDates: Boolean
    ): Boolean {
        val start = event.start
        val end = event.end
        if (start == null) return includeUnverifiedDates

        val max = now.plusSeconds(futureDays.toLong() * 24L * 60L * 60L)
        if (start.isAfter(max)) return false

        return when {
            end != null -> end.isAfter(now)
            else -> !start.isBefore(now)
        }
    }

    fun isCurrentSavedEvent(
        event: EventItem,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        val start = event.start ?: return false
        val end = event.end
        return if (end != null) end.isAfter(now) else !start.isBefore(now)
    }
}
