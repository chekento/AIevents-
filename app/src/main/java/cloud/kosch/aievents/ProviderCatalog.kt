package cloud.kosch.aievents

enum class ProviderCategory {
    ALL, MODEL, CLOUD, DEVTOOLS, AGENTS, LLMOPS, MLOPS, DATA, VECTOR,
    VOICE, IMAGE_VIDEO, ENTERPRISE, SAFETY, COMMUNITY, RESEARCH
}

data class ProviderEntry(
    val id: String,
    val name: String,
    val domains: List<String>,
    val category: ProviderCategory
)

object ProviderCatalog {
    private val raw = "openai|OpenAI|openai.com;developers.openai.com;forum.openai.com;academy.openai.com|MODEL\nanthropic|Anthropic|anthropic.com|MODEL\ngoogle-ai|Google AI / Gemini|ai.google;gemini.google.com;developers.google.com;deepmind.google|MODEL\ngoogle-cloud-ai|Google Cloud Vertex AI|cloud.google.com|CLOUD\nmicrosoft-ai|Microsoft AI / Azure AI|microsoft.com;azure.microsoft.com;developer.microsoft.com|CLOUD\nmeta-ai|Meta AI / Llama|ai.meta.com;meta.com|MODEL\nxai|xAI / Grok|x.ai|MODEL\nmistral|Mistral AI|mistral.ai|MODEL\ncohere|Cohere|cohere.com|MODEL\nai21|AI21 Labs|ai21.com|MODEL\naleph-alpha|Aleph Alpha|aleph-alpha.com|MODEL\naws-ai|AWS AI / Bedrock|aws.amazon.com|CLOUD\nnvidia-ai|NVIDIA AI|nvidia.com|CLOUD\nibm-watsonx|IBM watsonx|ibm.com|ENTERPRISE\nsalesforce-ai|Salesforce AI|salesforce.com|ENTERPRISE\noracle-ai|Oracle AI|oracle.com|ENTERPRISE\nsap-ai|SAP Business AI|sap.com|ENTERPRISE\nbaidu-ai|Baidu AI / ERNIE|baidu.com|MODEL\nalibaba-qwen|Alibaba Qwen|qwen.ai;alibabacloud.com|MODEL\ntencent-hunyuan|Tencent Hunyuan|hunyuan.tencent.com|MODEL\nbytedance-doubao|ByteDance Doubao|doubao.com;bytedance.com|MODEL\ndeepseek|DeepSeek|deepseek.com|MODEL\nzhipu|Zhipu AI / GLM|z.ai;zhipuai.cn|MODEL\nmoonshot|Moonshot AI / Kimi|moonshot.cn;kimi.com|MODEL\nminimax|MiniMax|minimax.io|MODEL\nzeroone|01.AI|01.ai|MODEL\nbaichuan|Baichuan AI|baichuan-ai.com|MODEL\nsensetime|SenseTime|sensetime.com|MODEL\niflytek|iFlytek Spark|xfyun.cn|MODEL\nhuawei-pangu|Huawei Pangu|huawei.com|MODEL\nnaver|NAVER HyperCLOVA X|navercorp.com|MODEL\nkakao|Kakao Brain|kakaobrain.com|MODEL\nupstage|Upstage|upstage.ai|MODEL\nlg-ai|LG AI Research / EXAONE|lgresearch.ai|MODEL\nsamsung-ai|Samsung Research AI|research.samsung.com|MODEL\nfujitsu-ai|Fujitsu Kozuchi|fujitsu.com|ENTERPRISE\nnec-ai|NEC AI|nec.com|ENTERPRISE\nrakuten-ai|Rakuten AI|rakuten.com|MODEL\npreferred-networks|Preferred Networks|preferred.jp|MODEL\nstability-ai|Stability AI|stability.ai|IMAGE_VIDEO\nblack-forest-labs|Black Forest Labs|blackforestlabs.ai|IMAGE_VIDEO\nmidjourney|Midjourney|midjourney.com|IMAGE_VIDEO\nrunway|Runway|runwayml.com|IMAGE_VIDEO\npika|Pika|pika.art|IMAGE_VIDEO\nluma-ai|Luma AI|lumalabs.ai|IMAGE_VIDEO\nideogram|Ideogram|ideogram.ai|IMAGE_VIDEO\nleonardo|Leonardo AI|leonardo.ai|IMAGE_VIDEO\nrecraft|Recraft|recraft.ai|IMAGE_VIDEO\nadobe-firefly|Adobe Firefly|adobe.com|IMAGE_VIDEO\nelevenlabs|ElevenLabs|elevenlabs.io|VOICE\nsuno|Suno|suno.com|VOICE\nzuno|Zuno||MODEL\nudio|Udio|udio.com|VOICE\nhume|Hume AI|hume.ai|VOICE\ncartesia|Cartesia|cartesia.ai|VOICE\ndeepgram|Deepgram|deepgram.com|VOICE\nassemblyai|AssemblyAI|assemblyai.com|VOICE\nspeechmatics|Speechmatics|speechmatics.com|VOICE\nresemble|Resemble AI|resemble.ai|VOICE\nplayht|PlayHT|play.ht|VOICE\nsynthesia|Synthesia|synthesia.io|IMAGE_VIDEO\nheygen|HeyGen|heygen.com|IMAGE_VIDEO\ncharacter-ai|Character.AI|character.ai|MODEL\ninflection|Inflection AI|inflection.ai|MODEL\nperplexity|Perplexity|perplexity.ai|MODEL\nyoucom|You.com|you.com|MODEL\nphind|Phind|phind.com|MODEL\nglean|Glean|glean.com|ENTERPRISE\nsierra|Sierra|sierra.ai|ENTERPRISE\nharvey|Harvey|harvey.ai|ENTERPRISE\nwriter|Writer|writer.com|ENTERPRISE\njasper|Jasper|jasper.ai|ENTERPRISE\ncopyai|Copy.ai|copy.ai|ENTERPRISE\ntypeface|Typeface|typeface.ai|ENTERPRISE\ntogether|Together AI|together.ai|CLOUD\nfireworks|Fireworks AI|fireworks.ai|CLOUD\ngroq|Groq|groq.com|CLOUD\ncerebras|Cerebras|cerebras.ai|CLOUD\nsambanova|SambaNova|sambanova.ai|CLOUD\nlambda|Lambda|lambda.ai|CLOUD\ncoreweave|CoreWeave|coreweave.com|CLOUD\ncrusoe|Crusoe|crusoe.ai|CLOUD\nnebius|Nebius|nebius.com|CLOUD\nreplicate|Replicate|replicate.com|CLOUD\nmodal|Modal|modal.com|CLOUD\nbaseten|Baseten|baseten.co|CLOUD\nbentoml|BentoML|bentoml.com|MLOPS\nanyscale|Anyscale|anyscale.com|CLOUD\nhuggingface|Hugging Face|huggingface.co|MODEL\nmodelscope|ModelScope|modelscope.cn|MODEL\nopenrouter|OpenRouter|openrouter.ai|CLOUD\nlitellm|LiteLLM|litellm.ai|LLMOPS\nvercel-ai|Vercel AI SDK|vercel.com|DEVTOOLS\ncloudflare-ai|Cloudflare Workers AI|cloudflare.com|CLOUD\ngithub-models|GitHub Models|github.com|DEVTOOLS\nazure-foundry|Azure AI Foundry|azure.microsoft.com|CLOUD\nlangchain|LangChain|langchain.com|AGENTS\nllamaindex|LlamaIndex|llamaindex.ai|AGENTS\nhaystack|deepset Haystack|deepset.ai|AGENTS\nsemantic-kernel|Semantic Kernel|github.com/microsoft|AGENTS\nautogen|Microsoft AutoGen|microsoft.github.io|AGENTS\ncrewai|CrewAI|crewai.com|AGENTS\nagno|Agno|agno.com|AGENTS\npydantic-ai|PydanticAI|ai.pydantic.dev|AGENTS\ndspy|DSPy|dspy.ai|AGENTS\nguidance|Guidance|github.com/guidance-ai|DEVTOOLS\noutlines|Outlines|dottxt.ai|DEVTOOLS\ninstructor|Instructor|python.useinstructor.com|DEVTOOLS\nguardrails-ai|Guardrails AI|guardrailsai.com|SAFETY\nnemo-guardrails|NVIDIA NeMo Guardrails|nvidia.com|SAFETY\nautogpt|AutoGPT|agpt.co|AGENTS\ncomposio|Composio|composio.dev|AGENTS\nlanggraph|LangGraph|langchain.com|AGENTS\nmastra|Mastra|mastra.ai|AGENTS\ndify|Dify|dify.ai|AGENTS\nflowise|Flowise|flowiseai.com|AGENTS\nn8n|n8n AI|n8n.io|AGENTS\nzapier-ai|Zapier AI|zapier.com|AGENTS\nmake-ai|Make AI|make.com|AGENTS\ngumloop|Gumloop|gumloop.com|AGENTS\ndust|Dust|dust.tt|AGENTS\nstack-ai|Stack AI|stack-ai.com|AGENTS\nrelevance-ai|Relevance AI|relevanceai.com|AGENTS\nlangsmith|LangSmith|smith.langchain.com|LLMOPS\nlangfuse|Langfuse|langfuse.com|LLMOPS\nhelicone|Helicone|helicone.ai|LLMOPS\narize|Arize AI / Phoenix|arize.com;phoenix.arize.com|LLMOPS\nwandb-weave|Weights & Biases Weave|wandb.ai|LLMOPS\nbraintrust|Braintrust|braintrust.dev|LLMOPS\nhumanloop|Humanloop|humanloop.com|LLMOPS\ngalileo|Galileo|galileo.ai|LLMOPS\nwhylabs|WhyLabs|whylabs.ai|LLMOPS\nfiddler|Fiddler AI|fiddler.ai|SAFETY\npatronus|Patronus AI|patronus.ai|SAFETY\narthur|Arthur AI|arthur.ai|SAFETY\ntraceloop|Traceloop|traceloop.com|LLMOPS\nliteral-ai|Literal AI|literalai.com|LLMOPS\nlunary|Lunary|lunary.ai|LLMOPS\ndeepeval|DeepEval / Confident AI|confident-ai.com|LLMOPS\nmaxim|Maxim AI|maxim.ai|LLMOPS\nevidently|Evidently AI|evidentlyai.com|MLOPS\ngiskard|Giskard|giskard.ai|SAFETY\nlakera|Lakera|lakera.ai|SAFETY\nprotect-ai|Protect AI|protectai.com|SAFETY\nhiddenlayer|HiddenLayer|hiddenlayer.com|SAFETY\ncalypsoai|CalypsoAI|calypsoai.com|SAFETY\npromptlayer|PromptLayer|promptlayer.com|LLMOPS\npromptfoo|Promptfoo|promptfoo.dev|LLMOPS\nportkey|Portkey|portkey.ai|LLMOPS\nkeywords-ai|Keywords AI|keywordsai.co|LLMOPS\nlangwatch|LangWatch|langwatch.ai|LLMOPS\nopenlit|OpenLIT|openlit.io|LLMOPS\nopik|Opik / Comet|comet.com|LLMOPS\nhoneyhive|HoneyHive|honeyhive.ai|LLMOPS\nagentops|AgentOps|agentops.ai|LLMOPS\nparea|Parea AI|parea.ai|LLMOPS\nmlops-community|MLOps Community|mlops.community|MLOPS\nkubeflow|Kubeflow|kubeflow.org|MLOPS\nmlflow|MLflow|mlflow.org|MLOPS\ncomet|Comet|comet.com|MLOPS\nclearml|ClearML|clear.ml|MLOPS\nneptune|Neptune.ai|neptune.ai|MLOPS\ndvc|DVC / Iterative|dvc.org|MLOPS\nmetaflow|Metaflow|metaflow.org|MLOPS\nflyte|Flyte|flyte.org|MLOPS\nzenml|ZenML|zenml.io|MLOPS\nkedro|Kedro|kedro.org|MLOPS\nray|Ray|ray.io|MLOPS\nprefect|Prefect|prefect.io|MLOPS\ndagster|Dagster|dagster.io|MLOPS\nairflow|Apache Airflow|airflow.apache.org|MLOPS\nfeast|Feast|feast.dev|MLOPS\ntecton|Tecton|tecton.ai|MLOPS\nhopsworks|Hopsworks|hopsworks.ai|MLOPS\nseldon|Seldon|seldon.io|MLOPS\nkserve|KServe|kserve.github.io|MLOPS\ndetermined|Determined AI / HPE|determined.ai|MLOPS\ndomino|Domino Data Lab|domino.ai|MLOPS\ndatarobot|DataRobot|datarobot.com|MLOPS\nh2o|H2O.ai|h2o.ai|MLOPS\ndataiku|Dataiku|dataiku.com|MLOPS\ndatabricks|Databricks|databricks.com|DATA\nsnowflake|Snowflake|snowflake.com|DATA\ndbt|dbt Labs|getdbt.com|DATA\nfivetran|Fivetran|fivetran.com|DATA\nconfluent|Confluent|confluent.io|DATA\nmongodb|MongoDB|mongodb.com|DATA\nelastic|Elastic|elastic.co|DATA\nredis|Redis|redis.io|DATA\nclickhouse|ClickHouse|clickhouse.com|DATA\npinecone|Pinecone|pinecone.io|VECTOR\nweaviate|Weaviate|weaviate.io|VECTOR\nqdrant|Qdrant|qdrant.tech|VECTOR\nzilliz|Zilliz / Milvus|zilliz.com;milvus.io|VECTOR\nchroma|Chroma|trychroma.com|VECTOR\nlancedb|LanceDB|lancedb.com|VECTOR\nvespa|Vespa|vespa.ai|VECTOR\nsinglestore|SingleStore|singlestore.com|DATA\nneo4j|Neo4j|neo4j.com|DATA\nsupabase|Supabase|supabase.com|DATA\nneon|Neon|neon.tech|DATA\nplanetscale|PlanetScale|planetscale.com|DATA\ncockroach|Cockroach Labs|cockroachlabs.com|DATA\nscale-ai|Scale AI|scale.com|DATA\nlabelbox|Labelbox|labelbox.com|DATA\nsnorkel|Snorkel AI|snorkel.ai|DATA\nsuperannotate|SuperAnnotate|superannotate.com|DATA\nencord|Encord|encord.com|DATA\nhuman-signal|HumanSignal / Label Studio|humansignal.com;labelstud.io|DATA\nsurge-ai|Surge AI|surgehq.ai|DATA\ntoloka|Toloka|toloka.ai|DATA\neleutherai|EleutherAI|eleuther.ai|RESEARCH\nlaion|LAION|laion.ai|RESEARCH\nallen-ai|Allen Institute for AI|allenai.org|RESEARCH\nmlcommons|MLCommons|mlcommons.org|RESEARCH\nlf-ai-data|Linux Foundation AI & Data|lfaidata.foundation|COMMUNITY\npytorch|PyTorch|pytorch.org|COMMUNITY\ntensorflow|TensorFlow|tensorflow.org|COMMUNITY\njax|JAX|jax.readthedocs.io|COMMUNITY\nonnx|ONNX|onnx.ai|COMMUNITY\nopenmmlab|OpenMMLab|openmmlab.com|COMMUNITY\ndatatalksclub|DataTalksClub|datatalks.club|COMMUNITY\nai-tinkerers|AI Tinkerers|aitinkerers.org|COMMUNITY\nglobal-ai-community|Global AI Community|globalai.community|COMMUNITY\nwomen-in-ai|Women in AI|womeninai.co|COMMUNITY\ndeeplearning-ai|DeepLearning.AI|deeplearning.ai|COMMUNITY\nfastai|fast.ai|fast.ai|COMMUNITY\nfullstackdeeplearning|Full Stack Deep Learning|fullstackdeeplearning.com|COMMUNITY\nai-engineer|AI Engineer|ai.engineer|COMMUNITY\ngenai-collective|GenAI Collective|genaicollective.com|COMMUNITY\nmlops-world|MLOps World|mlopsworld.com|COMMUNITY\nai-summit|The AI Summit|theaisummit.com|COMMUNITY\nodsc|ODSC|odsc.com|COMMUNITY\nneurips|NeurIPS|neurips.cc|RESEARCH\nicml|ICML|icml.cc|RESEARCH\niclr|ICLR|iclr.cc|RESEARCH\naaai|AAAI|aaai.org|RESEARCH\nacl|ACL|aclweb.org|RESEARCH\nemnlp|EMNLP|aclweb.org|RESEARCH\nkdd|KDD|kdd.org|RESEARCH\ncvpr|CVPR|thecvf.com|RESEARCH"

