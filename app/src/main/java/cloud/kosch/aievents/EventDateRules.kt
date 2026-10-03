package cloud.kosch.aievents

import java.time.LocalDate
import java.time.ZoneId

object EventDateRules {
    fun isVisible(
        event: EventItem,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        futureDays: Int,
        includeUnverifiedDates: Boolean
    ): Boolean {
        val startDay = event.start?.atZone(zone)?.toLocalDate()
        val endDay = event.end?.atZone(zone)?.toLocalDate()
        val maxDay = today.plusDays(futureDays.toLong())

        return when {
            startDay == null -> includeUnverifiedDates
            endDay != null && !endDay.isBefore(today) ->
                !startDay.isAfter(maxDay)
            else ->
                !startDay.isBefore(today) && !startDay.isAfter(maxDay)
        }
    }

    fun isCurrentSavedEvent(
        event: EventItem,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        val startDay = event.start?.atZone(zone)?.toLocalDate() ?: return false
        val endDay = event.end?.atZone(zone)?.toLocalDate()
        return if (endDay != null) !endDay.isBefore(today) else !startDay.isBefore(today)
    }
}
