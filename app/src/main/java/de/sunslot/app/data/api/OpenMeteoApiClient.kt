package de.sunslot.app.data.api

import de.sunslot.app.data.model.HourForecast
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Open-Meteo forecast client with global coverage. Used as a fallback when
 * the Bright Sky (DWD) API cannot provide usable data (e.g. sunshine outside
 * the German station network).
 */
class OpenMeteoApiClient {

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
        expectSuccess = true
    }

    suspend fun fetchHourlyForecast(
        lat: Double,
        lon: Double,
        days: Int
    ): List<HourForecast> {
        val response: OpenMeteoForecastResponse = client.get(BASE_URL) {
            parameter("latitude", lat)
            parameter("longitude", lon)
            parameter(
                "hourly",
                "temperature_2m,precipitation_probability,precipitation," +
                    "sunshine_duration,cloud_cover,wind_speed_10m,weather_code"
            )
            parameter("timezone", ZoneId.systemDefault().id)
            parameter("forecast_days", days)
        }.body()

        val zoneId = runCatching { ZoneId.of(response.timezone) }
            .getOrDefault(ZoneId.systemDefault())
        return response.hourly.toForecasts(zoneId)
    }

    companion object {
        private const val BASE_URL = "https://api.open-meteo.com/v1/forecast"
    }
}

@Serializable
data class OpenMeteoForecastResponse(
    val timezone: String = "",
    val hourly: OpenMeteoHourly = OpenMeteoHourly()
)

@Serializable
data class OpenMeteoHourly(
    val time: List<String> = emptyList(),
    val temperature_2m: List<Double> = emptyList(),
    val precipitation_probability: List<Double> = emptyList(),
    val precipitation: List<Double> = emptyList(),
    val sunshine_duration: List<Double> = emptyList(),
    val cloud_cover: List<Double> = emptyList(),
    val wind_speed_10m: List<Double> = emptyList(),
    val weather_code: List<Int> = emptyList()
)

internal fun OpenMeteoHourly.toForecasts(zoneId: ZoneId): List<HourForecast> =
    time.mapIndexedNotNull { index, time ->
        val timestamp = runCatching { LocalDateTime.parse(time).atZone(zoneId) }.getOrNull()
        val temperature = temperature_2m.getOrNull(index)
        if (timestamp == null || temperature == null) return@mapIndexedNotNull null

        HourForecast(
            timestamp = timestamp,
            temperature = temperature,
            precipitation = precipitation.getOrNull(index) ?: 0.0,
            precipitationProbability = precipitation_probability.getOrNull(index) ?: 0.0,
            // sunshine_duration is returned in seconds per hour.
            sunshine = (sunshine_duration.getOrNull(index) ?: 0.0) / 3600.0,
            cloudCover = cloud_cover.getOrNull(index) ?: 100.0,
            windSpeed = wind_speed_10m.getOrNull(index) ?: 0.0,
            condition = conditionFor(weather_code.getOrNull(index))
        )
    }

/** Maps WMO weather interpretation codes to the condition labels used by the app. */
internal fun conditionFor(code: Int?): String? = when (code) {
    0 -> "clear"
    1, 2 -> "partly-cloudy"
    3 -> "cloudy"
    in 45..48 -> "fog"
    in 51..57 -> "drizzle"
    in 61..67 -> "rain"
    in 71..77 -> "snow"
    in 80..82 -> "rain"
    in 85..86 -> "snow"
    in 95..99 -> "thunderstorm"
    else -> null
}