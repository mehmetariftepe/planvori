package com.mehmetarif.sadegorev.reminder

import com.mehmetarif.sadegorev.data.CourseEntity
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** Ders hatırlatma seçenekleri: dakika → etiket. -1 = hatırlatma yok. */
internal val courseReminderOptions = listOf(
    NO_REMINDER to "Yok", 0 to "Ders başında", 5 to "5 dk önce", 10 to "10 dk önce",
    15 to "15 dk önce", 30 to "30 dk önce", 60 to "1 saat önce"
)
internal const val DEFAULT_COURSE_REMINDER = 10

/** Ders kartı ve düzenleme ekranı için özet, ör. "Dersten 10 dk önce" veya "30 dk önce · Ders başında". */
internal fun courseReminderSummary(offsets: List<Int>): String = when (offsets.size) {
    0 -> "Hatırlatma yok"
    1 -> courseReminderLabel(offsets[0])
    else -> offsets.sortedDescending().joinToString(" · ") { m -> when { m == 0 -> "Ders başında"; m % 60 == 0 -> "${m / 60} saat önce"; else -> "$m dk önce" } }
}

internal fun courseReminderLabel(minutes: Int): String =
    if (minutes < 0) "Hatırlatma yok" else courseReminderOptions.firstOrNull { it.first == minutes }?.let {
        if (minutes == 0) "Ders başında" else "Dersten ${it.second}"
    } ?: "Dersten $minutes dk önce"

/**
 * Haftalık tekrarlanan dersin [now] sonrasındaki ilk hatırlatma anı.
 * Gece yarısını geçen hatırlatmalar (ör. 00:10 dersine 30 dk önce) bir önceki güne düşer.
 */
internal fun nextCourseReminder(weekday: Int, startMinute: Int, minutesBefore: Int, now: ZonedDateTime): ZonedDateTime? {
    if (minutesBefore < 0 || weekday !in 1..7 || startMinute !in 0..1439) return null
    val base = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.of(weekday)))
    val time = LocalTime.of(startMinute / 60, startMinute % 60)
    for (week in 0L..2L) {
        val reminder = base.plusWeeks(week).atTime(time).atZone(now.zone).minusMinutes(minutesBefore.toLong())
        if (reminder.isAfter(now)) return reminder
    }
    return null
}

private fun clock(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

/** Bildirim metni, ör. "10 dk sonra başlıyor · 09:00–10:00 · B12". */
internal fun courseNotificationText(course: CourseEntity, minutes: Int = course.reminderMinutes): String = buildString {
    val m = minutes
    append(when { m <= 0 -> "Ders başladı"; m % 60 == 0 -> "${m / 60} saat sonra başlıyor"; else -> "$m dk sonra başlıyor" })
    append(" · ").append(clock(course.startMinute)).append("–").append(clock(course.endMinute))
    if (course.location.isNotBlank()) append(" · ").append(course.location)
}
