package com.mehmetarif.sadegorev.reminder

import com.mehmetarif.sadegorev.data.CourseEntity
import com.mehmetarif.sadegorev.data.TaskEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class ReminderChoiceTest {
    private val due = 2_000_000_000_000L
    private val min = 60000L
    private val zone = ZoneId.of("Europe/Istanbul")

    @Test fun newTaskDefaultsToDueTime() {
        val sel = initialReminderSelection(false, null, emptyList())
        assertEquals(listOf(due), reminderTimes(sel, due))
        assertEquals("Görev saatinde", reminderSummary(sel))
    }
    @Test fun taskWithoutDateHasNoAlarm() = assertTrue(reminderTimes(ReminderSelection(listOf(0, 15), listOf(due)), null).isEmpty())
    @Test fun existingDisabledReminderStaysDisabled() {
        val sel = initialReminderSelection(true, due, emptyList())
        assertEquals(0, sel.count); assertTrue(reminderTimes(sel, due + 1000).isEmpty())
        assertEquals("Hatırlatma yok", reminderSummary(sel))
    }
    @Test fun addingDateToUndatedTaskDefaultsToDueTime() = assertEquals(listOf(0), initialReminderSelection(true, null, emptyList()).offsets)
    @Test fun multiplePresetsAreIdentifiedAndFollowDateChanges() {
        val stored = listOf(due - 1440 * min, due - 60 * min, due - 15 * min, due)
        val sel = initialReminderSelection(true, due, stored)
        assertEquals(listOf(0, 15, 60, 1440), sel.offsets); assertTrue(sel.custom.isEmpty())
        assertEquals(stored.map { it + 3600000L }, reminderTimes(sel, due + 3600000L))
        assertEquals("4 hatırlatma", reminderSummary(sel))
    }
    @Test fun customTimesStayAbsoluteAndMixWithPresets() {
        val custom = due - 7 * min
        val sel = initialReminderSelection(true, due, listOf(custom, due - 30 * min))
        assertEquals(listOf(30), sel.offsets); assertEquals(listOf(custom), sel.custom)
        assertEquals(listOf(custom, due + 3600000L - 30 * min).sorted(), reminderTimes(sel, due + 3600000L))
    }
    @Test fun duplicateTimesCollapse() =
        assertEquals(listOf(due - 5 * min), reminderTimes(ReminderSelection(listOf(5), listOf(due - 5 * min)), due))
    @Test fun storageRoundTripAndLegacyFallback() {
        assertEquals(listOf(1L, 5L, 9L), parseReminderTimes(formatReminderTimes(listOf(9, 1, 5, 5))))
        assertTrue(parseReminderTimes("").isEmpty())
        assertEquals(listOf(42L), TaskEntity(1, "Eski", reminderAt = 42).reminderTimes())
        assertEquals(listOf(3L, 7L), TaskEntity(1, "Yeni", reminderAt = 3, reminders = "3,7").reminderTimes())
    }
    @Test fun notificationTextIsRelativeToDue() {
        assertEquals("Şimdi zamanı · Bugün · 06:33", taskNotificationText(due, due, zone))
        assertEquals("15 dk sonra · Bugün · 06:33", taskNotificationText(due, due - 15 * min, zone))
        assertEquals("1 saat sonra · Bugün · 06:33", taskNotificationText(due, due - 60 * min, zone))
        assertEquals("1 sa 30 dk sonra · Bugün · 06:33", taskNotificationText(due, due - 90 * min, zone))
        assertEquals("1 gün sonra · Yarın · 06:33", taskNotificationText(due, due - 1440 * min, zone))
        assertEquals("Zamanı geçti · Bugün · 06:33", taskNotificationText(due, due + 10 * min, zone))
        assertEquals("Görev zamanı", taskNotificationText(null, due, zone))
    }
    @Test fun courseReminderListAndSummary() {
        val course = CourseEntity(1, "Mat", 1, 540, 600, reminderMinutes = 10)
        assertEquals(listOf(10), course.reminderOffsets())
        assertEquals(listOf(30, 10, 0), course.copy(reminders = "0,30,10").reminderOffsets())
        assertTrue(course.copy(reminderMinutes = -1).reminderOffsets().isEmpty())
        assertEquals("30,10,0", formatCourseReminders(listOf(10, 0, 30, 10)))
        assertEquals("Hatırlatma yok", courseReminderSummary(emptyList()))
        assertEquals("Dersten 10 dk önce", courseReminderSummary(listOf(10)))
        assertEquals("1 saat önce · 10 dk önce · Ders başında", courseReminderSummary(listOf(0, 60, 10)))
        assertEquals("30 dk sonra başlıyor · 09:00–10:00", courseNotificationText(course, 30))
    }
}
