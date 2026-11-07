package com.mehmetarif.sadegorev.reminder

import com.mehmetarif.sadegorev.data.CourseEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class CourseReminderTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    // 2026-11-09 bir Pazartesi.
    private fun at(day: Int, hour: Int, minute: Int) = ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone)

    @Test fun sameDayBeforeReminder() =
        assertEquals(at(9, 8, 50), nextCourseReminder(1, 9 * 60, 10, at(9, 8, 0)))

    @Test fun afterReminderMovesToNextWeek() =
        assertEquals(at(16, 8, 50), nextCourseReminder(1, 9 * 60, 10, at(9, 8, 50)))

    @Test fun laterWeekdayInSameWeek() =
        assertEquals(at(13, 13, 30), nextCourseReminder(5, 14 * 60, 30, at(9, 12, 0)))

    @Test fun earlierWeekdayWrapsToNextWeek() =
        assertEquals(at(16, 9, 0), nextCourseReminder(1, 9 * 60, 0, at(11, 10, 0)))

    @Test fun reminderCrossesMidnightToPreviousDay() =
        // Salı 00:10 dersi, 30 dk önce → Pazartesi 23:40
        assertEquals(at(9, 23, 40), nextCourseReminder(2, 10, 30, at(9, 20, 0)))

    @Test fun disabledReminderHasNoTime() =
        assertNull(nextCourseReminder(1, 9 * 60, NO_REMINDER, at(9, 8, 0)))

    @Test fun notificationTextIncludesTimeAndPlace() {
        val course = CourseEntity(id = 1, title = "Matematik", weekday = 1, startMinute = 540, endMinute = 600, location = "B12", reminderMinutes = 10)
        assertEquals("10 dk sonra başlıyor · 09:00–10:00 · B12", courseNotificationText(course))
        assertEquals("1 saat sonra başlıyor · 09:00–10:00", courseNotificationText(course.copy(location = "", reminderMinutes = 60)))
        assertEquals("Ders başladı · 09:00–10:00", courseNotificationText(course.copy(location = "", reminderMinutes = 0)))
    }

    @Test fun labels() {
        assertEquals("Hatırlatma yok", courseReminderLabel(NO_REMINDER))
        assertEquals("Ders başında", courseReminderLabel(0))
        assertEquals("Dersten 15 dk önce", courseReminderLabel(15))
    }
}
