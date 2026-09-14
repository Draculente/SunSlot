package de.sunslot.app.data.cache

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.sunslot.app.data.model.FavoriteLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "weather_highlight_settings")

class SettingsDataStore(context: Context) {

    private val dataStore = context.dataStore
    private val json = Json { ignoreUnknownKeys = true }

    val coordinates: Flow<Coordinates> = dataStore.data.map { prefs ->
        Coordinates(
            lat = prefs[KEY_LAT] ?: DEFAULT_LAT,
            lon = prefs[KEY_LON] ?: DEFAULT_LON
        )
    }

    val lastKnownLocationName: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_LOCATION_NAME] ?: DEFAULT_LOCATION_NAME
    }

    val lastUpdatedAt: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_UPDATED_AT] ?: 0L
    }

    val favorites: Flow<List<FavoriteLocation>> = dataStore.data.map { prefs ->
        prefs[KEY_FAVORITES]?.let { raw ->
            runCatching { json.decodeFromString<List<FavoriteLocation>>(raw) }.getOrNull()
        } ?: emptyList()
    }

    suspend fun updateCoordinates(lat: Double, lon: Double) {
        dataStore.edit { prefs ->
            prefs[KEY_LAT] = lat
            prefs[KEY_LON] = lon
        }
    }

    suspend fun updateLocationName(name: String) {
        dataStore.edit { prefs ->
            prefs[KEY_LOCATION_NAME] = name
        }
    }

    suspend fun updateLastUpdatedAt(timestamp: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_UPDATED_AT] = timestamp
        }
    }

    suspend fun saveFavorites(favorites: List<FavoriteLocation>) {
        dataStore.edit { prefs ->
            prefs[KEY_FAVORITES] = json.encodeToString(favorites)
        }
    }

    data class Coordinates(
        val lat: Double,
        val lon: Double
    )

    companion object {
        private val KEY_LAT = doublePreferencesKey("lat")
        private val KEY_LON = doublePreferencesKey("lon")
        private val KEY_LOCATION_NAME = stringPreferencesKey("location_name")
        private val KEY_LAST_UPDATED_AT = longPreferencesKey("last_updated_at")
        private val KEY_FAVORITES = stringPreferencesKey("favorites")

        const val DEFAULT_LAT = 53.8655
        const val DEFAULT_LON = 10.6866
        const val DEFAULT_LOCATION_NAME = "Lübeck"
    }
}
