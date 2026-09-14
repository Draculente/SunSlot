package de.sunslot.app.data.repository

import android.content.Context
import android.util.Log
import androidx.room.Room
import de.sunslot.app.data.api.BrightSkyApiClient
import de.sunslot.app.data.api.OpenMeteoApiClient
import de.sunslot.app.data.cache.CachedHourRecord
import de.sunslot.app.data.cache.SettingsDataStore
import de.sunslot.app.data.cache.WeatherCacheDatabase
import de.sunslot.app.data.model.FavoriteLocation
import de.sunslot.app.data.model.HourForecast
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class WeatherRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val api = BrightSkyApiClient()
    private val openMeteo = OpenMeteoApiClient()
    private val settings = SettingsDataStore(appContext)
    private val db by lazy {
        Room.databaseBuilder(
            appContext,
            WeatherCacheDatabase::class.java,
            "weather_cache.db"
        ).fallbackToDestructiveMigration().build()
    }
    private val dao by lazy { db.weatherCacheDao() }

    suspend fun getForecastForNextDays(days: Int = 3): List<HourForecast> {
        val coords = settings.coordinates.first()
        val today = LocalDate.now(ZoneId.systemDefault())
        // Bright Sky returns only the 00:00 slot of the requested last_date,
        // so we request one day further to cover all [days] full days.
        val endDate = today.plusDays(days.toLong())

        return try {
            val result = api.fetchHourlyForecast(coords.lat, coords.lon, today, endDate)
            if (result.records.isNotEmpty()) {
                cacheRecords(result.records, coords.lat, coords.lon)
                settings.updateLocationName(result.locationName)
                settings.updateLastUpdatedAt(System.currentTimeMillis())
                result.records
            } else {
                // Bright Sky covers the globe via ICON, but sunshine (and thus
                // scoring) is only fed within the German station network.
                Log.i(TAG, "Bright Sky returned no usable records, falling back to Open-Meteo")
                fallbackToOpenMeteo(coords.lat, coords.lon, days)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network fetch failed, falling back to cache", e)
            loadCachedRange(today.atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000,
                endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000,
                coords.lat, coords.lon)
        }
    }

    private suspend fun fallbackToOpenMeteo(lat: Double, lon: Double, days: Int): List<HourForecast> {
        return try {
            val records = openMeteo.fetchHourlyForecast(lat, lon, days)
            if (records.isEmpty()) throw IllegalStateException("Open-Meteo returned no records")
            cacheRecords(records, lat, lon)
            settings.updateLastUpdatedAt(System.currentTimeMillis())
            records
        } catch (e: Exception) {
            Log.w(TAG, "Open-Meteo fallback failed", e)
            throw e
        }
    }

    suspend fun coordinates() = settings.coordinates.first()
    suspend fun locationName() = settings.lastKnownLocationName.first()
    suspend fun lastUpdatedAt() = settings.lastUpdatedAt.first()
    suspend fun updateCoordinates(lat: Double, lon: Double) = settings.updateCoordinates(lat, lon)
    suspend fun updateLocationName(name: String) = settings.updateLocationName(name)
    fun coordinatesFlow() = settings.coordinates
    fun locationNameFlow() = settings.lastKnownLocationName
    fun favoritesFlow() = settings.favorites
    suspend fun updateFavorites(favorites: List<FavoriteLocation>) = settings.saveFavorites(favorites)
    fun lastUpdatedAtFlow() = settings.lastUpdatedAt

    private suspend fun cacheRecords(records: List<HourForecast>, lat: Double, lon: Double) {
        val now = System.currentTimeMillis()
        val entities = records.map {
            CachedHourRecord(
                timestamp = it.timestamp.toEpochSecond() * 1000,
                temperature = it.temperature,
                precipitation = it.precipitation,
                precipitationProbability = it.precipitationProbability,
                sunshine = it.sunshine,
                cloudCover = it.cloudCover,
                windSpeed = it.windSpeed,
                condition = it.condition,
                lat = lat,
                lon = lon,
                fetchedAt = now
            )
        }
        dao.insertRecords(entities)
        dao.deleteOlderThan(now - CACHE_TTL_MILLIS)
    }

    private suspend fun loadCachedRange(from: Long, until: Long, lat: Double, lon: Double): List<HourForecast> {
        return dao.getRecordsForRange(from, until, lat, lon).map { it.toHourForecast() }
    }

    private fun CachedHourRecord.toHourForecast(): HourForecast =
        HourForecast(
            timestamp = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()),
            temperature = temperature,
            precipitation = precipitation,
            precipitationProbability = precipitationProbability,
            sunshine = sunshine,
            cloudCover = cloudCover,
            windSpeed = windSpeed,
            condition = condition
        )

    companion object {
        private const val TAG = "WeatherRepository"
        private const val CACHE_TTL_MILLIS = 24L * 60 * 60 * 1000

        @Volatile
        private var instance: WeatherRepository? = null

        fun getInstance(context: Context): WeatherRepository {
            return instance ?: synchronized(this) {
                instance ?: WeatherRepository(context).also { instance = it }
            }
        }
    }
}
