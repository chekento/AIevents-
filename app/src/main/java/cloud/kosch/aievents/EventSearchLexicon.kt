package cloud.kosch.aievents

object EventSearchLexicon {
    private val universalParticipation = listOf(
        "event", "meetup", "conference", "workshop", "webinar", "seminar",
        "symposium", "congress", "summit", "forum", "hackathon", "datathon",
        "bootcamp", "masterclass", "developer day", "dev day", "tech talk",
        "panel", "roundtable", "fireside chat", "networking", "user group",
        "community meetup", "study group", "reading group", "research seminar",
        "demo day", "roadshow", "expo", "festival", "showcase", "build day",
        "hands-on lab", "lab", "office hours", "AMA", "livestream",
        "launch event", "product launch", "community night", "breakfast meetup",
        "lunch and learn", "open house", "training", "course", "session"
    )

    private val aiCore = listOf(
        "AI", "artificial intelligence", "generative AI", "GenAI",
        "machine learning", "ML", "deep learning", "LLM", "large language model",
        "AI agents", "agentic AI", "RAG", "retrieval augmented generation",
        "MCP", "multimodal AI", "computer vision", "robotics", "physical AI",
        "AI governance", "responsible AI", "AI safety", "AI ethics",
        "AI engineering", "LLMOps", "MLOps", "prompt engineering",
        "AI transformation", "AI automation", "AI developer"
    )

    private val localizedAi = mapOf(
        "de" to listOf("KI", "Künstliche Intelligenz", "maschinelles Lernen"),
        "fr" to listOf("IA", "intelligence artificielle", "apprentissage automatique"),
        "es" to listOf("IA", "inteligencia artificial", "aprendizaje automático"),
        "it" to listOf("IA", "intelligenza artificiale", "apprendimento automatico"),
        "pt" to listOf("IA", "inteligência artificial", "aprendizado de máquina"),
        "nl" to listOf("AI", "kunstmatige intelligentie", "machine learning"),
        "pl" to listOf("AI", "sztuczna inteligencja", "uczenie maszynowe"),
        "cs" to listOf("AI", "umělá inteligence", "strojové učení"),
        "tr" to listOf("AI", "yapay zeka", "makine öğrenmesi"),
        "sv" to listOf("AI", "artificiell intelligens", "maskininlärning"),
        "da" to listOf("AI", "kunstig intelligens", "maskinlæring"),
        "fi" to listOf("AI", "tekoäly", "koneoppiminen"),
        "no" to listOf("AI", "kunstig intelligens", "maskinlæring"),
        "ja" to listOf("AI", "人工知能", "機械学習", "生成AI"),
        "ko" to listOf("AI", "인공지능", "머신러닝", "생성형 AI"),
        "zh" to listOf("AI", "人工智能", "机器学习", "生成式人工智能"),
        "ar" to listOf("الذكاء الاصطناعي", "تعلم الآلة", "الذكاء الاصطناعي التوليدي"),
        "hi" to listOf("AI", "कृत्रिम बुद्धिमत्ता", "मशीन लर्निंग"),
        "id" to listOf("AI", "kecerdasan buatan", "pembelajaran mesin"),
        "th" to listOf("AI", "ปัญญาประดิษฐ์", "แมชชีนเลิร์นนิง"),
        "vi" to listOf("AI", "trí tuệ nhân tạo", "học máy"),
        "ru" to listOf("ИИ", "искусственный интеллект", "машинное обучение")
    )

    private val localizedParticipation = mapOf(
        "de" to listOf("Veranstaltung", "Treffen", "Stammtisch", "Konferenz", "Workshop", "Seminar", "Webinar", "Vortrag", "Hackathon", "Netzwerktreffen"),
        "fr" to listOf("événement", "rencontre", "conférence", "atelier", "séminaire", "webinaire", "hackathon"),
        "es" to listOf("evento", "encuentro", "conferencia", "taller", "seminario", "webinar", "hackathon"),
        "it" to listOf("evento", "incontro", "conferenza", "workshop", "seminario", "webinar", "hackathon"),
        "pt" to listOf("evento", "encontro", "conferência", "workshop", "seminário", "webinar", "hackathon"),
        "nl" to listOf("evenement", "bijeenkomst", "conferentie", "workshop", "seminar", "webinar", "hackathon"),
        "pl" to listOf("wydarzenie", "spotkanie", "konferencja", "warsztat", "seminarium", "webinar", "hackathon"),
        "cs" to listOf("akce", "setkání", "konference", "workshop", "seminář", "webinář", "hackathon"),
        "tr" to listOf("etkinlik", "buluşma", "konferans", "atölye", "seminer", "webinar", "hackathon"),
        "sv" to listOf("evenemang", "träff", "konferens", "workshop", "seminarium", "webbinarium", "hackathon"),
        "da" to listOf("arrangement", "møde", "konference", "workshop", "seminar", "webinar", "hackathon"),
        "fi" to listOf("tapahtuma", "tapaaminen", "konferenssi", "työpaja", "seminaari", "webinaari", "hackathon"),
        "no" to listOf("arrangement", "møte", "konferanse", "workshop", "seminar", "webinar", "hackathon"),
        "ja" to listOf("イベント", "勉強会", "カンファレンス", "ワークショップ", "セミナー", "ウェビナー", "ハッカソン"),
        "ko" to listOf("행사", "모임", "컨퍼런스", "워크숍", "세미나", "웨비나", "해커톤"),
        "zh" to listOf("活动", "聚会", "会议", "研讨会", "讲座", "网络研讨会", "黑客松"),
        "ar" to listOf("فعالية", "لقاء", "مؤتمر", "ورشة", "ندوة", "ويبينار", "هاكاثون"),
        "hi" to listOf("कार्यक्रम", "मीटअप", "सम्मेलन", "कार्यशाला", "सेमिनार", "वेबिनार", "हैकाथॉन"),
        "id" to listOf("acara", "pertemuan", "konferensi", "lokakarya", "seminar", "webinar", "hackathon"),
        "th" to listOf("งาน", "มีตอัป", "การประชุม", "เวิร์กช็อป", "สัมมนา", "เว็บบินาร์", "แฮกกาธอน"),
        "vi" to listOf("sự kiện", "gặp gỡ", "hội nghị", "hội thảo", "seminar", "webinar", "hackathon"),
        "ru" to listOf("мероприятие", "встреча", "конференция", "воркшоп", "семинар", "вебинар", "хакатон")
    )

    private val attendanceSignals = listOf(
        "register", "registration", "RSVP", "tickets", "attend", "join",
        "sign up", "admission", "free entry", "book now", "reserve",
        "apply", "call for participants", "open to public"
    )

    fun aiTerms(language: String): List<String> =
        (aiCore + localizedAi[language].orEmpty()).distinct()

    fun participationTerms(language: String): List<String> =
        (universalParticipation + localizedParticipation[language].orEmpty()).distinct()

    fun attendanceTerms(): List<String> = attendanceSignals

    fun compactAi(language: String, max: Int = 8): String =
        aiTerms(language).take(max).joinToString(" ")

    fun compactParticipation(language: String, max: Int = 12): String =
        participationTerms(language).take(max).joinToString(" ")

    fun compactAttendance(max: Int = 7): String =
        attendanceSignals.take(max).joinToString(" ")
}
