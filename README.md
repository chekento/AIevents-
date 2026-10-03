# AIevents

**AIevents** is a native, multilingual Android app for discovering current AI events, conferences, meetups and local community gatherings worldwide.

## v0.2 highlights
- Global place/region search and 5–500 km radius
- Privacy-respecting **My location** helper
- Live federated web discovery instead of a bundled static catalogue
- Search categories: Agents, GenAI, ML, Data, Robotics, Business, Governance, Developer, Research, Community
- Keyword search plus Today/Week/Month/90-day presets, custom date horizon, free/paid/any pricing, online, confidence and sorting filters
- Sort by date, distance or source-data confidence
- Persistent full event favourites
- Internal OpenStreetMap view for events that publish coordinates
- One-tap Android calendar insertion including source URL
- Configurable periodic background event notifications
- Per-source enable/disable controls
- Compact or comfortable result cards
- Light/dark system theme
- UI languages: German, English, French, Spanish, Italian, Polish, Portuguese, Dutch, Swedish, Danish, Finnish, Turkish, Czech, Japanese, Korean and Chinese
- GitHub Actions APK builds on every push to main

## Discovery sources
The source registry currently targets:
- Global AI Community
- AI Tinkerers
- MLOps Community
- Meetup
- Luma
- Eventbrite
- Sessionize
- Pretalx
- Google Developer Groups
- Microsoft Reactor
- AWS Events
- NVIDIA Events
- Hugging Face
- IEEE
- ACM
- 10times
- confs.tech
- DEV Events
- eventyay
- general community / Stammtisch web discovery

AIevents uses public search discovery and parses schema.org/Event / JSON-LD when publishers provide it. A heuristic fallback can identify event pages with weaker metadata. Sources and original links remain visible.

## Freshness
Searches run against the live web on demand. Optional periodic WorkManager jobs can refresh event discovery in the background when notifications are enabled.

## Data quality
Not every publisher provides precise date, venue, price or coordinates. AIevents shows a confidence score rather than silently treating incomplete metadata as certain. Radius filtering is exact for events with published coordinates. AIevents also performs a bounded, cached venue-geocoding pass for a small number of missing coordinates; unresolved locations remain visibly unverifiable and retain their original source.

## Calendar export
Every event with a verified start date can be inserted into the user's Android calendar through the system calendar UI. AIevents does not require direct calendar-write permission.

## Notifications
Users can enable or disable background checks and choose an interval between 1 and 168 hours. Android notification permission is requested only when notifications are enabled.

## Privacy
AIevents requires no account. Search text and location names are sent to the public discovery/geocoding services necessary to perform the requested search. Device location permission is optional and only requested when the user taps **My location**.

## APK
The GitHub workflow **Build Android APK** builds and uploads:
- artifact: `AIevents-debug-apk`
- file: `app-debug.apk`

## Stack
Kotlin · Jetpack Compose · Material 3 · DataStore · WorkManager · OkHttp · Jsoup · schema.org/Event · JSON-LD · OpenStreetMap/osmdroid · Nominatim


## Strict freshness policy
AIevents v0.2.1 treats event freshness as a hard invariant:
- Events from calendar days before today are never shown.
- Multi-day events that started earlier remain visible only while their end date is today or later.
- Events without a verified date are hidden by default and can only be shown through an explicit user setting.
- Expired saved/favourite events are hidden as well.
- Background notifications use the same freshness rules.
- Unit tests run before every APK build to prevent regressions that could reintroduce past events.


## Hybrid central-index architecture (v0.3)
AIevents now uses two complementary discovery layers:

1. **Central index first** — a scheduled GitHub Actions job incrementally searches a rotating batch of global AI regions, parses structured Event/JSON-LD data, removes expired/cancelled/undated records, deduplicates results and writes `data/events-index.json`.
2. **Live supplement second** — the Android app displays matching central-index results immediately, then runs its live federated discovery and merges/deduplicates the results.

The index refresh workflow runs every six hours and rotates through the global region list rather than recrawling every city on every run. Pure index-data commits do not rebuild the APK.

