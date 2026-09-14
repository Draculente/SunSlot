package de.sunslot.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object WeatherFormatting {

    private val dayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.GERMANY)
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMANY)

    fun dayLabel(date: LocalDate): String = when (date) {
        LocalDate.now() -> "Heute"
        LocalDate.now().plusDays(1) -> "Morgen"
        else -> dayFormatter.format(date).replaceFirstChar { it.uppercase() }
    }

    fun dateLabel(date: LocalDate): String = dateFormatter.format(date)

    fun hourRangeLabel(startHour: Int, endHour: Int): String =
        "$startHour–$endHour Uhr"

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY)

    fun lastUpdatedLabel(timestamp: Long): String {
        if (timestamp <= 0L) return "Noch nie aktualisiert"
        val time = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(timeFormatter)
        return "Aktualisiert: $time"
    }

    fun conditionIcon(condition: String?): String = when (condition?.lowercase()) {
        "clear", "sunny", "sun" -> "☀️"
        "cloudy", "overcast" -> "☁️"
        "partly-cloudy", "partly-cloudy-day", "partly-cloudy-night" -> "⛅"
        "rain", "drizzle", "sleet" -> "🌧️"
        "snow" -> "🌨️"
        "thunderstorm" -> "⛈️"
        "fog", "mist" -> "🌫️"
        else -> "🌤️"
    }

    fun tempString(celsius: Double): String = "${celsius.roundToOneDecimal()}°C"

    fun tempLabel(celsius: Double): String = "${kotlin.math.round(celsius).toInt()}°C"

    private fun Double.roundToOneDecimal(): String = String.format(Locale.GERMANY, "%.1f", this)
}