    val providers: List<ProviderEntry> by lazy {
        raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val p = line.split("|")
                if (p.size < 4) return@mapNotNull null
                ProviderEntry(
                    id = p[0],
                    name = p[1],
                    domains = p[2].split(";").filter { it.isNotBlank() },
                    category = runCatching { ProviderCategory.valueOf(p[3]) }
                        .getOrDefault(ProviderCategory.MODEL)
                )
            }
            .distinctBy { it.id }
            .toList()
    }

    fun find(query: String, category: ProviderCategory = ProviderCategory.ALL): List<ProviderEntry> {
        val q = query.trim().lowercase()
        return providers.filter { provider ->
            (category == ProviderCategory.ALL || provider.category == category) &&
                (q.isBlank() ||
                    q in provider.name.lowercase() ||
                    q in provider.id.lowercase() ||
                    provider.domains.any { q in it.lowercase() })
        }
    }

    fun detect(event: EventItem): ProviderEntry? {
        val urls = listOf(event.eventUrl, event.sourceUrl)
        providers.forEach { provider ->
            provider.domains.forEach { domain ->
                if (domain.isBlank()) return@forEach
                if (urls.any { url -> url.contains(domain, ignoreCase = true) }) return provider
            }
        }
        val text = (event.title + " " + event.organizer + " " + event.description).lowercase()
        return providers.firstOrNull { provider ->
            provider.name.length >= 4 && provider.name.lowercase() in text
        }
    }

    fun enrich(event: EventItem): EventItem {
        val provider = detect(event) ?: return event
        return event.copy(
            officialProvider = true,
            providerName = provider.name,
            confidence = (event.confidence + 5).coerceAtMost(100)
        )
    }

    fun logoDomain(providerName: String): String? =
        providers.firstOrNull { it.name.equals(providerName, ignoreCase = true) }
            ?.domains?.firstOrNull()

    fun countByCategory(category: ProviderCategory): Int =
        if (category == ProviderCategory.ALL) providers.size
        else providers.count { it.category == category }
}
