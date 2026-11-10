package com.mehmetarif.sadegorev

import com.mehmetarif.sadegorev.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DayProgressTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private val today = LocalDate.of(2026, 11, 10)
    private fun ms(d: LocalDate, h: Int) = d.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
    private val now = ms(today, 12)
    private fun progress(vararg t: TaskEntity) = dayProgress(t.toList(), now, zone)

    @Test fun completingUndatedTaskIncreasesDone() {
        val open = TaskEntity(1, "Tarihsiz", dueAt = null)
        assertEquals(DayProgress(0, 1, 0, 0), progress(open))
        // Hata buydu: tamamlanınca 0/0 oluyordu
        assertEquals(DayProgress(1, 1, 0, 0), progress(open.copy(completed = true, completedAt = now)))
    }

    @Test fun completingTaskDueTodayIncreasesDone() {
        val t = TaskEntity(1, "Bugün", dueAt = ms(today, 17))
        assertEquals(DayProgress(0, 1, 0, 0), progress(t))
        assertEquals(DayProgress(1, 1, 0, 0), progress(t.copy(completed = true, completedAt = now)))
    }

    @Test fun overdueTaskCountsAndCompletingItToday() {
        val t = TaskEntity(1, "Dünden kalan", dueAt = ms(today.minusDays(1), 9))
        assertEquals(DayProgress(0, 1, 1, 0), progress(t))
        assertEquals(DayProgress(1, 1, 0, 0), progress(t.copy(completed = true, completedAt = now)))
    }

    @Test fun futureTasksAreUpcomingNotToday() {
        val t = TaskEntity(1, "Yarın", dueAt = ms(today.plusDays(1), 9))
        assertEquals(DayProgress(0, 0, 0, 1), progress(t))
    }

    @Test fun completedYesterdayIsNotCountedToday() {
        val t = TaskEntity(1, "Dün bitti", dueAt = null, completed = true, completedAt = ms(today.minusDays(1), 20))
        assertEquals(DayProgress(0, 0, 0, 0), progress(t))
    }

    @Test fun legacyCompletedTaskWithoutTimestampUsesDueDate() {
        assertEquals(DayProgress(1, 1, 0, 0), progress(TaskEntity(1, "Eski", dueAt = ms(today, 8), completed = true)))
        assertEquals(DayProgress(0, 0, 0, 0), progress(TaskEntity(2, "Eski tarihsiz", completed = true)))
    }
}
