package de.sunslot.app.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class OpenMeteoApiClientTest {

    @Test
    fun `condition maps weather codes to labels`() {
        assertEquals("clear", conditionFor(0))
        assertEquals("partly-cloudy", conditionFor(1))
        assertEquals("cloudy", conditionFor(3))
        assertEquals("fog", conditionFor(48))
        assertEquals("drizzle", conditionFor(53))
        assertEquals("rain", conditionFor(61))
        assertEquals("snow", conditionFor(73))
        assertEquals("rain", conditionFor(80))
        assertEquals("thunderstorm", conditionFor(95))
        assertNull(conditionFor(-1))
    }

    @Test
    fun `hourly response maps to HourForecast with sunshine in hours`() {
        val hourly = OpenMeteoHourly(
            time = listOf("2026-09-14T06:00", "2026-09-14T07:00"),
            temperature_2m = listOf(20.0, 22.0),
            precipitation_probability = listOf(10.0, 30.0),
            precipitation = listOf(0.0, 0.5),
            sunshine_duration = listOf(3600.0, 1800.0),
            cloud_cover = listOf(20.0, 60.0),
            wind_speed_10m = listOf(15.0, 25.0),
            weather_code = listOf(1, 61)
        )

        val records = hourly.toForecasts(ZoneId.of("Asia/Ho_Chi_Minh"))

        assertEquals(2, records.size)
        val first = records[0]
        assertEquals("Asia/Ho_Chi_Minh", first.timestamp.zone.id)
        assertEquals(6, first.timestamp.hour)
        assertEquals(20.0, first.temperature, 1e-9)
        assertEquals(1.0, first.sunshine, 1e-9)
        assertEquals(0.5, records[1].sunshine, 1e-9)
        assertEquals("rain", records[1].condition)
    }

    @Test
    fun `missing slots are filtered out`() {
        val hourly = OpenMeteoHourly(
            time = listOf("2026-09-14T00:00", "2026-09-14T01:00"),
            temperature_2m = listOf(18.0)
        )
        val records = hourly.toForecasts(ZoneId.of("Europe/Berlin"))
        assertEquals(1, records.size)
        assertEquals(18.0, records.first().temperature, 1e-9)
        assertNull(records.first().condition)
    }
}