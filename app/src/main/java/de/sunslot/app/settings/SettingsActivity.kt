package de.sunslot.app.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.sunslot.app.data.api.GeocodingApiClient
import de.sunslot.app.data.api.GeocodingResult
import de.sunslot.app.data.model.FavoriteLocation
import de.sunslot.app.data.repository.WeatherRepository
import de.sunslot.app.worker.SyncWeatherWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                SettingsScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { WeatherRepository.getInstance(context) }
    val geocoder = remember { GeocodingApiClient() }

    val coords by repo.coordinatesFlow().collectAsState(initial = null)
    val locationName by repo.locationNameFlow().collectAsState(initial = "")
    val favorites by repo.favoritesFlow().collectAsState(initial = emptyList())

    var latText by remember(coords) { mutableStateOf(coords?.lat?.toString() ?: DEFAULT_LAT.toString()) }
    var lonText by remember(coords) { mutableStateOf(coords?.lon?.toString() ?: DEFAULT_LON.toString()) }
    var status by remember { mutableStateOf("") }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeocodingResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var pickedName by remember { mutableStateOf("") }

    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            searching = false
            results = emptyList()
            return@LaunchedEffect
        }
        searching = true
        delay(400)
        results = runCatching { geocoder.search(trimmed) }.getOrDefault(emptyList())
        searching = false
    }

    fun selectLocation(name: String, lat: Double, lon: Double) {
        pickedName = name
        latText = lat.toString()
        lonText = lon.toString()
        scope.launch {
            repo.updateCoordinates(lat, lon)
            SyncWeatherWorker.schedulePeriodic(context)
            status = "Standort gesetzt: $name"
        }
    }

    fun isCurrent(lat: Double, lon: Double): Boolean =
        coords?.let { abs(it.lat - lat) < 1e-4 && abs(it.lon - lon) < 1e-4 } ?: false

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Standort-Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(
                text = "Aktueller Standort",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (locationName.isNotBlank()) locationName
                else "Lübeck (53.8655, 10.6866)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Ort suchen",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Stadt oder Ort eingeben") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth()
            )
            if (searching) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Suche…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!searching && query.trim().length >= 2 && results.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Keine Orte gefunden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            results.forEach { result ->
                LocationRow(
                    title = result.name,
                    subtitle = result.detailLabel,
                    leadingIcon = Icons.Filled.LocationOn,
                    onClick = { selectLocation(result.name, result.latitude, result.longitude) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Favoriten",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = {
                        val lat = coords?.lat ?: return@OutlinedButton
                        val lon = coords?.lon ?: return@OutlinedButton
                        val name = pickedName.ifBlank { locationName.ifBlank { "Standort" } }
                        if (favorites.any { abs(it.lat - lat) < 1e-4 && abs(it.lon - lon) < 1e-4 }) {
                            status = "$name ist bereits in den Favoriten."
                        } else {
                            scope.launch {
                                repo.updateFavorites(favorites + FavoriteLocation(name, lat, lon))
                                status = "Favorit gespeichert: $name"
                            }
                        }
                    }
                ) {
                    Icon(
                        Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Als Favorit speichern")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (favorites.isEmpty()) {
                Text(
                    text = "Noch keine Favoriten.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            favorites.forEach { favorite ->
                LocationRow(
                    title = favorite.name,
                    subtitle = "%.4f, %.4f".format(favorite.lat, favorite.lon),
                    leadingIcon = Icons.Filled.Favorite,
                    onClick = { selectLocation(favorite.name, favorite.lat, favorite.lon) },
                    trailing = {
                        IconButton(onClick = {
                            scope.launch {
                                repo.updateFavorites(favorites.filter { it != favorite })
                                status = "Favorit entfernt: ${favorite.name}"
                            }
                        }) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = "Favorit entfernen",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    highlight = isCurrent(favorite.lat, favorite.lon)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Manuell per Koordinaten",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Passe Breiten- und Längengrad an, um einen anderen Ort zu verwenden.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = latText,
                onValueChange = { latText = it },
                label = { Text("Breitengrad (Lat)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = lonText,
                onValueChange = { lonText = it },
                label = { Text("Längengrad (Lon)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val lat = latText.replace(',', '.').toDoubleOrNull()
                    val lon = lonText.replace(',', '.').toDoubleOrNull()
                    if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                        status = "Ungültige Koordinaten"
                        return@Button
                    }
                    scope.launch {
                        repo.updateCoordinates(lat, lon)
                        SyncWeatherWorker.schedulePeriodic(context)
                        status = "Gespeichert. Widget wird aktualisiert."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Speichern & Widget aktualisieren")
            }

            if (status.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = status, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun LocationRow(
    title: String,
    subtitle: String,
    leadingIcon: ImageVector,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    highlight: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            tint = if (highlight) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

private const val DEFAULT_LAT = 53.8655
private const val DEFAULT_LON = 10.6866