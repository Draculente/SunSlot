package de.sunslot.app.domain.scorer

import de.sunslot.app.data.model.DayOutdoorWindow
import de.sunslot.app.data.model.HourForecast
import de.sunslot.app.util.SunCalculator
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.math.max
import kotlin.math.min

/**
 * Calculates the best contiguous outdoor activity window for a list of hourly forecasts.
 */
object OutdoorWindowScorer {

    private const val MIN_WINDOW_HOURS = 2
    private const val MAX_WINDOW_HOURS = 4

    private const val OPTIMAL_TEMP_MIN = 18.0
    private const val OPTIMAL_TEMP_MAX = 24.0
    private const val COLD_THRESHOLD = 12.0
    private const val HEAT_THRESHOLD = 28.0
    private const val WIND_THRESHOLD = 30.0
    private const val MEDIUM_SCORE = 45.0

    /**
     * Returns up to [days] daily outdoor windows based on hourly forecasts.
     * [latitude] and [longitude] are used to compute sunrise/sunset per day.
     */
    fun scoreWindows(
        hourly: List<HourForecast>,
        latitude: Double,
        longitude: Double,
        days: Int = 3
    ): List<DayOutdoorWindow> {
        if (hourly.isEmpty()) return emptyList()

        val byDay = hourly.groupBy { it.timestamp.toLocalDate() }
            .mapValues { (_, records) -> records.sortedBy { it.timestamp } }

        return byDay.keys.sorted().take(days).mapNotNull { date ->
            val records = byDay[date] ?: return@mapNotNull null
            val (sunrise, sunset) = SunCalculator.sunriseSunset(date, latitude, longitude)
            findBestWindow(date, records, sunrise, sunset)
        }
    }

    private fun findBestWindow(date: LocalDate, records: List<HourForecast>, sunrise: ZonedDateTime, sunset: ZonedDateTime): DayOutdoorWindow? {
        val sunriseHour = sunrise.hour
        val sunsetHour = sunset.hour
        val scored = records.map { it to scoreHour(it) }
        val candidates = mutableListOf<Triple<Int, Int, Double>>()

        for (windowSize in MAX_WINDOW_HOURS downTo MIN_WINDOW_HOURS) {
            for (i in 0..scored.size - windowSize) {
                val window = scored.subList(i, i + windowSize)
                val avg = window.sumOf { it.second } / windowSize
                // Penalize windows that start before civil sunrise or after sunset.
                val dayPortion = window.count { isDaylight(it.first.timestamp, sunriseHour, sunsetHour) }.toDouble() / windowSize
                if (dayPortion <= 0.0) continue
                // The "best" window must be completely rain-free; hours with
                // measurable precipitation or >= 10% probability are excluded.
                if (window.any { !isRainFree(it.first) }) continue
                candidates.add(Triple(i, i + windowSize - 1, avg * dayPortion))
            }
        }

        val best = candidates.maxByOrNull { it.third }
        val bestRange = best?.let { it.first..it.second }

        val partialRanges = if (bestRange != null) {
            findPartialRanges(scored, bestRange, sunriseHour, sunsetHour)
        } else {
            findMediumRanges(scored, sunriseHour, sunsetHour)
        }

        val avgTemp = when {
            bestRange != null -> records.subList(bestRange.first, bestRange.last + 1).map { it.temperature }.average()
            partialRanges.isNotEmpty() -> partialRanges
                .flatMap { r -> records.filter { it.timestamp.hour >= r.first && it.timestamp.hour < r.last } }
                .map { it.temperature }
                .average()
            else -> records.map { it.temperature }.average()
        }

        val windowRecords = bestRange?.let { records.subList(it.first, it.last + 1) } ?: emptyList()
        val inPartial = { record: HourForecast ->
            partialRanges.any { record.timestamp.hour >= it.first && record.timestamp.hour < it.last }
        }
        val conditionRecords = windowRecords
            .ifEmpty { records.filter(inPartial) }
            .ifEmpty { records }

        return DayOutdoorWindow(
            date = date,
            startHour = bestRange?.let { records[it.first].timestamp.hour },
            endHour = bestRange?.let { records[it.last].timestamp.hour + 1 },
            avgTemperature = avgTemp,
            condition = dominantCondition(conditionRecords),
            score = best?.third,
            hours = windowRecords,
            sunrise = sunrise,
            sunset = sunset,
            partialRanges = partialRanges
        )
    }

