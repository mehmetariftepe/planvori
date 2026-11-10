package com.mehmetarif.sadegorev

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class FormattingTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private val today = LocalDate.of(2026, 11, 10) // Salı
    private fun ms(d: LocalDate, h: Int, m: Int = 0) = d.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    @Test fun relativeDays() {
        assertEquals("Bugün · 17:00", friendlyDateTime(ms(today, 17), zone, today))
        assertEquals("Yarın · 09:30", friendlyDateTime(ms(today.plusDays(1), 9, 30), zone, today))
        assertEquals("Dün · 12:00", friendlyDateTime(ms(today.minusDays(1), 12), zone, today))
        assertEquals("Cuma · 14:00", friendlyDateTime(ms(today.plusDays(3), 14), zone, today))
        assertEquals("20 Kas · 08:00", friendlyDateTime(ms(today.plusDays(10), 8), zone, today))
        assertEquals("5 Oca 2027 · 08:00", friendlyDateTime(ms(LocalDate.of(2027, 1, 5), 8), zone, today))
    }
    @Test fun greetings() {
        assertEquals("Günaydın", greeting(LocalTime.of(8, 0)))
        assertEquals("İyi akşamlar", greeting(LocalTime.of(20, 0)))
        assertEquals("İyi geceler", greeting(LocalTime.of(2, 0)))
    }
}
