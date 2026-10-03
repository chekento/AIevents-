package cloud.kosch.aievents

data class EventSource(
    val id: String,
    val name: String,
    val domain: String,
    val directQuery: Boolean = true
)

object SourceRegistry {
    val sources = listOf(
        EventSource("globalai", "Global AI Community", "globalai.community"),
        EventSource("houseofai", "House of AI", "house-of-ai.org", directQuery = false),
        EventSource("aihamburg", "AI.HAMBURG", "ai.hamburg", directQuery = false),
        EventSource("aitinkerers", "AI Tinkerers", "aitinkerers.org"),
        EventSource("mlops", "MLOps Community", "mlops.community"),
        EventSource("meetup", "Meetup", "meetup.com"),
        EventSource("luma", "Luma", "luma.com"),
        EventSource("luma-short", "Luma (lu.ma)", "lu.ma"),
        EventSource("eventbrite", "Eventbrite", "eventbrite.com"),
        EventSource("partiful", "Partiful", "partiful.com"),
        EventSource("bevy", "Bevy Communities", "bevy.com"),
        EventSource("sched", "Sched", "sched.com"),
        EventSource("splash", "Splash", "splashthat.com"),
        EventSource("allevents", "AllEvents", "allevents.in"),
        EventSource("sessionize", "Sessionize", "sessionize.com"),
        EventSource("pretalx", "Pretalx", "pretalx.com"),
        EventSource("gdg", "Google Developer Groups", "gdg.community.dev"),
        EventSource("reactor", "Microsoft Reactor", "reactor.microsoft.com"),
        EventSource("aws", "AWS Events", "aws.amazon.com"),
        EventSource("nvidia", "NVIDIA Events", "nvidia.com"),
        EventSource("huggingface", "Hugging Face", "huggingface.co"),
        EventSource("ieee", "IEEE", "ieee.org"),
        EventSource("acm", "ACM", "acm.org"),
        EventSource("10times", "10times", "10times.com"),
        EventSource("confs", "confs.tech", "confs.tech"),
        EventSource("dev-events", "DEV Events", "dev.events"),
        EventSource("eventyay", "eventyay", "eventyay.com"),
        EventSource("devpost", "Devpost Hackathons", "devpost.com"),
        EventSource("hackerearth", "HackerEarth", "hackerearth.com"),
        EventSource("linkedin-events", "LinkedIn Events", "linkedin.com"),
        EventSource("airmeet", "Airmeet", "airmeet.com"),
        EventSource("livestorm", "Livestorm", "livestorm.co"),
        EventSource("zoom-events", "Zoom Events", "events.zoom.us"),
        EventSource("cvent", "Cvent", "cvent.com"),
        EventSource("whova", "Whova", "whova.com"),
        EventSource("bizzabo", "Bizzabo", "bizzabo.com"),
        EventSource("goldcast", "Goldcast", "goldcast.io"),
        EventSource("ringcentral-events", "RingCentral Events", "events.ringcentral.com"),
        EventSource("swapcard", "Swapcard", "swapcard.com"),
        EventSource("brella", "Brella", "brella.io"),
        EventSource("humanitix", "Humanitix", "humanitix.com"),
        EventSource("tickettailor", "Ticket Tailor", "tickettailor.com"),
        EventSource("mobilizon", "Mobilizon", "mobilizon.org"),
        EventSource("opencollective", "OpenCollective", "opencollective.com"),
        EventSource("eventfrog", "Eventfrog", "eventfrog.ch"),
        EventSource("eventfinda", "Eventfinda", "eventfinda.com"),
        EventSource("facebook-events", "Facebook Events", "facebook.com"),
        EventSource("aicamp", "AI Camp", "aicamp.ai"),
        EventSource("aisummit", "The AI Summit", "theaisummit.com"),
        EventSource("odsc", "ODSC", "odsc.com"),
        EventSource("datasciencesalon", "Data Science Salon", "datascience.salon"),
        EventSource("mlconf", "MLconf", "mlconf.com"),
        EventSource("pulse-nyc", "Pulse NYC / AI Week", "pulse.nyc", directQuery = false),
        EventSource("community", "Community / Stammtisch web", "", directQuery = false)
    )

    fun byId(id: String) = sources.firstOrNull { source -> source.id == id }