    /**
     * Finds contiguous runs of hours that are still okay ("half-optimal"),
     * i.e. clearly above a mediocre threshold but not part of the best window.
     * These runs may sit next to the best window (extending it visually) or be
     * separated from it by poorer hours (interrupted periods).
     */
    private fun findPartialRanges(
        scored: List<Pair<HourForecast, Double>>,
        bestRange: IntRange,
        sunriseHour: Int,
        sunsetHour: Int
    ): List<IntRange> {
        val bestAvg = scored.subList(bestRange.first, bestRange.last + 1)
            .map { it.second }
            .average()
        val partialThreshold = max(bestAvg * 0.6, 45.0)

        val runs = mutableListOf<IntRange>()
        var runStart: Int? = null

        scored.forEachIndexed { index, (hour, score) ->
            val isCandidate = index !in bestRange &&
                isDaylight(hour.timestamp, sunriseHour, sunsetHour) &&
                score >= partialThreshold
            if (isCandidate) {
                if (runStart == null) runStart = index
            } else {
                runStart?.let { start ->
                    runs.add(start..index - 1)
                    runStart = null
                }
            }
        }
        runStart?.let { start -> runs.add(start..scored.lastIndex) }

        return runs.map { run ->
            scored[run.first].first.timestamp.hour..(scored[run.last].first.timestamp.hour + 1)
        }
    }

    /**
     * For days without a rain-free best window: contiguous daylight hours that
     * are still "medium" (fixed, decent score), rendered dimmed only.
     */
    private fun findMediumRanges(
        scored: List<Pair<HourForecast, Double>>,
        sunriseHour: Int,
        sunsetHour: Int
    ): List<IntRange> {
        val runs = mutableListOf<IntRange>()
        var runStart: Int? = null

        scored.forEachIndexed { index, (hour, score) ->
            val isMedium = isDaylight(hour.timestamp, sunriseHour, sunsetHour) && score >= MEDIUM_SCORE
            if (isMedium) {
                if (runStart == null) runStart = index
            } else {
                runStart?.let { start ->
                    runs.add(start..index - 1)
                    runStart = null
                }
            }
        }
        runStart?.let { start -> runs.add(start..scored.lastIndex) }

        return runs.map { run ->
            scored[run.first].first.timestamp.hour..(scored[run.last].first.timestamp.hour + 1)
        }
    }

    private fun scoreHour(hour: HourForecast): Double {
        var score = 50.0

        // Precipitation and probability: strong penalty / exclusion.
        when {
            hour.precipitation > 0.5 || hour.precipitationProbability > 70 -> score -= 80.0
            hour.precipitation > 0.1 || hour.precipitationProbability > 40 -> score -= 35.0
            hour.precipitationProbability > 20 -> score -= 10.0
        }

        // Temperature optimum.
        score += when {
            hour.temperature in OPTIMAL_TEMP_MIN..OPTIMAL_TEMP_MAX -> 25.0
            hour.temperature < COLD_THRESHOLD -> -30.0 + (hour.temperature - COLD_THRESHOLD) * 2
            hour.temperature > HEAT_THRESHOLD -> -25.0 - (hour.temperature - HEAT_THRESHOLD) * 2
            hour.temperature < OPTIMAL_TEMP_MIN -> ((hour.temperature - COLD_THRESHOLD) / (OPTIMAL_TEMP_MIN - COLD_THRESHOLD)) * 15
            else -> ((HEAT_THRESHOLD - hour.temperature) / (HEAT_THRESHOLD - OPTIMAL_TEMP_MAX)) * 15
        }

        // Wind.
        if (hour.windSpeed > WIND_THRESHOLD) {
            score -= (hour.windSpeed - WIND_THRESHOLD) * 1.5
        }

        // Sunshine & cloud cover.
        if (hour.sunshine >= 30) score += 15.0
        if (hour.sunshine >= 55) score += 10.0
        when {
            hour.cloudCover < 20 -> score += 12.0
            hour.cloudCover < 50 -> score += 5.0
            hour.cloudCover > 80 -> score -= 10.0
        }

        return max(0.0, min(100.0, score))
    }

    private fun isDaylight(timestamp: ZonedDateTime, sunriseHour: Int, sunsetHour: Int): Boolean {
        val hour = timestamp.hour
        return hour in sunriseHour..sunsetHour
    }

    private fun isRainFree(hour: HourForecast): Boolean =
        hour.precipitation <= 0.1 && hour.precipitationProbability < 10.0

    private fun dominantCondition(hours: List<HourForecast>): String {
        val conditions = hours.mapNotNull { it.condition }
        if (conditions.isEmpty()) return "Unknown"
        return conditions.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: conditions.first()
    }
}
