package cloud.kosch.aievents

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                AIeventsApp()
            }
        }
    }
}

data class UiState(
    val loading: Boolean = false,
    val snapshot: SearchSnapshot? = null,
    val error: String? = null
)

class EventViewModel : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun search(config: SearchConfig) {
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { LiveEventSearch.search(config) }
                .onSuccess { _state.value = UiState(snapshot = it) }
                .onFailure { _state.value = UiState(error = it.message ?: "Search failed") }
        }
    }
}

private data class Copy(
    val title: String,
    val subtitle: String,
    val place: String,
    val radius: String,
    val online: String,
    val refresh: String,
    val searching: String,
    val noEvents: String,
    val source: String,
    val organizer: String,
    val confidence: String,
    val distance: String,
    val open: String,
    val preset: String,
    val unknownDate: String
)

private fun copy(lang: String): Copy = when (lang) {
    "de" -> Copy("AIevents", "Aktuelle KI-Termine aus dem Web", "Ort / Region", "Radius", "Online-Events einbeziehen", "Aktualisieren", "Web wird durchsucht…", "Keine Termine gefunden. Ort/Radius ändern oder erneut suchen.", "Quelle", "Veranstalter", "Datenqualität", "Entfernung", "Ahrensburg + südliches SH + Hamburg", "Datum nicht verifiziert")
    "fr" -> Copy("AIevents", "Événements IA actuels du web", "Lieu / région", "Rayon", "Inclure les événements en ligne", "Actualiser", "Recherche sur le web…", "Aucun événement trouvé.", "Source", "Organisateur", "Qualité des données", "Distance", "Ahrensburg + sud du Schleswig-Holstein + Hambourg", "Date non vérifiée")
    "es" -> Copy("AIevents", "Eventos de IA actuales de la web", "Lugar / región", "Radio", "Incluir eventos online", "Actualizar", "Buscando en la web…", "No se encontraron eventos.", "Fuente", "Organizador", "Calidad de datos", "Distancia", "Ahrensburg + sur de Schleswig-Holstein + Hamburgo", "Fecha no verificada")
    "it" -> Copy("AIevents", "Eventi AI aggiornati dal web", "Luogo / regione", "Raggio", "Includi eventi online", "Aggiorna", "Ricerca sul web…", "Nessun evento trovato.", "Fonte", "Organizzatore", "Qualità dati", "Distanza", "Ahrensburg + Schleswig-Holstein meridionale + Amburgo", "Data non verificata")
    "pl" -> Copy("AIevents", "Aktualne wydarzenia AI z internetu", "Miejsce / region", "Promień", "Uwzględnij wydarzenia online", "Odśwież", "Przeszukiwanie internetu…", "Nie znaleziono wydarzeń.", "Źródło", "Organizator", "Jakość danych", "Odległość", "Ahrensburg + południowy Szlezwik-Holsztyn + Hamburg", "Data niezweryfikowana")
    else -> Copy("AIevents", "Current AI events from the web", "Place / region", "Radius", "Include online events", "Refresh", "Searching the web…", "No events found. Change place/radius or search again.", "Source", "Organizer", "Data quality", "Distance", "Ahrensburg + southern Schleswig-Holstein + Hamburg", "Date not verified")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AIeventsApp(vm: EventViewModel = viewModel()) {
    val ui by vm.state.collectAsState()
    var lang by remember { mutableStateOf(Locale.getDefault().language.takeIf { it in setOf("de","en","fr","es","it","pl") } ?: "en") }
    var place by remember { mutableStateOf("Ahrensburg, Schleswig-Holstein, Germany") }
    var radius by remember { mutableFloatStateOf(45f) }
    var includeOnline by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    val c = copy(lang)

    LaunchedEffect(Unit) {
        vm.search(SearchConfig(place, radius.toInt(), lang, includeOnline))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(c.title, fontWeight = FontWeight.Bold)
                        Text(c.subtitle, style = MaterialTheme.typography.labelMedium)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.Language, contentDescription = "Language") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            listOf("de" to "Deutsch", "en" to "English", "fr" to "Français", "es" to "Español", "it" to "Italiano", "pl" to "Polski").forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.second) },
                                    onClick = { lang = item.first; menu = false }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchPanel(
                copy = c,
                place = place,
                onPlace = { place = it },
                radius = radius,
                onRadius = { radius = it },
                includeOnline = includeOnline,
                onIncludeOnline = { includeOnline = it },
                loading = ui.loading,
                onPreset = {
                    place = "Ahrensburg, Schleswig-Holstein, Germany"
                    radius = 45f
                },
                onSearch = { vm.search(SearchConfig(place, radius.toInt(), lang, includeOnline)) }
            )

            when {
                ui.loading && ui.snapshot == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(c.searching)
                    }
                }
                ui.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(ui.error ?: "", modifier = Modifier.padding(24.dp))
                }
                else -> EventResults(ui.snapshot, c, ui.loading)
            }
        }
    }
}

