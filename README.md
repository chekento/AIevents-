# AIevents

**AIevents** is a native, multilingual Android app for discovering current AI events, conferences, meetups and local community gatherings worldwide.

## v0.2 highlights
- Global place/region search and 5–500 km radius
- Privacy-respecting **My location** helper
- Ahrensburg preset remains a practical default for southern Schleswig-Holstein + Hamburg
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
