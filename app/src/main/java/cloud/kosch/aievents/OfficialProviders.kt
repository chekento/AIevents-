package cloud.kosch.aievents

import java.net.URI

data class OfficialProvider(
    val id: String,
    val name: String,
    val domains: Set<String>,
    val queryTerms: String
)

object OfficialProviders {
    val providers = listOf(
        OfficialProvider(
            "openai", "OpenAI",
            setOf("openai.com", "developers.openai.com", "events.openai.com", "forum.openai.com", "academy.openai.com"),
            "OpenAI DevDay Codex developer community event"
        ),
        OfficialProvider(
            "google", "Google",
            setOf("developers.google.com", "cloud.google.com", "events.withgoogle.com", "deepmind.google"),
            "Google AI developer Gemini Cloud event DevFest summit"
        ),
        OfficialProvider(
            "microsoft", "Microsoft",
            setOf("microsoft.com", "developer.microsoft.com", "events.microsoft.com"),
            "Microsoft AI Tour Reactor Build Copilot event"
        ),
        OfficialProvider(
            "anthropic", "Anthropic",
            setOf("anthropic.com"),
            "Anthropic Claude developer builders event"
        ),
        OfficialProvider(
            "meta", "Meta",
            setOf("ai.meta.com", "meta.com"),
            "Meta AI Llama developer event"
        ),
        OfficialProvider(
            "aws", "AWS",
            setOf("aws.amazon.com"),
            "AWS generative AI Bedrock summit event"
        ),
        OfficialProvider(
            "nvidia", "NVIDIA",
            setOf("nvidia.com"),
            "NVIDIA GTC AI developer event"
        ),
        OfficialProvider(
            "huggingface", "Hugging Face",
            setOf("huggingface.co"),
            "Hugging Face community AI event"
        ),
        OfficialProvider(
            "mistral", "Mistral AI",
            setOf("mistral.ai"),
            "Mistral AI developer event"
        ),
        OfficialProvider(
            "cohere", "Cohere",
            setOf("cohere.com"),
            "Cohere AI developer event"
        ),
        OfficialProvider(
            "databricks", "Databricks",
            setOf("databricks.com"),
            "Databricks Data AI summit event"
        ),
        OfficialProvider(
            "snowflake", "Snowflake",
            setOf("snowflake.com"),
            "Snowflake AI Data Cloud summit event"
        ),
        OfficialProvider(
            "mongodb", "MongoDB",
            setOf("mongodb.com"),
            "MongoDB AI developer event"
        )
    )

    fun detect(event: EventItem): OfficialProvider? {
        val urls = listOf(event.eventUrl, event.sourceUrl)
        for (raw in urls) {
            val host = runCatching { URI(raw).host?.lowercase()?.removePrefix("www.") }.getOrNull() ?: continue
            providers.firstOrNull { provider ->
                provider.domains.any { domain -> host == domain || host.endsWith("." + domain) }
            }?.let { return it }
        }

        val text = (event.organizer + " " + event.title).lowercase()
        return providers.firstOrNull { provider ->
            val n = provider.name.lowercase()
            text.contains(n) &&
                listOf("event", "dev", "summit", "meetup", "tour", "conference", "workshop", "community")
                    .any { it in text }
        }
    }

    fun enrich(event: EventItem): EventItem {
        val provider = detect(event) ?: return event
        return event.copy(
            officialProvider = true,
            providerName = provider.name,
            confidence = (event.confidence + 8).coerceAtMost(100)
        )
    }

    fun globalQueries(year: Int): List<String> = providers.take(8).mapNotNull { provider ->
        provider.domains.firstOrNull()?.let { domain ->
            "site:" + domain + " " + provider.queryTerms + " " + year + " upcoming"
        }
    }.distinct()
}
