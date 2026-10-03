# AIevents

**AIevents** is a native Android app for discovering current AI events anywhere in the world.

## Current MVP
- Global free-text location/region search
- Radius filter from 5 to 300 km
- Ahrensburg preset: 45 km, intended to cover southern Schleswig-Holstein and Hamburg
- Live federated web discovery
- Event discovery across general web results plus Meetup, Luma and Eventbrite-focused searches
- JSON-LD / schema.org Event extraction where available
- Heuristic fallback for event pages without structured metadata
- Date, venue, organizer, price, online status and description
- Source domain and direct original-event link
- Coordinate-based radius filtering where event coordinates are published
- Duplicate suppression and a visible data-confidence score
- UI languages: German, English, French, Spanish, Italian and Polish
- Dark/light system theme
- Automatic GitHub Actions APK build

## Freshness model
AIevents searches the live web when the app starts and when the user taps refresh. It does not ship a stale hard-coded event catalogue.

Some event websites block automated access or omit structured location/date data. AIevents therefore shows source attribution and a confidence score instead of pretending incomplete data is certain.

## APK
Every push to `main` runs **Build Android APK** in GitHub Actions. Download the artifact named:

`AIevents-debug-apk`

The APK inside is:

`app-debug.apk`

## Architecture
- Kotlin
- Jetpack Compose / Material 3
- OkHttp
- Jsoup
- schema.org / JSON-LD event parsing
- OpenStreetMap Nominatim for resolving the search center

## Planned next steps
- Persistent favourites and saved searches
- Background refresh + notifications
- More source adapters and optional API-backed discovery providers
- Calendar export (.ics)
- Map view
- More languages
- Release signing and GitHub Releases
- Better geocoding for event venues that do not publish coordinates

## Privacy
The MVP does not require an account. Search terms are sent to public web/geocoding endpoints to perform discovery.

---
Created for the AIevents project.
