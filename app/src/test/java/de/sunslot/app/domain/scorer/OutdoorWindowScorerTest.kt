package de.sunslot.app.domain.scorer

import de.sunslot.app.data.model.HourForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class OutdoorWindowScorerTest {

    @Test
    fun `scoreWindows returns best sunny afternoon window`() {
        val baseTime = ZonedDateTime.of(2024, 6, 15, 0, 0, 0, 0, ZoneId.systemDefault())
        val hours = (0..23).map { hourOffset ->
            val hour = hourOffset
            val temp = when {
                hour in 10..16 -> 22.0
                hour < 10 -> 14.0
                else -> 19.0
            }
            val cloudCover = if (hour in 12..15) 10.0 else 60.0
            val sunshine = if (hour in 12..15) 60.0 else 10.0
            HourForecast(
                timestamp = baseTime.plusHours(hourOffset.toLong()),
                temperature = temp,
                precipitation = 0.0,
                precipitationProbability = 0.0,
                sunshine = sunshine,
                cloudCover = cloudCover,
                windSpeed = 10.0,
                condition = if (hour in 12..15) "sunny" else "cloudy"
            )
        }

        val result = OutdoorWindowScorer.scoreWindows(hours, latitude = 53.8655, longitude = 10.6866)

        assertTrue(result.isNotEmpty())
        val day = result.first()
        assertEquals(12, day.startHour)
        assertTrue(day.endHour in 15..16)
        assertTrue(day.avgTemperature in 20.0..24.0)
    }

    @Test
    fun `rainy hours are excluded from best window`() {
        val baseTime = ZonedDateTime.of(2024, 6, 15, 0, 0, 0, 0, ZoneId.systemDefault())
        val hours = (0..23).map { hourOffset ->
            val hour = hourOffset
            val raining = hour in 13..16
            HourForecast(
                timestamp = baseTime.plusHours(hourOffset.toLong()),
                temperature = 21.0,
                precipitation = if (raining) 2.0 else 0.0,
                precipitationProbability = if (raining) 90.0 else 5.0,
                sunshine = if (raining) 0.0 else 50.0,
                cloudCover = if (raining) 100.0 else 20.0,
                windSpeed = 10.0,
                condition = if (raining) "rain" else "sunny"
            )
        }

        val result = OutdoorWindowScorer.scoreWindows(hours, latitude = 53.8655, longitude = 10.6866)
        assertTrue(result.isNotEmpty())
        val day = result.first()
        val startHour = day.startHour
        assertTrue(startHour != null && startHour !in 13..16)
    }

    @Test
    fun `day without rain-free window is still returned with medium ranges`() {
        val baseTime = ZonedDateTime.of(2024, 6, 15, 0, 0, 0, 0, ZoneId.systemDefault())
        val hours = (0..23).map { hourOffset ->
            val hour = hourOffset
            // Every contiguous hour pair contains an even (rainy) hour,
            // so no rain-free 2+ hour window can exist.
            val raining = hour % 2 == 0
            HourForecast(
                timestamp = baseTime.plusHours(hourOffset.toLong()),
                temperature = if (raining) 16.0 else 22.0,
                precipitation = if (raining) 2.0 else 0.0,
                precipitationProbability = if (raining) 90.0 else 5.0,
                sunshine = if (raining) 0.0 else 50.0,
                cloudCover = if (raining) 100.0 else 25.0,
                windSpeed = 10.0,
                condition = if (raining) "rain" else "sunny"
            )
        }

        val result = OutdoorWindowScorer.scoreWindows(hours, latitude = 53.8655, longitude = 10.6866)
        assertTrue(result.isNotEmpty())
        val day = result.first()
        assertNull(day.startHour)
        assertTrue("medium ranges should exist", day.partialRanges.isNotEmpty())
        // Average is computed from the medium (non-rainy) hours.
        assertEquals(22.0, day.avgTemperature, 1e-9)
    }

    @Test
    fun `day without any window uses daily average temperature`() {
        val baseTime = ZonedDateTime.of(2024, 6, 15, 0, 0, 0, 0, ZoneId.systemDefault())
        val hours = (0..23).map { hourOffset ->
            val temp = if (hourOffset < 12) 10.0 else 20.0
            HourForecast(
                timestamp = baseTime.plusHours(hourOffset.toLong()),
                temperature = temp,
                precipitation = 2.0,
                precipitationProbability = 90.0,
                sunshine = 0.0,
                cloudCover = 100.0,
                windSpeed = 40.0,
                condition = "rain"
            )
        }

        val result = OutdoorWindowScorer.scoreWindows(hours, latitude = 53.8655, longitude = 10.6866)
        assertTrue(result.isNotEmpty())
        val day = result.first()
        assertNull(day.startHour)
        assertTrue(day.partialRanges.isEmpty())
        assertEquals(15.0, day.avgTemperature, 1e-9)
    }

    @Test
    fun `rainy hours cannot be in best window even if they score highest`() {
        val baseTime = ZonedDateTime.of(2024, 6, 15, 0, 0, 0, 0, ZoneId.systemDefault())
        val hours = (0..23).map { hourOffset ->
            val hour = hourOffset
            val raining = hour in 12..16
            HourForecast(
                timestamp = baseTime.plusHours(hourOffset.toLong()),
                temperature = 21.0,
                precipitation = if (raining) 1.5 else 0.0,
                precipitationProbability = if (raining) 80.0 else 5.0,
                sunshine = if (raining) 60.0 else 20.0,
                cloudCover = if (raining) 10.0 else 60.0,
                windSpeed = 10.0,
                condition = if (raining) "rain" else "mostly-cloudy"
            )
        }

        val result = OutdoorWindowScorer.scoreWindows(hours, latitude = 53.8655, longitude = 10.6866)
        assertTrue(result.isNotEmpty())
        val day = result.first()
        val startHour = day.startHour
        assertTrue(startHour != null && startHour !in 12..16)
        assertTrue("best window must not contain rain hours", day.hours.none { it.precipitation > 0.1 })
        assertTrue("best window must not contain >=10% rain probability", day.hours.none { it.precipitationProbability >= 10.0 })
    }
}