### Index safety
- Central index never stores undated events.
- Past and cancelled events are discarded during indexing.
- The Android client re-applies freshness, location, price, category, keyword and confidence filters before showing indexed records.
- Index failures do not disable the app: live discovery remains the fallback.
- Live-search failures do not discard already loaded central-index results.

### Backend files
- `backend/regions.json` — rotating global region catalogue.
- `backend/index_events.py` — incremental indexer.
- `backend/event-index.schema.json` — machine-readable index contract.
- `data/events-index.json` — generated public event index.
- `.github/workflows/index-events.yml` — scheduled index refresh.


## v0.4 — precision search and promo-grade UX
AIevents v0.4 substantially rebuilds discovery and presentation:

- **Worldwide place autocomplete** backed by OpenStreetMap/Photon data. Selecting a place stores its latitude/longitude, not only a text label.
- **Strict geo relevance:** when event coordinates exist, radius matching is authoritative. Unverified off-location events no longer leak into local result lists.
- **Location regression tests** explicitly prevent cases such as New York searches returning Tokyo events. Safe aliases such as NYC are supported.
- **Three live search paths:** DuckDuckGo, Bing and Google discovery are merged for broad searches, with targeted source queries layered on top.
- **Expanded event ecosystem:** Meetup, Eventbrite, Luma/lu.ma, Partiful, Bevy, Sched, Splash, AllEvents, Sessionize, Pretalx, GDG, Microsoft Reactor, Global AI Community, AI Tinkerers, ODSC, MLconf, AI Camp, The AI Summit, Data Science Salon and more.
- **Independent official-provider discovery** for OpenAI, Google, Microsoft, Anthropic, Meta, AWS, NVIDIA and Hugging Face, with additional provider registry support.
- **Official provider events are visually separated and badged** instead of being mixed into the selected-city results.
- **Relevance-first ranking** combines distance, data confidence, source quality, official-provider status, keywords, freshness and corroborating sources.
- **Cross-source deduplication** merges multiple references to the same event and preserves a source-count signal.
- **Richer cards** display provider/source logos and event artwork when publishers expose it.
- **Event-type filters:** conference, meetup, workshop and hackathon, plus an official-provider-only switch.
- **Per-event reminders:** 1 day, 1 hour or 15 minutes before an event, alongside calendar insertion.
- **New adaptive launcher icon** and a refined purple/lavender Material 3 identity.
- Existing installs migrate automatically to relevance-first sorting and the expanded source catalogue.

### Local vs global official events
Local results remain geographically strict. Important official AI/LLM provider events are discovered independently and shown in their own clearly labeled section, so a global provider event never masquerades as a local city result.


## v0.5 — Provider Radar, LLMOps/MLOps and deeper source coverage

AIevents now separates local event discovery from global AI-provider monitoring.

### Provider Radar
- The **second bottom-navigation tab** is Provider Radar.
- The provider directory contains **242 AI/LLM ecosystems** across model vendors, cloud AI, agents, developer tools, LLMOps, MLOps, data platforms, vector databases, safety/evals, media AI, communities and research.
- Zuno is explicitly included without assigning an unverified official domain.
- LLMOps.Space and MLOps Community are first-class monitored sources.
- Provider events never appear above or between the normal local search results.
- Provider Radar supports **All / Online / In-person** event views.
- Selecting a provider triggers a deeper live search specifically for that provider.
- Provider cards and events prefer provider logos over ticket-platform branding.

### Continuous provider monitoring
The scheduled backend rotates through the provider catalogue every four hours. Provider searches include:
- official provider domains,
- general public web discovery,
- LinkedIn Events where publicly indexable,
- public X event/webinar references,
- YouTube livestream/event pages,
- Meetup,
- Luma / lu.ma.

Only events that can be parsed and verified are admitted to the central event index. Social posts that cannot be validated are not promoted as events.

