package cloud.kosch.aievents

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AIeventsRoot() }
    }
}

enum class Screen { DISCOVER, MAP, FAVORITES, SETTINGS }

data class UiState(
    val loading: Boolean = false,
    val snapshot: SearchSnapshot? = null,
    val error: String? = null
)

class EventViewModel : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    fun search(config: SearchConfig) {
        searchJob?.cancel()
        _state.value = _state.value.copy(loading = true, error = null)
        searchJob = viewModelScope.launch {
            runCatching { LiveEventSearch.search(config) }
                .onSuccess { _state.value = UiState(snapshot = it) }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) return@onFailure
                    _state.value = _state.value.copy(loading = false, error = it.message ?: "Search failed")
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AIeventsRoot(vm: EventViewModel = viewModel()) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context.applicationContext) }
    val persisted by store.flow.collectAsState(initial = AppSettings(language = Locale.getDefault().language.ifBlank { "en" }))
    var settings by remember { mutableStateOf(persisted) }
    val ui by vm.state.collectAsState()
    var screen by remember { mutableStateOf(Screen.DISCOVER) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(persisted) { settings = persisted }
    LaunchedEffect(Unit) { vm.search(settings.toSearchConfig()) }

    fun commit(next: AppSettings) {
        settings = next
        scope.launch { store.save(next) }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        LocationUtils.bestLocation(context)?.let { loc ->
            commit(settings.copy(place = LocationUtils.describe(context, loc)))
            vm.search(settings.copy(place = LocationUtils.describe(context, loc)).toSearchConfig())
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("AIevents", fontWeight = FontWeight.Black)
                            Text(t(settings.language, "tagline"), style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    actions = {
                        if (ui.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        IconButton(onClick = { vm.search(settings.toSearchConfig()) }, enabled = !ui.loading) {
                            Icon(Icons.Default.Refresh, contentDescription = t(settings.language, "refresh"))
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavItem(Screen.DISCOVER, screen, Icons.Default.Search, t(settings.language, "discover")) { screen = it }
                    NavItem(Screen.MAP, screen, Icons.Default.Map, t(settings.language, "map")) { screen = it }
                    NavItem(Screen.FAVORITES, screen, Icons.Default.Favorite, t(settings.language, "favorites")) { screen = it }
                    NavItem(Screen.SETTINGS, screen, Icons.Default.Settings, t(settings.language, "settings")) { screen = it }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (screen) {
                    Screen.DISCOVER -> DiscoverScreen(
                        ui = ui,
                        settings = settings,
                        onSettings = { commit(it) },
                        onSearch = { vm.search(it.toSearchConfig()) },
                        onUseLocation = {
                            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                            if (coarse == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                LocationUtils.bestLocation(context)?.let { loc ->
                                    val place = LocationUtils.describe(context, loc)
                                    val next = settings.copy(place = place)
                                    commit(next)
                                    vm.search(next.toSearchConfig())
                                }
                            } else {
                                locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                            }
                        },
                        onFavorite = { event ->
                            val selected = event.stableKey in settings.favorites
                            val nextFavs = if (selected) settings.favorites - event.stableKey else settings.favorites + event.stableKey
                            val nextEvents = if (selected) settings.favoriteEvents.filterNot { it.stableKey == event.stableKey }
                            else (settings.favoriteEvents + event).distinctBy { it.stableKey }
                            commit(settings.copy(favorites = nextFavs, favoriteEvents = nextEvents))
                        }
                    )
                    Screen.MAP -> EventMapScreen(ui.snapshot?.events.orEmpty(), settings.language)
                    Screen.FAVORITES -> FavoritesScreen(
                        events = settings.favoriteEvents,
                        language = settings.language,
                        onRemove = { event ->
                            commit(settings.copy(
                                favorites = settings.favorites - event.stableKey,
                                favoriteEvents = settings.favoriteEvents.filterNot { it.stableKey == event.stableKey }
                            ))
                        }
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        settings = settings,
                        onChange = { next ->
                            commit(next)
                            EventRefreshWorker.schedule(context, next.notificationsEnabled, next.notificationHours)
                            if (next.notificationsEnabled && android.os.Build.VERSION.SDK_INT >= 33) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(screen: Screen, selected: Screen, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onSelect: (Screen) -> Unit) {
    NavigationBarItem(
        selected = screen == selected,
        onClick = { onSelect(screen) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label, maxLines = 1) }
    )
}

@Composable
private fun DiscoverScreen(
    ui: UiState,
    settings: AppSettings,
    onSettings: (AppSettings) -> Unit,
    onSearch: (AppSettings) -> Unit,
    onUseLocation: () -> Unit,
    onFavorite: (EventItem) -> Unit
) {
    var filtersOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ElevatedCard(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(
                    value = settings.place,
                    onValueChange = { onSettings(settings.copy(place = it)) },
                    label = { Text(t(settings.language, "place")) },
                    leadingIcon = { Icon(Icons.Default.Place, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onUseLocation, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.MyLocation, null)
                        Spacer(Modifier.width(6.dp))
                        Text(t(settings.language, "my_location"))
                    }
                    FilledTonalButton(onClick = { filtersOpen = !filtersOpen }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Tune, null)
                        Spacer(Modifier.width(6.dp))
                        Text(t(settings.language, "filters"))
                    }
                }
                OutlinedTextField(
                    value = settings.keywords,
                    onValueChange = { onSettings(settings.copy(keywords = it)) },
                    label = { Text(t(settings.language, "keywords")) },
                    placeholder = { Text("RAG, robotics, governance…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (filtersOpen) {
                    FilterPanel(settings, onSettings)
                }
                Button(
                    onClick = { onSearch(settings) },
                    enabled = !ui.loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Search, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (ui.loading) t(settings.language, "searching") else t(settings.language, "search"))
                }
            }
        }

        ui.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        ui.snapshot?.let { snap ->
            Text(
                snap.events.size.toString() + " " + t(settings.language, "events") + " · " +
                    snap.discoveredPages + " pages · " + snap.searchedSources + " queries",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        val events = ui.snapshot?.events.orEmpty()
        if (events.isEmpty() && !ui.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(t(settings.language, "no_events"), modifier = Modifier.padding(24.dp))
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(if (settings.compactCards) 6.dp else 10.dp)
            ) {
                items(events, key = { it.stableKey }) { event ->
                    EventCard(
                        event = event,
                        language = settings.language,
                        compact = settings.compactCards,
                        favorite = event.stableKey in settings.favorites,
                        onFavorite = { onFavorite(event) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterPanel(settings: AppSettings, onSettings: (AppSettings) -> Unit) {
    Text(t(settings.language, "radius") + ": " + settings.radiusKm + " km", fontWeight = FontWeight.SemiBold)
    Slider(
        value = settings.radiusKm.toFloat(),
        onValueChange = { onSettings(settings.copy(radiusKm = it.toInt())) },
        valueRange = 5f..500f
    )
    Text(t(settings.language, "category"), fontWeight = FontWeight.SemiBold)
    CategoryMenu(settings.category, settings.language) { onSettings(settings.copy(category = it)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(settings.includeOnline, { onSettings(settings.copy(includeOnline = it)) })
        Spacer(Modifier.width(8.dp))
        Text(t(settings.language, "online"))
    }
    PriceMenu(settings.priceMode, settings.language) { onSettings(settings.copy(priceMode = it)) }
    Text(t(settings.language, "time_horizon") + ": " + settings.futureDays + " d")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(1 to t(settings.language, "today"), 7 to t(settings.language, "week"), 30 to t(settings.language, "month"), 90 to "90d").forEach { pair ->
            FilterChip(
                selected = settings.futureDays == pair.first,
                onClick = { onSettings(settings.copy(futureDays = pair.first)) },
                label = { Text(pair.second) }
            )
        }
    }
    Slider(
        value = settings.futureDays.toFloat(),
        onValueChange = { onSettings(settings.copy(futureDays = it.toInt())) },
        valueRange = 7f..730f
    )
    SettingsToggle(t(settings.language, "unverified_dates"), settings.includeUnverifiedDates) {
        onSettings(settings.copy(includeUnverifiedDates = it))
    }
    Text(t(settings.language, "confidence") + ": ≥ " + settings.minConfidence + "%")
    Slider(
        value = settings.minConfidence.toFloat(),
        onValueChange = { onSettings(settings.copy(minConfidence = it.toInt())) },
        valueRange = 0f..100f
    )
    SortMenu(settings.sortMode, settings.language) { onSettings(settings.copy(sortMode = it)) }
}

@Composable
private fun CategoryMenu(value: EventCategory, language: String, onChange: (EventCategory) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(value.name.replace("_", " "))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            EventCategory.entries.forEach { cat ->
                DropdownMenuItem(text = { Text(cat.name.replace("_", " ")) }, onClick = { onChange(cat); open = false })
            }
        }
    }
}

@Composable
private fun PriceMenu(value: PriceMode, language: String, onChange: (PriceMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text(t(language, "price") + ": " + when (value) {
            PriceMode.ANY -> t(language, "any")
            PriceMode.FREE -> t(language, "free")
            PriceMode.PAID -> t(language, "paid")
        })
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        PriceMode.entries.forEach { mode ->
            val label = when (mode) {
                PriceMode.ANY -> t(language, "any")
                PriceMode.FREE -> t(language, "free")
                PriceMode.PAID -> t(language, "paid")
            }
            DropdownMenuItem(text = { Text(label) }, onClick = { onChange(mode); open = false })
        }
    }
}

@Composable
private fun SortMenu(value: SortMode, language: String, onChange: (SortMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(t(language, "sort") + ": ", style = MaterialTheme.typography.labelLarge)
        TextButton(onClick = { open = true }) { Text(value.name) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortMode.entries.forEach { mode ->
                DropdownMenuItem(text = { Text(mode.name) }, onClick = { onChange(mode); open = false })
            }
        }
    }
}

@Composable
private fun EventCard(event: EventItem, language: String, compact: Boolean, favorite: Boolean, onFavorite: () -> Unit) {
    val context = LocalContext.current
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(if (compact) 12.dp else 16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(formatDate(event, language), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = onFavorite) {
                    Icon(Icons.Default.Favorite, contentDescription = t(language, "favorite"),
                        tint = if (favorite) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                }
            }
            if (event.locality.isNotBlank()) Text(event.locality, style = MaterialTheme.typography.bodyMedium)
            val chips = buildList {
                if (event.online) add("Online")
                if (event.price.isNotBlank()) add(event.price)
                event.distanceKm?.let { add(String.format(Locale.getDefault(), "%.1f km", it)) }
                add(event.confidence.toString() + "%")
            }
            if (chips.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 5.dp)) {
                    chips.take(4).forEach { label -> SuggestionChip(onClick = {}, label = { Text(label) }) }
                }
            }
            if (!compact && event.description.isNotBlank()) {
                Text(event.description, maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(7.dp))
            }
            Text(t(language, "source") + ": " + event.sourceName, style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                FilledTonalButton(onClick = { addToCalendar(context, event) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CalendarMonth, null)
                    Spacer(Modifier.width(4.dp))
                    Text(t(language, "calendar"))
                }
                Button(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(event.eventUrl.ifBlank { event.sourceUrl })))
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.OpenInNew, null)
                    Spacer(Modifier.width(4.dp))
                    Text(t(language, "source"))
                }
            }
        }
    }
}

@Composable
private fun FavoritesScreen(events: List<EventItem>, language: String, onRemove: (EventItem) -> Unit) {
    val today = java.time.LocalDate.now(ZoneId.systemDefault())
    val currentEvents = events.filter { event ->
        EventDateRules.isCurrentSavedEvent(event, today, ZoneId.systemDefault())
    }
    if (currentEvents.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(t(language, "no_favorites"), modifier = Modifier.padding(24.dp))
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(currentEvents.sortedBy { it.start }, key = { it.stableKey }) { event ->
            EventCard(event, language, compact = false, favorite = true, onFavorite = { onRemove(event) })
        }
    }
}

@Composable
private fun EventMapScreen(events: List<EventItem>, language: String) {
    val context = LocalContext.current
    val mapped = events.filter { it.geo != null }
    Column(Modifier.fillMaxSize()) {
        Text(
            mapped.size.toString() + " / " + events.size + " " + t(language, "mapped_events"),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(12.dp)
        )
        if (mapped.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(t(language, "no_map")) }
        } else {
            AndroidView(
                factory = {
                    Configuration.getInstance().userAgentValue = context.packageName
                    MapView(context).apply {
                        setMultiTouchControls(true)
                        controller.setZoom(9.0)
                    }
                },
                update = { map ->
                    map.overlays.clear()
                    mapped.forEach { event ->
                        event.geo?.let { g ->
                            Marker(map).apply {
                                position = OsmGeoPoint(g.lat, g.lon)
                                title = event.title
                                snippet = event.locality
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            }.also { map.overlays.add(it) }
                        }
                    }
                    mapped.firstOrNull()?.geo?.let { map.controller.setCenter(OsmGeoPoint(it.lat, it.lon)) }
                    map.invalidate()
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun SettingsScreen(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(t(settings.language, "preferences"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            LanguageMenu(settings.language) { onChange(settings.copy(language = it)) }
        }
        item {
            SettingsToggle(t(settings.language, "compact"), settings.compactCards) { onChange(settings.copy(compactCards = it)) }
        }
        item {
            Text(t(settings.language, "notifications"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            SettingsToggle(t(settings.language, "notify_new"), settings.notificationsEnabled) {
                onChange(settings.copy(notificationsEnabled = it))
            }
            if (settings.notificationsEnabled) {
                SettingsToggle(t(settings.language, "notify_only_new"), settings.notifyOnlyNew) {
                    onChange(settings.copy(notifyOnlyNew = it))
                }
                Text(t(settings.language, "notification_interval") + ": " + settings.notificationHours + " h")
                Slider(
                    value = settings.notificationHours.toFloat(),
                    onValueChange = { onChange(settings.copy(notificationHours = it.toInt().coerceAtLeast(1))) },
                    valueRange = 1f..168f
                )
            }
        }
        item {
            Text(t(settings.language, "sources"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(settings.enabledSources.size.toString() + " / " + SourceRegistry.sources.size, style = MaterialTheme.typography.labelMedium)
        }
        items(SourceRegistry.sources, key = { it.id }) { source ->
            val enabled = source.id in settings.enabledSources
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(source.name, fontWeight = FontWeight.SemiBold)
                        if (source.domain.isNotBlank()) Text(source.domain, style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(enabled, {
                        val next = if (it) settings.enabledSources + source.id else settings.enabledSources - source.id
                        onChange(settings.copy(enabledSources = next))
                    })
                }
            }
        }
        item {
            OutlinedButton(onClick = { onChange(settings.copy(enabledSources = SourceRegistry.sources.map { it.id }.toSet())) }, modifier = Modifier.fillMaxWidth()) {
                Text(t(settings.language, "all_sources"))
            }
        }
        item {
            Text(t(settings.language, "privacy_note"), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsToggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(value, onChange)
    }
}

@Composable
private fun LanguageMenu(language: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val languages = listOf(
        "de" to "Deutsch", "en" to "English", "fr" to "Français", "es" to "Español",
        "it" to "Italiano", "pl" to "Polski", "pt" to "Português", "nl" to "Nederlands",
        "sv" to "Svenska", "da" to "Dansk", "fi" to "Suomi", "tr" to "Türkçe",
        "cs" to "Čeština", "ja" to "日本語", "ko" to "한국어", "zh" to "中文"
    )
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Language, null)
        Spacer(Modifier.width(8.dp))
        Text(languages.firstOrNull { it.first == language }?.second ?: language)
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        languages.forEach { pair ->
            DropdownMenuItem(text = { Text(pair.second) }, onClick = { onChange(pair.first); open = false })
        }
    }
}

private fun formatDate(event: EventItem, language: String): String {
    val start = event.start ?: return t(language, "unknown_date")
    return start.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy · HH:mm"))
}

private fun addToCalendar(context: android.content.Context, event: EventItem) {
    val start = event.start ?: return
    val end = event.end ?: start.plusSeconds(7200)
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.toEpochMilli())
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.toEpochMilli())
        putExtra(CalendarContract.Events.TITLE, event.title)
        putExtra(CalendarContract.Events.EVENT_LOCATION, event.locality)
        putExtra(CalendarContract.Events.DESCRIPTION, event.description + "\n\nSource: " + event.eventUrl)
    }
    context.startActivity(intent)
}

private fun t(lang: String, key: String): String {
    val en = mapOf(
        "tagline" to "Live AI events worldwide", "discover" to "Discover", "map" to "Map", "favorites" to "Saved",
        "settings" to "Settings", "refresh" to "Refresh", "place" to "Place / region", "my_location" to "My location",
        "filters" to "Filters", "keywords" to "Keywords", "search" to "Search live web", "searching" to "Searching…",
        "events" to "events", "no_events" to "No matching AI events found.", "radius" to "Radius", "category" to "Category",
        "online" to "Online", "price" to "Price", "any" to "Any", "free" to "Free", "paid" to "Paid", "today" to "Today", "week" to "Week", "month" to "Month", "time_horizon" to "Time horizon", "confidence" to "Data quality", "unverified_dates" to "Show events with unverified date",
        "sort" to "Sort", "source" to "Source", "calendar" to "Calendar", "favorite" to "Favorite",
        "no_favorites" to "No saved events yet.", "mapped_events" to "events with map coordinates",
        "no_map" to "No coordinates are available for the current results.", "preferences" to "Preferences",
        "compact" to "Compact event cards", "notifications" to "Notifications", "notify_new" to "Periodic event alerts",
        "notification_interval" to "Refresh interval", "notify_only_new" to "Only notify for newly discovered events", "sources" to "Event sources", "all_sources" to "Enable all sources",
        "privacy_note" to "Search terms and location names are sent only to the public discovery/geocoding services required for the search.",
        "unknown_date" to "Date not verified"
    )
    val de = en + mapOf(
        "tagline" to "Aktuelle KI-Events weltweit", "discover" to "Entdecken", "map" to "Karte", "favorites" to "Gespeichert",
        "settings" to "Einstellungen", "refresh" to "Aktualisieren", "place" to "Ort / Region", "my_location" to "Mein Standort",
        "filters" to "Filter", "keywords" to "Stichwörter", "search" to "Web live durchsuchen", "searching" to "Suche…",
        "events" to "Events", "no_events" to "Keine passenden KI-Events gefunden.", "radius" to "Radius", "category" to "Kategorie",
        "online" to "Online", "price" to "Preis", "any" to "Alle", "free" to "Kostenlos", "paid" to "Kostenpflichtig", "today" to "Heute", "week" to "Woche", "month" to "Monat", "time_horizon" to "Zeitraum", "confidence" to "Datenqualität", "unverified_dates" to "Events ohne verifiziertes Datum anzeigen",
        "sort" to "Sortierung", "source" to "Quelle", "calendar" to "Kalender", "favorite" to "Favorit",
        "no_favorites" to "Noch keine Events gespeichert.", "mapped_events" to "Events mit Kartenkoordinaten",
        "no_map" to "Für die aktuellen Treffer liegen keine Koordinaten vor.", "preferences" to "Einstellungen",
        "compact" to "Kompakte Eventkarten", "notifications" to "Benachrichtigungen", "notify_new" to "Regelmäßig nach Events suchen",
        "notification_interval" to "Aktualisierungsintervall", "notify_only_new" to "Nur neu entdeckte Events melden", "sources" to "Eventquellen", "all_sources" to "Alle Quellen aktivieren",
        "privacy_note" to "Suchbegriffe und Ortsnamen werden nur an die für Suche und Geocoding erforderlichen öffentlichen Dienste gesendet.",
        "unknown_date" to "Datum nicht verifiziert"
    )
    val fr = en + mapOf("discover" to "Découvrir", "map" to "Carte", "favorites" to "Favoris", "settings" to "Réglages", "place" to "Lieu / région", "search" to "Rechercher", "calendar" to "Calendrier", "source" to "Source")
    val es = en + mapOf("discover" to "Descubrir", "map" to "Mapa", "favorites" to "Guardados", "settings" to "Ajustes", "place" to "Lugar / región", "search" to "Buscar", "calendar" to "Calendario", "source" to "Fuente")
    val it = en + mapOf("discover" to "Scopri", "map" to "Mappa", "favorites" to "Salvati", "settings" to "Impostazioni", "place" to "Luogo / regione", "search" to "Cerca", "calendar" to "Calendario", "source" to "Fonte")
    val pl = en + mapOf("discover" to "Odkrywaj", "map" to "Mapa", "favorites" to "Zapisane", "settings" to "Ustawienia", "place" to "Miejsce / region", "search" to "Szukaj", "calendar" to "Kalendarz", "source" to "Źródło")
    val pt = en + mapOf("discover" to "Descobrir", "map" to "Mapa", "favorites" to "Guardados", "settings" to "Definições", "place" to "Local / região", "search" to "Pesquisar", "calendar" to "Calendário", "source" to "Fonte")
    val nl = en + mapOf("discover" to "Ontdekken", "map" to "Kaart", "favorites" to "Opgeslagen", "settings" to "Instellingen", "place" to "Plaats / regio", "search" to "Zoeken", "calendar" to "Agenda", "source" to "Bron")
    val sv = en + mapOf("discover" to "Upptäck", "map" to "Karta", "favorites" to "Sparade", "settings" to "Inställningar", "place" to "Plats / region", "search" to "Sök", "calendar" to "Kalender", "source" to "Källa")
    val da = en + mapOf("discover" to "Opdag", "map" to "Kort", "favorites" to "Gemte", "settings" to "Indstillinger", "place" to "Sted / region", "search" to "Søg", "calendar" to "Kalender", "source" to "Kilde")
    val fi = en + mapOf("discover" to "Löydä", "map" to "Kartta", "favorites" to "Tallennetut", "settings" to "Asetukset", "place" to "Paikka / alue", "search" to "Hae", "calendar" to "Kalenteri", "source" to "Lähde")
    val tr = en + mapOf("discover" to "Keşfet", "map" to "Harita", "favorites" to "Kaydedilenler", "settings" to "Ayarlar", "place" to "Yer / bölge", "search" to "Ara", "calendar" to "Takvim", "source" to "Kaynak")
    val cs = en + mapOf("discover" to "Objevit", "map" to "Mapa", "favorites" to "Uložené", "settings" to "Nastavení", "place" to "Místo / region", "search" to "Hledat", "calendar" to "Kalendář", "source" to "Zdroj")
    val ja = en + mapOf("discover" to "探す", "map" to "地図", "favorites" to "保存済み", "settings" to "設定", "place" to "場所 / 地域", "search" to "検索", "calendar" to "カレンダー", "source" to "情報源")
    val ko = en + mapOf("discover" to "탐색", "map" to "지도", "favorites" to "저장됨", "settings" to "설정", "place" to "장소 / 지역", "search" to "검색", "calendar" to "캘린더", "source" to "출처")
    val zh = en + mapOf("discover" to "发现", "map" to "地图", "favorites" to "已保存", "settings" to "设置", "place" to "地点 / 地区", "search" to "搜索", "calendar" to "日历", "source" to "来源")
    return when (lang) {
        "de" -> de; "fr" -> fr; "es" -> es; "it" -> it; "pl" -> pl; "pt" -> pt; "nl" -> nl;
        "sv" -> sv; "da" -> da; "fi" -> fi; "tr" -> tr; "cs" -> cs; "ja" -> ja; "ko" -> ko; "zh" -> zh; else -> en
    }[key] ?: key
}
