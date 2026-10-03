package cloud.kosch.aievents

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.*

class EventDateRulesTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = ZonedDateTime.of(2026, 10, 3, 16, 30, 0, 0, zone).toInstant()

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

    @Test fun already_finished_event_today_is_hidden() {
        val start = ZonedDateTime.of(2026, 10, 3, 9, 0, 0, 0, zone).toInstant()
        val end = ZonedDateTime.of(2026, 10, 3, 12, 0, 0, 0, zone).toInstant()
        assertFalse(EventDateRules.isVisible(event(start, end), now, zone, 365, false))
    }

    @Test fun upcoming_event_today_is_visible() {
        val start = ZonedDateTime.of(2026, 10, 3, 18, 0, 0, 0, zone).toInstant()
        assertTrue(EventDateRules.isVisible(event(start), now, zone, 365, false))
    }

    @Test fun ongoing_event_is_visible_when_end_is_future() {
        val start = now.minusSeconds(3600)
        val end = now.plusSeconds(3600)
        assertTrue(EventDateRules.isVisible(event(start, end), now, zone, 365, false))
    }

    @Test fun past_event_without_end_is_hidden() {
        assertFalse(EventDateRules.isVisible(event(now.minusSeconds(60)), now, zone, 365, false))
    }

    @Test fun future_event_is_visible_within_horizon() {
        assertTrue(EventDateRules.isVisible(event(now.plusSeconds(30L * 86400L)), now, zone, 365, false))
    }

    @Test fun event_beyond_horizon_is_hidden() {
        assertFalse(EventDateRules.isVisible(event(now.plusSeconds(31L * 86400L)), now, zone, 30, false))
    }

    @Test fun undated_event_is_hidden_by_default() {
        assertFalse(EventDateRules.isVisible(event(null), now, zone, 365, false))
    }

    @Test fun undated_event_can_be_explicitly_enabled() {
        assertTrue(EventDateRules.isVisible(event(null), now, zone, 365, true))
    }

    @Test fun expired_saved_event_is_hidden() {
        assertFalse(EventDateRules.isCurrentSavedEvent(event(now.minusSeconds(60)), now, zone))
    }
}