@Composable
private fun SearchPanel(
    copy: Copy,
    place: String,
    onPlace: (String) -> Unit,
    radius: Float,
    onRadius: (Float) -> Unit,
    includeOnline: Boolean,
    onIncludeOnline: (Boolean) -> Unit,
    loading: Boolean,
    onPreset: () -> Unit,
    onSearch: () -> Unit
) {
    Card(Modifier.padding(12.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = place,
                onValueChange = onPlace,
                label = { Text(copy.place) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Text(copy.radius + ": " + radius.toInt() + " km", fontWeight = FontWeight.SemiBold)
            Slider(
                value = radius,
                onValueChange = onRadius,
                valueRange = 5f..300f,
                steps = 58
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = includeOnline, onCheckedChange = onIncludeOnline)
                Spacer(Modifier.width(8.dp))
                Text(copy.online)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPreset, modifier = Modifier.weight(1f)) {
                    Text(copy.preset, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Button(onClick = onSearch, enabled = !loading, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(copy.refresh)
                }
            }
        }
    }
}

@Composable
private fun EventResults(snapshot: SearchSnapshot?, copy: Copy, refreshing: Boolean) {
    if (snapshot == null) return
    Column(Modifier.fillMaxSize()) {
        if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
        val meta = snapshot.events.size.toString() + " events · " +
            snapshot.discoveredPages.toString() + " pages · " +
            snapshot.searchedSources.toString() + " search passes"
        Text(meta, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))

        snapshot.warnings.firstOrNull()?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        if (snapshot.events.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(copy.noEvents, modifier = Modifier.padding(24.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(snapshot.events, key = { it.stableKey }) { event ->
                    EventCard(event, copy)
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: EventItem, copy: Copy) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val dateText = event.start?.atZone(zone)?.format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy · HH:mm"))
        ?: copy.unknownDate

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(dateText, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)

            if (event.online) {
                Spacer(Modifier.height(4.dp))
                AssistChip(onClick = {}, label = { Text("Online") })
            }
            if (event.locality.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(event.locality, style = MaterialTheme.typography.bodyMedium)
            }
            event.distanceKm?.let {
                Text(copy.distance + ": " + String.format(Locale.getDefault(), "%.1f km", it), style = MaterialTheme.typography.labelMedium)
            }
            if (event.organizer.isNotBlank()) {
                Text(copy.organizer + ": " + event.organizer, style = MaterialTheme.typography.labelMedium)
            }
            if (event.price.isNotBlank()) {
                Text(event.price, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            if (event.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(event.description, maxLines = 5, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text(copy.source + ": " + event.sourceName, style = MaterialTheme.typography.labelSmall)
            Text(copy.confidence + ": " + event.confidence + "%", style = MaterialTheme.typography.labelSmall)

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val target = event.eventUrl.ifBlank { event.sourceUrl }
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(copy.open)
            }
        }
    }
}