    fun queries(config: SearchConfig, nearbyPlaces: List<String> = emptyList()): List<String> {
        val place = config.place.trim()
        val year = java.time.Year.now().value
        val extra = config.keywords.trim()
        val category = categoryTerms(config.category)
        val languages = EventSearchLexicon.languagesFor(config.countryCode, config.language)
        val aliases = LocationMatcher.searchAliases(place)
            .filter { it.length >= 3 && !it.equals(place.substringBefore(",").trim(), ignoreCase = true) }
            .take(4)

        val aiCore = EventSearchLexicon.aiTerms("en").take(10).joinToString(" ")
        val participationCore = EventSearchLexicon.participationTerms("en").take(16).joinToString(" ")
        val attendance = EventSearchLexicon.compactAttendance(7)

        val base = mutableListOf(
            "\"" + place + "\" " + aiCore + " " + participationCore + " " + year + " " + extra,
            "\"" + place + "\" " + category + " register RSVP tickets attend join " + year + " " + extra,
            "\"" + place + "\" AI community user group developer group tech talk seminar symposium roundtable networking " + year,
            "\"" + place + "\" AI Stammtisch local chapter club society association community calendar " + year,
            "\"" + place + "\" AI university student group research lab faculty institute colloquium " + year,
            "\"" + place + "\" AI coworking makerspace innovation hub startup hub tech hub informal meetup " + year,
            "\"" + place + "\" AI conference summit congress expo hackathon datathon bootcamp masterclass demo day roadshow " + year,
            "\"" + place + "\" LLM GenAI agents RAG MCP LLMOps MLOps webinar workshop meetup " + year + " " + extra,
            "\"" + place + "\" AI livestream online hybrid in-person event " + attendance + " " + year
        )

        languages.take(3).forEach { lang ->
            val localAi = EventSearchLexicon.aiTerms(lang).takeLast(4).joinToString(" ")
            val localParticipation = EventSearchLexicon.participationTerms(lang).takeLast(8).joinToString(" ")
            if (localAi.isNotBlank() && localParticipation.isNotBlank()) {
                base += "\"" + place + "\" " + localAi + " " + localParticipation + " " + year + " " + extra
            }
        }

        val aliasQueries = aliases.map { alias ->
            "\"" + alias + "\" " + category +
                " AI meetup conference workshop webinar seminar event " + year + " " + extra
        }
        val nearbyQueries = nearbyPlaces
            .filter { it.isNotBlank() && !it.equals(place.substringBefore(",").trim(), ignoreCase = true) }
            .take(6)
            .flatMap { nearby ->
                listOf(
                    "\"" + nearby + "\" " + category + " AI meetup conference workshop webinar event " + year,
                    "\"" + nearby + "\" LLM GenAI agents MLOps LLMOps seminar networking hackathon " + year
                )
            }

        val eligible = eligibleSources(config)
        val siteQueries = eligible.take(22).map { source ->
            "site:" + source.domain + " \"" + place + "\" " + category +
                " event meetup webinar workshop conference register " + year + " " + extra
        }
        return (base + aliasQueries + nearbyQueries + siteQueries).distinct()
    }

    fun deepQueries(config: SearchConfig, nearbyPlaces: List<String> = emptyList()): List<String> {
        val place = config.place.trim()
        val year = java.time.Year.now().value
        val category = categoryTerms(config.category)
        val extra = config.keywords.trim()
        val languages = EventSearchLexicon.languagesFor(config.countryCode, config.language)

        val nearbyDeep = nearbyPlaces
            .filter { it.isNotBlank() && !it.equals(place.substringBefore(",").trim(), ignoreCase = true) }
            .take(8)
            .map { nearby ->
                "\"" + nearby + "\" AI artificial intelligence meetup conference workshop seminar webinar " + year
            }

        val sourceQueries = eligibleSources(config).drop(22).map { source ->
            "site:" + source.domain + " \"" + place + "\" " + category +
                " AI event meetup webinar workshop seminar conference hackathon register " + year
        }

        val longTail = listOf(
            "\"" + place + "\" AI research seminar colloquium lecture reading group study group " + year,
            "\"" + place + "\" AI fireside chat panel roundtable networking open house community night " + year,
            "\"" + place + "\" AI hands-on lab training course session office hours AMA " + year,
            "\"" + place + "\" generative AI product launch launch event showcase developer day devday " + year,
            "\"" + place + "\" AI breakfast meetup lunch and learn unconference festival fair " + year,
            "\"" + place + "\" AI Stammtisch regulars table local group chapter club society association " + year,
            "\"" + place + "\" AI university lab student society faculty seminar colloquium journal club " + year,
            "\"" + place + "\" AI coworking makerspace community calendar tech hub innovation hub informal meetup " + year,
            "site:facebook.com/events \"" + place + "\" AI meetup community " + year,
            "site:linkedin.com/events \"" + place + "\" AI meetup community " + year,
            "site:opencollective.com \"" + place + "\" AI community event " + year,
            "\"" + place + "\" artificial intelligence call for participants apply attend RSVP " + year + " " + extra
        )

        val localized = languages.take(3).map { lang ->
            "\"" + place + "\" " +
                EventSearchLexicon.aiTerms(lang).take(6).joinToString(" ") + " " +
                EventSearchLexicon.participationTerms(lang).take(10).joinToString(" ") +
                " " + year
        }

        return (sourceQueries + nearbyDeep + longTail + localized).distinct()
    }

    private fun categoryTerms(category: EventCategory): String = when (category) {
        EventCategory.AGENTS -> "AI agents agentic MCP"
        EventCategory.GENAI -> "generative AI GenAI LLM"
        EventCategory.ML -> "machine learning ML deep learning"
        EventCategory.DATA -> "data AI analytics"
        EventCategory.ROBOTICS -> "robotics physical AI computer vision"
        EventCategory.BUSINESS -> "AI business transformation enterprise"
        EventCategory.GOVERNANCE -> "AI governance responsible AI regulation ethics safety"
        EventCategory.DEVELOPER -> "AI developer coding engineering"
        EventCategory.RESEARCH -> "AI research paper seminar symposium conference"
        EventCategory.COMMUNITY -> "AI meetup community user group networking"
        EventCategory.ALL -> "AI artificial intelligence generative AI machine learning LLM"
    }

    private fun eligibleSources(config: SearchConfig): List<EventSource> =
        sources.filter { source ->
            source.id in config.enabledSourceIds &&
                source.directQuery &&
                source.domain.isNotBlank()
        }

}
