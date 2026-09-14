package de.sunslot.app.data.api

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

/**
 * Geocoding client for place search based on Open-Meteo (no API key required).
 */
class GeocodingApiClient {

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }
        expectSuccess = true
    }

    suspend fun search(query: String): List<GeocodingResult> {
        if (query.isBlank()) return emptyList()
        val response: GeocodingSearchResponse = client.get(BASE_URL) {
            parameter("name", query)
            parameter("count", 8)
            parameter("language", "de")
            parameter("format", "json")
        }.body()
        return response.results.orEmpty()
    }

    companion object {
        private const val BASE_URL = "https://geocoding-api.open-meteo.com/v1/search"
    }
}

@Serializable
data class GeocodingSearchResponse(
    val results: List<GeocodingResult>? = null
)

@Serializable
data class GeocodingResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null,
    val country_code: String? = null
) {
    val detailLabel: String
        get() = listOfNotNull(admin1, country).joinToString(", ")
}