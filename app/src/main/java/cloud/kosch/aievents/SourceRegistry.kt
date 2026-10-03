package cloud.kosch.aievents

data class EventSource(
    val id: String,
    val name: String,
    val domain: String,
    val directQuery: Boolean = true
)

object SourceRegistry {
    val sources = listOf(
        EventSource("openai", "OpenAI Events", "openai.com", directQuery = false),
        EventSource("google", "Google Developer / AI Events", "developers.google.com", directQuery = false),
        EventSource("microsoft", "Microsoft AI Events", "microsoft.com", directQuery = false),
        EventSource("anthropic", "Anthropic Events", "anthropic.com", directQuery = false),
        EventSource("meta", "Meta AI Events", "ai.meta.com", directQuery = false),
        EventSource("aws-official", "AWS AI Events", "aws.amazon.com", directQuery = false),
        EventSource("nvidia-official", "NVIDIA AI Events", "nvidia.com", directQuery = false),
        EventSource("huggingface-official", "Hugging Face Events", "huggingface.co", directQuery = false),
        EventSource("globalai", "Global AI Community", "globalai.community"),
        EventSource("houseofai", "House of AI", "house-of-ai.org"),
        EventSource("aihamburg", "AI.HAMBURG", "ai.hamburg"),
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
        EventSource("community", "Community / Stammtisch web", "", directQuery = false)
    )

    fun byId(id: String) = sources.firstOrNull { source -> source.id == id }

    fun queries(config: SearchConfig): List<String> {
        val place = config.place.trim()
        val year = java.time.Year.now().value
        val category = when (config.category) {
            EventCategory.AGENTS -> "AI agents agentic MCP"
            EventCategory.GENAI -> "generative AI LLM"
            EventCategory.ML -> "machine learning ML"
            EventCategory.DATA -> "data AI analytics"
            EventCategory.ROBOTICS -> "robotics physical AI computer vision"
            EventCategory.BUSINESS -> "AI business transformation enterprise"
            EventCategory.GOVERNANCE -> "AI governance responsible AI regulation"
            EventCategory.DEVELOPER -> "AI developer coding engineering"
            EventCategory.RESEARCH -> "AI research conference"
            EventCategory.COMMUNITY -> "AI meetup community Stammtisch user group"
            EventCategory.ALL -> "AI artificial intelligence generative AI machine learning"
        }
        val extra = config.keywords.trim()
        val aliases = LocationMatcher.searchAliases(place)
            .filter { it.length >= 3 && !it.equals(place.substringBefore(",").trim(), ignoreCase = true) }
            .take(3)
        val localAi = when (config.language) {
            "de" -> "KI Künstliche Intelligenz"
            "fr" -> "IA intelligence artificielle"
            "es" -> "IA inteligencia artificial"
            "it" -> "IA intelligenza artificiale"
            "pl" -> "AI sztuczna inteligencja"
            "pt" -> "IA inteligência artificial"
            "nl" -> "AI kunstmatige intelligentie"
            "sv" -> "AI artificiell intelligens"
            "da" -> "AI kunstig intelligens"
            "fi" -> "AI tekoäly"
            "tr" -> "AI yapay zeka"
            "cs" -> "AI umělá inteligence"
            "ja" -> "AI 人工知能"
            "ko" -> "AI 인공지능"
            "zh" -> "AI 人工智能"
            else -> "AI artificial intelligence"
        }
        val base = listOf(
            "\"" + place + "\" " + category + " event conference meetup workshop hackathon " + year + " " + extra,
            "\"" + place + "\" " + localAi + " meetup community user group Stammtisch " + year + " " + extra,
            "\"" + place + "\" LLM agents RAG MCP workshop meetup " + year + " " + extra,
            "\"" + place + "\" AI events calendar upcoming " + year + " " + extra,
            "\"" + place + "\" KI Veranstaltung Termine Stammtisch Konferenz Workshop " + year + " " + extra,
            "\"" + place + "\" House of AI AI hub community events " + year + " " + extra
        )
        val aliasQueries = if (aliases.isEmpty()) emptyList() else listOf(
            aliases.joinToString(" OR ") { "\"" + it + "\"" } +
                " " + category + " meetup conference workshop event " + year + " " + extra
        )
        val siteQueries = sources
            .filter { source -> source.id in config.enabledSourceIds && source.directQuery && source.domain.isNotBlank() }
            .map { source ->
                "site:" + source.domain + " \"" + place + "\" " + category +
                    " event meetup conference upcoming " + year + " " + extra
            }
        val officialQueries = OfficialProviders.globalQueries(year, config.enabledSourceIds)
        return (base + aliasQueries + siteQueries + officialQueries).distinct()
    }
}
