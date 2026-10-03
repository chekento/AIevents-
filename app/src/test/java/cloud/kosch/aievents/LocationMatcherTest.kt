package cloud.kosch.aievents

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationMatcherTest {
    @Test fun new_york_never_matches_tokyo() {
        assertFalse(
            LocationMatcher.textMatches(
                "Private venue, 101-0063 Chiyoda City, Tokyo, Japan",
                "New York City, New York, United States"
            )
        )
    }

    @Test fun new_york_matches_new_york_city() {
        assertTrue(
            LocationMatcher.textMatches(
                "Javits Center, New York City, NY, USA",
                "New York City, New York, United States"
            )
        )
    }

    @Test fun new_york_matches_nyc_acronym() {
        assertTrue(
            LocationMatcher.textMatches(
                "Google Agentic AI Builder Lab - NYC",
                "New York City, New York, United States"
            )
        )
    }

    @Test fun hamburg_matches_hamburg() {
        assertTrue(
            LocationMatcher.textMatches(
                "Hongkongstraße 2-4, Hamburg, Germany",
                "Hamburg, Germany"
            )
        )
    }

    @Test fun berlin_does_not_match_hamburg() {
        assertFalse(
            LocationMatcher.textMatches(
                "Berlin, Germany",
                "Hamburg, Germany"
            )
        )
    }
}
