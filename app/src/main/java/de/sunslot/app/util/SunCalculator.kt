package de.sunslot.app.util

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Approximate sunrise/sunset calculation for a given date and coordinates.
 * Uses a simplified astronomical algorithm accurate enough for outdoor scoring.
 */
object SunCalculator {

    private const val ZENITH_OFFICIAL = 90.83

    fun sunriseSunset(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Pair<ZonedDateTime, ZonedDateTime> {
        val dayOfYear = date.dayOfYear
        val longitudeHour = longitude / 15.0

        val approxTimeRise = dayOfYear + ((6.0 - longitudeHour) / 24.0)
        val approxTimeSet = dayOfYear + ((18.0 - longitudeHour) / 24.0)

        val sunMeanAnomalyRise = (0.9856 * approxTimeRise) - 3.289
        val sunMeanAnomalySet = (0.9856 * approxTimeSet) - 3.289

        val sunTrueLongitudeRise = normalizeLongitude(
            sunMeanAnomalyRise + (1.916 * sin(Math.toRadians(sunMeanAnomalyRise))) +
                    (0.020 * sin(Math.toRadians(2 * sunMeanAnomalyRise))) + 282.634
        )
        val sunTrueLongitudeSet = normalizeLongitude(
            sunMeanAnomalySet + (1.916 * sin(Math.toRadians(sunMeanAnomalySet))) +
                    (0.020 * sin(Math.toRadians(2 * sunMeanAnomalySet))) + 282.634
        )

        val rightAscensionRise = normalizeRightAscension(Math.toDegrees(kotlin.math.atan2(0.91764 * kotlin.math.tan(Math.toRadians(sunTrueLongitudeRise)), 1.0)))
        val rightAscensionSet = normalizeRightAscension(Math.toDegrees(kotlin.math.atan2(0.91764 * kotlin.math.tan(Math.toRadians(sunTrueLongitudeSet)), 1.0)))

        val lQuadrantRise = (sunTrueLongitudeRise / 90.0).toInt() * 90.0
        val raQuadrantRise = (rightAscensionRise / 90.0).toInt() * 90.0
        val rightAscensionRiseHours = (rightAscensionRise + (lQuadrantRise - raQuadrantRise)) / 15.0

        val lQuadrantSet = (sunTrueLongitudeSet / 90.0).toInt() * 90.0
        val raQuadrantSet = (rightAscensionSet / 90.0).toInt() * 90.0
        val rightAscensionSetHours = (rightAscensionSet + (lQuadrantSet - raQuadrantSet)) / 15.0

        val sinDecRise = 0.39782 * sin(Math.toRadians(sunTrueLongitudeRise))
        val cosDecRise = cos(asin(sinDecRise))
        val sinDecSet = 0.39782 * sin(Math.toRadians(sunTrueLongitudeSet))
        val cosDecSet = cos(asin(sinDecSet))

        val cosHourRise = (cos(Math.toRadians(ZENITH_OFFICIAL)) - (sinDecRise * sin(Math.toRadians(latitude)))) /
                (cosDecRise * cos(Math.toRadians(latitude)))
        val cosHourSet = (cos(Math.toRadians(ZENITH_OFFICIAL)) - (sinDecSet * sin(Math.toRadians(latitude)))) /
                (cosDecSet * cos(Math.toRadians(latitude)))

        val hourRise = (360.0 - Math.toDegrees(kotlin.math.acos(cosHourRise.coerceIn(-1.0, 1.0)))) / 15.0
        val hourSet = Math.toDegrees(kotlin.math.acos(cosHourSet.coerceIn(-1.0, 1.0))) / 15.0

        val localMeanTimeRise = hourRise + rightAscensionRiseHours - (0.06571 * approxTimeRise) - 6.622
        val localMeanTimeSet = hourSet + rightAscensionSetHours - (0.06571 * approxTimeSet) - 6.622

        val utcRise = normalizeTime(localMeanTimeRise - longitudeHour)
        val utcSet = normalizeTime(localMeanTimeSet - longitudeHour)

        val sunrise = date.atTime(toLocalTime(utcRise)).atZone(ZoneId.of("UTC")).withZoneSameInstant(zoneId)
        val sunset = date.atTime(toLocalTime(utcSet)).atZone(ZoneId.of("UTC")).withZoneSameInstant(zoneId)

        return sunrise to sunset
    }

    private fun normalizeLongitude(value: Double): Double {
        var v = value
        while (v >= 360.0) v -= 360.0
        while (v < 0.0) v += 360.0
        return v
    }

    private fun normalizeRightAscension(value: Double): Double {
        var v = value
        while (v >= 360.0) v -= 360.0
        while (v < 0.0) v += 360.0
        return v
    }

    private fun normalizeTime(value: Double): Double {
        var v = value
        while (v >= 24.0) v -= 24.0
        while (v < 0.0) v += 24.0
        return v
    }

    private fun toLocalTime(decimalHour: Double): java.time.LocalTime {
        val hour = decimalHour.toInt()
        val minute = ((decimalHour - hour) * 60).toInt()
        return java.time.LocalTime.of(hour, minute)
    }
}
