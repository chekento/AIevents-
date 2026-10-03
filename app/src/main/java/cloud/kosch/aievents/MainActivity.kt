package cloud.kosch.aievents

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import coil.compose.AsyncImage
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.delay
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

enum class Screen { DISCOVER, PROVIDERS, MAP, FAVORITES, SETTINGS }

data class UiState(
    val loading: Boolean = false,
    val snapshot: SearchSnapshot? = null,
    val error: String? = null
)

data class ProviderUiState(
    val loading: Boolean = false,
    val events: List<EventItem> = emptyList(),
    val indexedCount: Int = 0,
    val generatedAt: java.time.Instant? = null,
    val selectedProviderId: String? = null,
    val error: String? = null
)

enum class ProviderEventMode { ALL, ONLINE, IN_PERSON }

class EventViewModel : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var searchJob: Job? = null
    private val _providerState = MutableStateFlow(ProviderUiState())
    val providerState: StateFlow<ProviderUiState> = _providerState.asStateFlow()
    private var providerJob: Job? = null

    fun search(config: SearchConfig) {
        searchJob?.cancel()
        _state.value = _state.value.copy(loading = true, error = null)
        searchJob = viewModelScope.launch {
            val indexed = runCatching { CentralEventIndex.search(config) }
                .getOrElse { CentralEventIndex.Result(emptyList(), 0, null, it.message) }

            if (indexed.events.isNotEmpty()) {
                _state.value = UiState(
                    loading = true,
                    snapshot = SearchSnapshot(
                        config = config,
                        events = sortEvents(indexed.events, config.sortMode),
                        searchedSources = 0,
                        discoveredPages = 0,
                        warnings = listOfNotNull(indexed.warning),
                        indexedEvents = indexed.indexedCount,
                        indexGeneratedAt = indexed.generatedAt,
                        phase = "index",
                        featuredOfficialEvents = indexed.featuredOfficialEvents
                    )
                )
            }

            runCatching { LiveEventSearch.search(config) }
                .onSuccess { live ->
                    val merged = EventMerger.merge(indexed.events + live.events)
                    val official = EventMerger.merge(
                        indexed.featuredOfficialEvents + live.featuredOfficialEvents
                    ).map { EventRanker.rank(it, config) }
                        .sortedWith(
                            compareByDescending<EventItem> { it.relevanceScore }
                                .thenBy { it.start ?: java.time.Instant.MAX }
                        ).take(12)
                    _state.value = UiState(
                        loading = false,
                        snapshot = live.copy(
                            events = EventRanker.sort(merged, config),
                            warnings = (listOfNotNull(indexed.warning) + live.warnings).distinct(),
                            indexedEvents = indexed.indexedCount,
                            indexGeneratedAt = indexed.generatedAt,
                            phase = "hybrid",
                            featuredOfficialEvents = official
                        )
                    )
                }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) return@onFailure
                    if (indexed.events.isNotEmpty()) {
                        _state.value = UiState(
                            loading = false,
                            snapshot = _state.value.snapshot,
                            error = "Live supplement unavailable: " + (it.message ?: "network error")
                        )
                    } else {
                        _state.value = UiState(loading = false, error = it.message ?: "Search failed")
                    }
                }
        }
    }

    fun loadProviderRadar(force: Boolean = false) {
        if (_providerState.value.loading) return
        if (!force && _providerState.value.events.isNotEmpty()) return
        providerJob?.cancel()
        _providerState.value = _providerState.value.copy(loading = true, error = null)
        providerJob = viewModelScope.launch {
            runCatching { CentralEventIndex.providerEvents() }
                .onSuccess { result ->
                    _providerState.value = ProviderUiState(
                        loading = false,
                        events = result.events,
                        indexedCount = result.indexedCount,
                        generatedAt = result.generatedAt,
                        error = result.warning
                    )
                }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) return@onFailure
                    _providerState.value = ProviderUiState(
                        loading = false,
                        error = it.message ?: "Provider radar failed"
                    )
                }
        }
    }

    fun searchProvider(provider: ProviderEntry, language: String) {
        providerJob?.cancel()
        _providerState.value = _providerState.value.copy(
            loading = true,
            selectedProviderId = provider.id,
            error = null
        )
        providerJob = viewModelScope.launch {
            val indexed = runCatching { CentralEventIndex.providerEvents() }
                .getOrElse { CentralEventIndex.ProviderResult(emptyList(), 0, null, it.message) }
            val indexedForProvider = indexed.events.filter {
                ProviderCatalog.detect(it)?.id == provider.id ||
                    it.providerName.equals(provider.name, ignoreCase = true)
            }
            runCatching { LiveEventSearch.searchProvider(provider, language) }
                .onSuccess { live ->
                    _providerState.value = ProviderUiState(
                        loading = false,
                        events = EventMerger.merge(indexedForProvider + live)
                            .sortedBy { it.start ?: java.time.Instant.MAX },
                        indexedCount = indexed.indexedCount,
                        generatedAt = indexed.generatedAt,
                        selectedProviderId = provider.id,
                        error = indexed.warning
                    )
                }
                .onFailure {
                    _providerState.value = ProviderUiState(
                        loading = false,
                        events = indexedForProvider,
                        indexedCount = indexed.indexedCount,
                        generatedAt = indexed.generatedAt,
                        selectedProviderId = provider.id,
                        error = "Live provider search unavailable: " + (it.message ?: "network error")
                    )
                }
        }
    }

    fun clearProviderSelection() {
        _providerState.value = _providerState.value.copy(selectedProviderId = null)
        loadProviderRadar(force = true)
    }

    private fun sortEvents(events: List<EventItem>, mode: SortMode, config: SearchConfig? = null): List<EventItem> {
        if (config != null) return EventRanker.sort(events, config)
        return when (mode) {
            SortMode.RELEVANCE -> events.sortedWith(
                compareByDescending<EventItem> { it.relevanceScore }.thenBy { it.start ?: java.time.Instant.MAX }
            )
            SortMode.DISTANCE -> events.sortedWith(
                compareBy<EventItem> { it.distanceKm == null }
                    .thenBy { it.distanceKm ?: Double.MAX_VALUE }
                    .thenBy { it.start ?: java.time.Instant.MAX }
            )
            SortMode.CONFIDENCE -> events.sortedWith(
                compareByDescending<EventItem> { it.confidence }.thenBy { it.start ?: java.time.Instant.MAX }
            )
            SortMode.DATE -> events.sortedBy { it.start ?: java.time.Instant.MAX }
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
    val providerUi by vm.providerState.collectAsState()
    var screen by remember { mutableStateOf(Screen.DISCOVER) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(persisted) { settings = persisted }
    var bootSearchDone by remember { mutableStateOf(false) }
    LaunchedEffect(persisted.place) {
        if (!bootSearchDone && persisted.place.isNotBlank()) {
            bootSearchDone = true
            vm.search(persisted.toSearchConfig())
        }
    }
    LaunchedEffect(screen) {
        if (screen == Screen.PROVIDERS) vm.loadProviderRadar()
    }

    fun commit(next: AppSettings) {
        settings = next
        scope.launch { store.save(next) }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        LocationUtils.bestLocation(context)?.let { loc ->
            val place = LocationUtils.describe(context, loc)
            val next = settings.copy(
                place = place,
                placeLat = loc.latitude,
                placeLon = loc.longitude,
                placeId = "device:" + loc.latitude + ":" + loc.longitude
            )
            commit(next)
            vm.search(next.toSearchConfig())
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = Color(0xFFBEA1FF),
            secondary = Color(0xFFD6C3FF),
            tertiary = Color(0xFF9FD8FF),
            background = Color(0xFF120D1D),
            surface = Color(0xFF1B1428),
            surfaceVariant = Color(0xFF2A2039)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF6F4BB2),
            secondary = Color(0xFF7D5AC1),
            tertiary = Color(0xFF4D75B8),
            background = Color(0xFFFFF9FF),
            surface = Color(0xFFFCF7FF),
            surfaceVariant = Color(0xFFF0E6FA),
            primaryContainer = Color(0xFFE8DDFF),
            secondaryContainer = Color(0xFFEDE4FF)
        )
    }
    MaterialTheme(colorScheme = colors) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(42.dp),
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Public,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("AIevents", fontWeight = FontWeight.Black)
                                Text(t(settings.language, "tagline"), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    },
                    actions = {
                        val activeLoading = if (screen == Screen.PROVIDERS) providerUi.loading else ui.loading
                        if (activeLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        IconButton(
                            onClick = {
                                if (screen == Screen.PROVIDERS) vm.loadProviderRadar(force = true)
                                else if (settings.place.isNotBlank()) vm.search(settings.toSearchConfig())
                            },
                            enabled = if (screen == Screen.PROVIDERS) !providerUi.loading
                                else !ui.loading && settings.place.isNotBlank()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = t(settings.language, "refresh"))
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavItem(Screen.DISCOVER, screen, Icons.Default.Search, t(settings.language, "discover")) { screen = it }
                    NavItem(Screen.PROVIDERS, screen, Icons.Default.Hub, t(settings.language, "providers")) { screen = it }
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
                                    val next = settings.copy(
                                        place = place,
                                        placeLat = loc.latitude,
                                        placeLon = loc.longitude,
                                        placeId = "device:" + loc.latitude + ":" + loc.longitude
                                    )
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
                    Screen.PROVIDERS -> ProviderRadarScreen(
                        ui = providerUi,
                        settings = settings,
                        onRefresh = { vm.loadProviderRadar(force = true) },
                        onSearchProvider = { provider -> vm.searchProvider(provider, settings.language) },
                        onClearProvider = { vm.clearProviderSelection() },
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
    var suggestions by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }
    var placeLookupBusy by remember { mutableStateOf(false) }

    LaunchedEffect(settings.place, settings.placeId, settings.language) {
        if (settings.placeId.isNotBlank() || settings.place.trim().length < 2) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        placeLookupBusy = true
        suggestions = runCatching {
            LocationAutocomplete.search(settings.place, settings.language, 8)
        }.getOrDefault(emptyList())
        placeLookupBusy = false
    }

    Column(Modifier.fillMaxSize()) {
        ElevatedCard(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(
                    value = settings.place,
                    onValueChange = {
                        onSettings(
                            settings.copy(
                                place = it,
                                placeLat = null,
                                placeLon = null,
                                placeId = ""
                            )
                        )
                    },
                    label = { Text(t(settings.language, "place")) },
                    placeholder = { Text(t(settings.language, "place_hint")) },
                    leadingIcon = { Icon(Icons.Default.Place, null) },
                    trailingIcon = {
                        if (placeLookupBusy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else if (settings.placeId.isNotBlank()) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (suggestions.isNotEmpty()) {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column {
                            suggestions.take(8).forEach { suggestion ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSettings(
                                                settings.copy(
                                                    place = suggestion.displayName,
                                                    placeLat = suggestion.point.lat,
                                                    placeLon = suggestion.point.lon,
                                                    placeId = suggestion.id
                                                )
                                            )
                                            suggestions = emptyList()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(suggestion.displayName, fontWeight = FontWeight.SemiBold)
                                        val meta = listOf(suggestion.type, suggestion.countryCode)
                                            .filter { it.isNotBlank() }.joinToString(" · ")
                                        if (meta.isNotBlank()) {
                                            Text(meta, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
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
                    enabled = !ui.loading && settings.place.isNotBlank(),
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
            val allVisible = snap.events
            val sourceCount = allVisible.map { it.sourceName }.filter { it.isNotBlank() }.distinct().size
            val placeLabel = settings.place.substringBefore(",").ifBlank { t(settings.language, "worldwide") }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                Text(
                    allVisible.size.toString() + " " + t(settings.language, "events") + " · " +
                        placeLabel + " · " + sourceCount + " " + t(settings.language, "sources_short") + " · " +
                        t(settings.language, "updated") + " " +
                        snap.updatedAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (snap.phase == "index") t(settings.language, "live_supplement_running")
                    else t(settings.language, "hybrid_search_done"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        val events = ui.snapshot?.events.orEmpty()
        if (events.isEmpty() && !ui.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (settings.place.isBlank()) t(settings.language, "choose_place")
                    else t(settings.language, "no_events"),
                    modifier = Modifier.padding(24.dp)
                )
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
    Text(t(settings.language, "event_type"), fontWeight = FontWeight.SemiBold)
    EventType.entries.chunked(3).forEach { rowTypes ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            rowTypes.forEach { type ->
                FilterChip(
                    selected = settings.eventType == type,
                    onClick = { onSettings(settings.copy(eventType = type)) },
                    label = { Text(eventTypeLabel(type, settings.language)) }
                )
            }
        }
    }
    SettingsToggle(t(settings.language, "official_only"), settings.officialOnly) {
        onSettings(settings.copy(officialOnly = it))
    }
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
private fun EventSourceLogo(event: EventItem) {
    val domain = when {
        event.officialProvider && event.providerName.isNotBlank() ->
            OfficialProviders.logoDomain(event.providerName)
        event.sourceName.contains(".") -> event.sourceName
        else -> null
    }
    val logoUrl = domain?.let {
        "https://www.google.com/s2/favicons?domain=" + it + "&sz=128"
    }
    Surface(
        modifier = Modifier.size(42.dp),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp
    ) {
        if (logoUrl != null) {
            AsyncImage(
                model = logoUrl,
                contentDescription = event.providerName.ifBlank { event.sourceName },
                modifier = Modifier.padding(7.dp)
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    (event.providerName.ifBlank { event.sourceName }).take(2).uppercase(),
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun EventCard(event: EventItem, language: String, compact: Boolean, favorite: Boolean, onFavorite: () -> Unit) {
    val context = LocalContext.current
    var reminderDialog by remember { mutableStateOf(false) }
    var reminderMessage by remember { mutableStateOf<String?>(null) }
    val reminderPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (event.officialProvider)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(if (compact) 12.dp else 16.dp)) {
            if (event.officialProvider) {
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            t(language, "official_badge") +
                                if (event.providerName.isNotBlank()) " · " + event.providerName else ""
                        )
                    },
                    icon = { Icon(Icons.Default.Star, null, Modifier.size(16.dp)) }
                )
                Spacer(Modifier.height(4.dp))
            }
            if (!compact && event.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = event.imageUrl,
                    contentDescription = event.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(118.dp)
                        .padding(bottom = 8.dp)
                )
            }
            Row(verticalAlignment = Alignment.Top) {
                EventSourceLogo(event)
                Spacer(Modifier.width(10.dp))
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
                if (event.relevanceScore > 0) add("★ " + event.relevanceScore)
                if (event.sourceCount > 1) add(event.sourceCount.toString() + " sources")
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
                FilledTonalIconButton(
                    onClick = {
                        if (android.os.Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            reminderPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        reminderDialog = true
                    }
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = t(language, "reminder"))
                }
                FilledTonalIconButton(onClick = { addToCalendar(context, event) }) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = t(language, "calendar"))
                }
                Button(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(event.eventUrl.ifBlank { event.sourceUrl })))
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.OpenInNew, null)
                    Spacer(Modifier.width(4.dp))
                    Text(t(language, "source"))
                }
            }
            reminderMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (reminderDialog) {
                AlertDialog(
                    onDismissRequest = { reminderDialog = false },
                    title = { Text(t(language, "set_reminder")) },
                    text = {
                        Column {
                            listOf(
                                1440L to t(language, "one_day_before"),
                                60L to t(language, "one_hour_before"),
                                15L to t(language, "fifteen_min_before")
                            ).forEach { option ->
                                TextButton(
                                    onClick = {
                                        val ok = EventReminderWorker.schedule(context, event, option.first)
                                        reminderMessage = if (ok) t(language, "reminder_set") else t(language, "reminder_too_late")
                                        reminderDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(option.second)
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { reminderDialog = false }) {
                            Text(t(language, "cancel"))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FavoritesScreen(events: List<EventItem>, language: String, onRemove: (EventItem) -> Unit) {
    val now = java.time.Instant.now()
    val currentEvents = events.filter { event ->
        EventDateRules.isCurrentSavedEvent(event, now, ZoneId.systemDefault())
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

private fun eventTypeLabel(type: EventType, language: String): String = when (type) {
    EventType.ALL -> t(language, "all_types")
    EventType.CONFERENCE -> t(language, "conference")
    EventType.MEETUP -> t(language, "meetup")
    EventType.WORKSHOP -> t(language, "workshop")
    EventType.HACKATHON -> t(language, "hackathon")
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
        "tagline" to "Live AI events worldwide", "place_hint" to "e.g. Hamburg, Tokyo, New York", "choose_place" to "Enter a place or use My location to start.", "discover" to "Discover", "map" to "Map", "favorites" to "Saved",
        "settings" to "Settings", "refresh" to "Refresh", "place" to "Place / region", "my_location" to "My location",
        "filters" to "Filters", "keywords" to "Keywords", "search" to "Search AI events", "searching" to "Searching…",
        "events" to "events", "updated" to "updated", "sources_short" to "sources", "worldwide" to "Worldwide", "live_supplement_running" to "Central index loaded · live web search running…", "hybrid_search_done" to "Central index + live web search", "event_type" to "Event type", "official_only" to "Official providers only", "all_types" to "All", "conference" to "Conference", "meetup" to "Meetup", "workshop" to "Workshop", "hackathon" to "Hackathon", "official_events" to "Major official AI events", "official_events_sub" to "Independently discovered from AI and LLM providers", "official_badge" to "Official AI Provider Event", "local_results" to "Events for your selected area", "indexed" to "indexed", "index_loading" to "index loaded; live supplement", "no_events" to "No matching AI events found.", "radius" to "Radius", "category" to "Category",
        "online" to "Online", "price" to "Price", "any" to "Any", "free" to "Free", "paid" to "Paid", "today" to "Today", "week" to "Week", "month" to "Month", "time_horizon" to "Time horizon", "confidence" to "Data quality", "unverified_dates" to "Show events with unverified date",
        "sort" to "Sort", "source" to "Source", "calendar" to "Calendar", "favorite" to "Favorite", "reminder" to "Reminder", "set_reminder" to "Set reminder", "one_day_before" to "1 day before", "one_hour_before" to "1 hour before", "fifteen_min_before" to "15 minutes before", "reminder_set" to "Reminder scheduled", "reminder_too_late" to "This reminder time has already passed", "cancel" to "Cancel",
        "no_favorites" to "No saved events yet.", "mapped_events" to "events with map coordinates",
        "no_map" to "No coordinates are available for the current results.", "preferences" to "Preferences",
        "compact" to "Compact event cards", "notifications" to "Notifications", "notify_new" to "Periodic event alerts",
        "notification_interval" to "Refresh interval", "notify_only_new" to "Only notify for newly discovered events", "sources" to "Event sources", "all_sources" to "Enable all sources",
        "privacy_note" to "Search terms and location names are sent only to the public discovery/geocoding services required for the search.",
        "unknown_date" to "Date not verified"
    )
    val de = en + mapOf(
        "tagline" to "Aktuelle KI-Events weltweit", "place_hint" to "z. B. Hamburg, Tokio, New York", "choose_place" to "Ort eingeben oder „Mein Standort“ verwenden.", "discover" to "Entdecken", "map" to "Karte", "favorites" to "Gespeichert",
        "settings" to "Einstellungen", "refresh" to "Aktualisieren", "place" to "Ort / Region", "my_location" to "Mein Standort",
        "filters" to "Filter", "keywords" to "Stichwörter", "search" to "KI-Events suchen", "searching" to "Suche…",
        "events" to "Events", "updated" to "aktualisiert", "sources_short" to "Quellen", "worldwide" to "Weltweit", "live_supplement_running" to "Zentralindex geladen · Live-Websuche läuft…", "hybrid_search_done" to "Zentralindex + Live-Websuche", "event_type" to "Eventtyp", "official_only" to "Nur offizielle Anbieter", "all_types" to "Alle", "conference" to "Konferenz", "meetup" to "Meetup", "workshop" to "Workshop", "hackathon" to "Hackathon", "official_events" to "Wichtige offizielle KI-Events", "official_events_sub" to "Unabhängig bei KI- und LLM-Anbietern gefunden", "official_badge" to "Offizielles KI-Anbieter-Event", "local_results" to "Events im gewählten Gebiet", "indexed" to "im Index", "index_loading" to "Index geladen; Live-Ergänzung", "no_events" to "Keine passenden KI-Events gefunden.", "radius" to "Radius", "category" to "Kategorie",
        "online" to "Online", "price" to "Preis", "any" to "Alle", "free" to "Kostenlos", "paid" to "Kostenpflichtig", "today" to "Heute", "week" to "Woche", "month" to "Monat", "time_horizon" to "Zeitraum", "confidence" to "Datenqualität", "unverified_dates" to "Events ohne verifiziertes Datum anzeigen",
        "sort" to "Sortierung", "source" to "Quelle", "calendar" to "Kalender", "favorite" to "Favorit", "reminder" to "Erinnerung", "set_reminder" to "Erinnerung setzen", "one_day_before" to "1 Tag vorher", "one_hour_before" to "1 Stunde vorher", "fifteen_min_before" to "15 Minuten vorher", "reminder_set" to "Erinnerung geplant", "reminder_too_late" to "Dieser Erinnerungszeitpunkt ist bereits vorbei", "cancel" to "Abbrechen",
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
