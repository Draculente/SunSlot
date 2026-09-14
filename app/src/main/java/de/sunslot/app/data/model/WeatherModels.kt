package de.sunslot.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response model for Bright Sky API weather endpoint.
 */
@Serializable
data class BrightSkyWeatherResponse(
    @SerialName("weather") val records: List<BrightSkyRecord> = emptyList(),
    @SerialName("sources") val sources: List<BrightSkySource> = emptyList()
)

@Serializable
data class BrightSkyRecord(
    @SerialName("timestamp") val timestamp: String,
    @SerialName("source_id") val sourceId: Int? = null,
    @SerialName("cloud_cover") val cloudCover: Double? = null,
    @SerialName("condition") val condition: String? = null,
    @SerialName("dew_point") val dewPoint: Double? = null,
    @SerialName("icon") val icon: String? = null,
    @SerialName("precipitation") val precipitation: Double? = null,
    @SerialName("pressure_msl") val pressureMsl: Double? = null,
    @SerialName("relative_humidity") val relativeHumidity: Int? = null,
    @SerialName("sunshine") val sunshine: Double? = null,
    @SerialName("temperature") val temperature: Double? = null,
    @SerialName("visibility") val visibility: Int? = null,
    @SerialName("wind_direction") val windDirection: Int? = null,
    @SerialName("wind_speed") val windSpeed: Double? = null,
    @SerialName("wind_gust_direction") val windGustDirection: Int? = null,
    @SerialName("wind_gust_speed") val windGustSpeed: Double? = null,
    @SerialName("precipitation_probability") val precipitationProbability: Double? = null,
    @SerialName("solar") val solar: Double? = null,
    @SerialName("fallback_source_ids") val fallbackSourceIds: Map<String, Int>? = null
)

@Serializable
data class BrightSkySource(
    @SerialName("id") val id: Int,
    @SerialName("dwd_station_id") val dwdStationId: String? = null,
    @SerialName("observation_type") val observationType: String? = null,
    @SerialName("lat") val lat: Double,
    @SerialName("lon") val lon: Double,
    @SerialName("height") val height: Double? = null,
    @SerialName("station_name") val stationName: String? = null,
    @SerialName("wmo_station_id") val wmoStationId: String? = null,
    @SerialName("first_record") val firstRecord: String? = null,
    @SerialName("last_record") val lastRecord: String? = null,
    @SerialName("distance") val distance: Double? = null
)

/**
 * Internal domain model for a single hour forecast.
 */
data class HourForecast(
    val timestamp: java.time.ZonedDateTime,
    val temperature: Double,
    val precipitation: Double,
    val precipitationProbability: Double,
    val sunshine: Double,
    val cloudCover: Double,
    val windSpeed: Double,
    val condition: String?
)

/**
 * Domain model representing the best outdoor window for a single day.
 * When no rain-free best window exists, [startHour] and [endHour] are null,
 * the timeline shows only the medium ranges (if any) and [avgTemperature]
 * falls back to the medium-range average, then the daily average.
 */
data class DayOutdoorWindow(
    val date: java.time.LocalDate,
    val startHour: Int?,
    val endHour: Int?,
    val avgTemperature: Double,
    val condition: String,
    val score: Double?,
    val hours: List<HourForecast>,
    val sunrise: java.time.ZonedDateTime,
    val sunset: java.time.ZonedDateTime,
    /**
     * Contiguous hour-ranges of "partially optimal" periods adjacent
     * to the best window, rendered dimmer than the bright segment.
     * Each IntRange is inclusive startHour..endHour-1.
     */
    val partialRanges: List<IntRange> = emptyList()
)

/**
 * Aggregated widget data.
 */
data class OutdoorWidgetData(
    val days: List<DayOutdoorWindow>,
    val locationName: String,
    val lastUpdated: java.time.ZonedDateTime
)
