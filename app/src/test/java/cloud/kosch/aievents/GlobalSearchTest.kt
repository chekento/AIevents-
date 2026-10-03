package cloud.kosch.aievents

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSearchTest {
    @Test fun japan_search_uses_japanese_and_english_terms() {
        val langs = EventSearchLexicon.languagesFor("JP", "de")
        assertTrue("ja" in langs)
        assertTrue("en" in langs)
        assertTrue(EventSearchLexicon.aiTerms("ja").contains("人工知能"))
        assertTrue(EventSearchLexicon.participationTerms("ja").contains("イベント"))
    }

    @Test fun brazil_search_uses_portuguese_terms() {
        val langs = EventSearchLexicon.languagesFor("BR", "en")
        assertTrue("pt" in langs)
        assertTrue(EventSearchLexicon.aiTerms("pt").contains("inteligência artificial"))
        assertTrue(EventSearchLexicon.participationTerms("pt").contains("evento"))
    }

    @Test fun arbitrary_world_city_gets_generic_global_queries() {
        val cfg = SearchConfig(
            place = "Reykjavík, Iceland",
            radiusKm = 50,
            language = "de",
            countryCode = "IS"
        )
        val queries = SourceRegistry.queries(cfg)
        assertTrue(queries.isNotEmpty())
        assertTrue(queries.any { "Reykjavík" in it })
        assertTrue(queries.any { "conference" in it || "meetup" in it || "webinar" in it })
    }

    @Test fun long_tail_participation_terms_are_present() {
        val terms = EventSearchLexicon.participationTerms("en")
        assertTrue("symposium" in terms)
        assertTrue("roundtable" in terms)
        assertTrue("fireside chat" in terms)
        assertTrue("demo day" in terms)
        assertTrue("office hours" in terms)
        assertTrue("livestream" in terms)
    }

    @Test fun ai_focus_rejects_non_ai_event() {
        val event = EventItem(
            title = "General startup networking evening",
            start = java.time.Instant.now().plusSeconds(3600),
            end = null,
            venue = "Venue",
            locality = "Reykjavík",
            description = "Meet founders and investors.",
            organizer = "Startup Club",
            sourceName = "example.org",
            sourceUrl = "https://example.org",
            eventUrl = "https://example.org/e",
            price = "",
            language = "en",
            online = false,
            geo = null,
            distanceKm = null,
            confidence = 80
        )
        assertFalse(EventClassifier.hasAiFocus(event, listOf("en")))
    }

    @Test fun ai_focus_accepts_real_ai_event() {
        val event = EventItem(
            title = "Generative AI and LLM Engineering Meetup",
            start = java.time.Instant.now().plusSeconds(3600),
            end = null,
            venue = "Venue",
            locality = "Reykjavík",
            description = "Hands-on RAG and agentic AI sessions.",
            organizer = "AI Community",
            sourceName = "example.org",
            sourceUrl = "https://example.org",
            eventUrl = "https://example.org/e",
            price = "",
            language = "en",
            online = false,
            geo = null,
            distanceKm = null,
            confidence = 80
        )
        assertTrue(EventClassifier.hasAiFocus(event, listOf("en")))
    }
}
