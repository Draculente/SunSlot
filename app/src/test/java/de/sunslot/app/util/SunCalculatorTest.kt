package de.sunslot.app.util

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SunCalculatorTest {

    @Test
    fun `sunrise is before sunset on a summer day in Luebeck`() {
        val (sunrise, sunset) = SunCalculator.sunriseSunset(
            date = LocalDate.of(2024, 6, 21),
            latitude = 53.8655,
            longitude = 10.6866,
            zoneId = ZoneId.of("Europe/Berlin")
        )
        assertTrue(sunrise.isBefore(sunset))
        assertTrue(sunrise.hour in 3..7)
        assertTrue(sunset.hour in 19..23)
    }

    @Test
    fun `sunrise is later in winter`() {
        val (sunriseWinter, _) = SunCalculator.sunriseSunset(
            date = LocalDate.of(2024, 12, 21),
            latitude = 53.8655,
            longitude = 10.6866,
            zoneId = ZoneId.of("Europe/Berlin")
        )
        val (sunriseSummer, _) = SunCalculator.sunriseSunset(
            date = LocalDate.of(2024, 6, 21),
            latitude = 53.8655,
            longitude = 10.6866,
            zoneId = ZoneId.of("Europe/Berlin")
        )
        assertTrue(sunriseWinter.hour > sunriseSummer.hour)
    }
}
