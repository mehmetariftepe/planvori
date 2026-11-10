package com.mehmetarif.sadegorev

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val TR: Locale = Locale.forLanguageTag("tr")

internal fun String.capitalizeTr() = replaceFirstChar { it.titlecase(TR) }

internal fun clockText(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

/** "Bugün · 17:00", "Yarın · 10:00", "Cuma · 14:30", "12 Kas · 09:00". */
internal fun friendlyDateTime(ms: Long, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): String {
    val dt = Instant.ofEpochMilli(ms).atZone(zone)
    val d = dt.toLocalDate()
    val day = when {
        d == today -> "Bugün"
        d == today.plusDays(1) -> "Yarın"
        d == today.minusDays(1) -> "Dün"
        d.isAfter(today) && d.isBefore(today.plusDays(7)) -> d.format(DateTimeFormatter.ofPattern("EEEE", TR)).capitalizeTr()
        d.year == today.year -> d.format(DateTimeFormatter.ofPattern("d MMM", TR))
        else -> d.format(DateTimeFormatter.ofPattern("d MMM yyyy", TR))
    }
    return "$day · " + dt.format(DateTimeFormatter.ofPattern("HH:mm", TR))
}

internal fun greeting(now: LocalTime = LocalTime.now()) = when (now.hour) {
    in 5..11 -> "Günaydın"
    in 12..17 -> "İyi günler"
    in 18..22 -> "İyi akşamlar"
    else -> "İyi geceler"
}

internal fun longDate(d: LocalDate) = d.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", TR))