### Direct high-value event hubs
The backend directly monitors event hubs including OpenAI community/forum sources, Anthropic, Microsoft Reactor, Databricks, Snowflake, LangChain, Weights & Biases, Arize, Cohere, Mistral, MongoDB, MLOps Community, LLMOps.Space, Global AI Community, AI Tinkerers, House of AI Hamburg and AI.HAMBURG.

### Deeper local search
Local discovery now uses a fast core pass and an automatic **Deep Search** pass when coverage is thin. Additional platforms include Devpost, HackerEarth, LinkedIn Events, Airmeet, Livestorm, Zoom Events, Cvent, Whova, Bizzabo, Goldcast, RingCentral Events, Swapcard and Brella, in addition to Meetup, Eventbrite, Luma, Partiful, Bevy, Sched, Splash, AllEvents, Sessionize and Pretalx.

The local and provider search systems remain deliberately separate: **city/radius relevance in Discover, global provider intelligence in Provider Radar.**


## v0.5.1 — global adaptive local discovery

The local Discover tab is now explicitly **location-agnostic and worldwide**. Hamburg, New York and other previously used cities are only test examples; no city is a privileged search target.

### How worldwide local discovery works
1. The user selects any place worldwide through autocomplete. AIevents stores the exact coordinates and country code.
2. Search language is derived from **English + the app language + the selected country's local language(s)** where known.
3. Queries combine AI-topic terms with participatory event forms such as meetup, conference, workshop, webinar, seminar, symposium, congress, forum, hackathon, datathon, bootcamp, masterclass, developer day, tech talk, panel, roundtable, fireside chat, networking, user group, study group, research seminar, demo day, roadshow, expo, showcase, build day, lab, office hours, AMA, livestream, launch event, community night and similar formats.
4. Registration/participation signals such as register, RSVP, tickets, attend, join, sign-up and admission are used as additional ranking evidence.
5. If the selected radius covers nearby cities or towns, AIevents resolves those places dynamically from OpenStreetMap/Overpass and searches them too. This allows a small town to discover relevant events in a nearby larger city without hard-coding any metropolitan area.
6. All candidates are finally checked against exact coordinates/radius when coordinates are available.
7. Events must have a strong AI focus; generic technology events with only incidental AI mentions are suppressed.
8. If the first source pass is thin, the app automatically performs a deeper source pass.

### Central index vs live coverage
The rotating central index is a **warm cache**, not a coverage boundary. Its global city list is geographically diverse and has no priority-city logic. A place does not need to be present in that list for the Android app to search it: live discovery is generated directly from the user's selected place anywhere in the world.


## v0.5.2 — event-first Provider Radar

The Provider Radar was redesigned around the event feed:
- compact always-visible radar summary,
- **Provider search**, **Filters** and **Provider directory** are independent collapsible sections,
- all three sections start collapsed so provider events receive most of the screen,
- provider search results remain horizontally browsable when explicitly opened,
- filter summaries remain visible while their controls are collapsed,
- online and in-person event counts are shown directly above the feed,
- the event list explicitly takes the remaining screen height and scrolls independently,
- selected-provider state remains visible without keeping the directory open,
- narrow-screen bottom navigation uses shorter labels and smaller typography to avoid clipping.

This is intended as the finishing UX pass for the current application architecture.


## v0.5.3 — compact Discover search and long-tail communities

- The large Discover search form is now a **collapsible search panel**.
- Starting a search automatically collapses the panel so results receive most of the screen.
- The collapsed header preserves a concise place / radius / keyword summary and can be reopened at any time.
- Filters collapse together with the form after starting a search.
- The results list explicitly owns the remaining screen height and scrolls independently.
- Long-tail discovery now looks harder for **small AI meetups, Stammtische, local chapters, clubs, user groups, university/student groups, research labs, journal clubs, coworking/makerspace meetups, innovation hubs and informal community gatherings**.
- Added discovery support for Humanitix, Ticket Tailor, Mobilizon, OpenCollective, Eventfrog, Eventfinda and publicly indexable Facebook Events.
- Search-engine long-tail queries also target LinkedIn Events, Facebook Events and OpenCollective community pages.
- Existing installations automatically receive the expanded source catalogue.
