package de.sunslot.app.data.api

import de.sunslot.app.data.model.BrightSkyRecord
import de.sunslot.app.data.model.BrightSkyWeatherResponse
import de.sunslot.app.data.model.HourForecast
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Ktor-based REST client for the Bright Sky (DWD) API.
 */
class BrightSkyApiClient {

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(jsonConfig) }
        install(Logging) { level = LogLevel.NONE }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
        expectSuccess = true
    }

    private val isoFormatter = DateTimeFormatter.ISO_DATE_TIME

    suspend fun fetchHourlyForecast(
        lat: Double,
        lon: Double,
        startDate: LocalDate,
        endDate: LocalDate
    ): ForecastResult {
        val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        val response: BrightSkyWeatherResponse = client.get(BASE_URL) {
            parameter("lat", lat)
            parameter("lon", lon)
            parameter("date", startDate.format(dateFormatter))
            parameter("last_date", endDate.format(dateFormatter))
            parameter("timezone", "Europe/Berlin")
            parameter("units", "dwd")
        }.body()

        val locationName = response.sources
            .minByOrNull { it.distance ?: Double.MAX_VALUE }
            ?.stationName ?: "Unbekannter Ort"

        return ForecastResult(
            records = response.records.mapNotNull { it.toHourForecast() },
            locationName = locationName
        )
    }

    data class ForecastResult(
        val records: List<HourForecast>,
        val locationName: String
    )

    private fun BrightSkyRecord.toHourForecast(): HourForecast? {
        val instant = runCatching { ZonedDateTime.parse(timestamp, isoFormatter) }
            .getOrNull()
            ?.withZoneSameInstant(ZoneId.systemDefault())
            ?: return null
        return HourForecast(
            timestamp = instant,
            temperature = temperature ?: return null,
            precipitation = precipitation ?: 0.0,
            precipitationProbability = precipitationProbability ?: 0.0,
            sunshine = sunshine ?: 0.0,
            cloudCover = cloudCover ?: 100.0,
            windSpeed = windSpeed ?: 0.0,
            condition = condition
        )
    }

    companion object {
        private const val BASE_URL = "https://api.brightsky.dev/weather"
    }
}
