package cloud.kosch.aievents

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.*

class EventDateRulesTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 10, 3)

    private fun instant(day: LocalDate, hour: Int = 12): Instant =
        day.atTime(hour, 0).atZone(zone).toInstant()

    private fun event(start: Instant?, end: Instant? = null) = EventItem(
        title = "Test AI event",
        start = start,
        end = end,
        venue = "Test",
        locality = "Hamburg",
        description = "",
        organizer = "",
        sourceName = "test",
        sourceUrl = "https://example.com",
        eventUrl = "https://example.com",
        price = "",
        language = "de",
        online = false,
        geo = null,
        distanceKm = null,
        confidence = 100
    )

    @Test fun yesterday_is_never_visible() {
        assertFalse(EventDateRules.isVisible(event(instant(today.minusDays(1))), today, zone, 365, false))
    }

    @Test fun today_is_visible_even_if_clock_time_passed() {
        assertTrue(EventDateRules.isVisible(event(instant(today, 1)), today, zone, 365, false))
    }

    @Test fun future_event_is_visible_within_horizon() {
        assertTrue(EventDateRules.isVisible(event(instant(today.plusDays(30))), today, zone, 365, false))
    }

    @Test fun event_beyond_horizon_is_hidden() {
        assertFalse(EventDateRules.isVisible(event(instant(today.plusDays(31))), today, zone, 30, false))
    }

    @Test fun ongoing_multiday_event_remains_visible() {
        assertTrue(EventDateRules.isVisible(
            event(instant(today.minusDays(2)), instant(today.plusDays(1))),
            today, zone, 365, false
        ))
    }

    @Test fun already_ended_multiday_event_is_hidden() {
        assertFalse(EventDateRules.isVisible(
            event(instant(today.minusDays(3)), instant(today.minusDays(1))),
            today, zone, 365, false
        ))
    }

    @Test fun undated_event_is_hidden_by_default() {
        assertFalse(EventDateRules.isVisible(event(null), today, zone, 365, false))
    }

    @Test fun undated_event_can_be_explicitly_enabled() {
        assertTrue(EventDateRules.isVisible(event(null), today, zone, 365, true))
    }

    @Test fun expired_saved_event_is_hidden() {
        assertFalse(EventDateRules.isCurrentSavedEvent(event(instant(today.minusDays(1))), today, zone))
    }
}
