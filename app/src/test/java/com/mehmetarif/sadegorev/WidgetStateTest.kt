package com.mehmetarif.sadegorev

import com.mehmetarif.sadegorev.data.*
import com.mehmetarif.sadegorev.widget.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class WidgetStateTest {
    @Test fun deadlineShowsDateWeekdayAndLocalTime(){
        val due=java.time.Instant.parse("2026-10-09T09:00:00Z").toEpochMilli()
        assertEquals("9 Eki Cum · 12:00",widgetDueLabel(due,java.time.ZoneId.of("Europe/Istanbul")))
        assertNull(widgetDueLabel(null))
    }

    @Test fun widgetListsAreNeverTruncatedByLegacyLimits() {
        val tasks=(1L..40L).map{TaskEntity(id=it,title="Görev $it")}
        assertEquals(40,taskWidgetSnapshot(tasks,WidgetSettings(filter=3,limit=3)).tasks.size)
        val courses=(1L..28L).map{CourseEntity(id=it,title="Ders $it",weekday=((it-1)%7+1).toInt(),startMinute=540,endMinute=600)}
        assertEquals(28,courseWidgetSnapshot(courses,WidgetSettings(courseDay=0,courseLimit=3)).courses.size)
        for(day in 1..7) {
            val selected=courseWidgetSnapshot(courses,WidgetSettings(courseDay=day))
            assertEquals(4,selected.total)
            assertTrue(selected.courses.all{it.weekday==day})
        }
    }
    @Test fun prioritySelectionUpdatesLiveListWithoutDroppingOtherTasks()=runBlocking {
        val tasks=MutableStateFlow(listOf(TaskEntity(id=1,title="Düşük",priority=0),TaskEntity(id=2,title="Orta",priority=1),TaskEntity(id=3,title="Yüksek",priority=2)))
        val prefs=MutableStateFlow(WidgetSettings(filter=3,sort=1))
        val updates=Channel<TaskWidgetSnapshot>(Channel.UNLIMITED)
        val job=launch{taskWidgetSnapshots(tasks,prefs).collect{updates.send(it)}}
        try {
            assertEquals(listOf(2,1,0),withTimeout(2000){updates.receive()}.tasks.map{it.priority})
            for(priority in listOf(2,1,0,-1)) {
                prefs.value=prefs.value.copy(priority=priority)
                val result=withTimeout(2000){updates.receive()}
                assertEquals(if(priority<0)3 else 1,result.pending)
                assertTrue(result.tasks.all{priority<0||it.priority==priority})
            }
        } finally {job.cancelAndJoin()}
    }

    @Test fun addingUndatedTaskUpdatesAnAlreadyRunningTodayWidget()=runBlocking {
        val tasks=MutableStateFlow(emptyList<TaskEntity>())
        val prefs=MutableStateFlow(WidgetSettings(filter=0))
        val updates=Channel<TaskWidgetSnapshot>(Channel.UNLIMITED)
        val job=launch {taskWidgetSnapshots(tasks,prefs).collect{updates.send(it)}}
        try {
            assertEquals(0,withTimeout(2000){updates.receive()}.pending)
            tasks.value=listOf(TaskEntity(id=12,title="Yeni görev"))
            val next=withTimeout(2000){updates.receive()}
            assertEquals("Yeni görev",next.tasks.single().title)
            assertEquals(1,next.pending)
        } finally {job.cancelAndJoin()}
    }
    @Test fun todayIncludesUndatedTasksButPreservesFutureAndOverdueFilters() {
        val zone=java.time.ZoneId.of("UTC")
        val now=java.time.Instant.parse("2026-10-06T12:00:00Z").toEpochMilli()
        val tasks=listOf(TaskEntity(id=1,title="Tarihsiz"),TaskEntity(id=2,title="Bugün",dueAt=now+3600000),TaskEntity(id=3,title="Yarın",dueAt=now+86400000),TaskEntity(id=4,title="Dün",dueAt=now-86400000))
        assertEquals(setOf(1L,2L),taskWidgetSnapshot(tasks,WidgetSettings(filter=0),now,zone).tasks.map{it.id}.toSet())
        assertEquals(listOf(3L),taskWidgetSnapshot(tasks,WidgetSettings(filter=1),now,zone).tasks.map{it.id})
        assertEquals(listOf(4L),taskWidgetSnapshot(tasks,WidgetSettings(filter=2),now,zone).tasks.map{it.id})
        assertEquals(4,taskWidgetSnapshot(tasks,WidgetSettings(filter=3),now,zone).pending)
    }

    @Test fun completedTaskLeavesAnAlreadyRunningWidget()=runBlocking {
        val tasks=MutableStateFlow(listOf(TaskEntity(id=1,title="Rapor")))
        val prefs=MutableStateFlow(WidgetSettings(filter=3))
        val updates=Channel<TaskWidgetSnapshot>(Channel.UNLIMITED)
        val job=launch { taskWidgetSnapshots(tasks,prefs).collect{updates.send(it)} }
        try {
            val first=withTimeout(2000){updates.receive()}
            assertEquals(1,first.pending)
            tasks.value=tasks.value.map{it.copy(completed=true)}
            val next=withTimeout(2000){updates.receive()}
            assertTrue(next.tasks.isEmpty());assertEquals(0,next.pending);assertEquals(1,next.completed)
        } finally {job.cancelAndJoin()}
    }
    @Test fun changingScopeUpdatesAnAlreadyRunningSchedule()=runBlocking {
        val courses=MutableStateFlow(listOf(CourseEntity(id=1,title="Pzt",weekday=1,startMinute=540,endMinute=600),CourseEntity(id=2,title="Sal",weekday=2,startMinute=600,endMinute=660)))
        val prefs=MutableStateFlow(WidgetSettings(courseScope=0))
        val updates=Channel<CourseWidgetSnapshot>(Channel.UNLIMITED)
        val job=launch {courseWidgetSnapshots(courses,prefs).collect{updates.send(it)}}
        try {
            withTimeout(2000){updates.receive()}
            prefs.value=prefs.value.copy(courseScope=1)
            val next=withTimeout(2000){updates.receive()}
            assertEquals(listOf("Pzt","Sal"),next.courses.map{it.title})
            courses.value=courses.value.filter{it.id!=1L}
            val deleted=withTimeout(2000){updates.receive()}
            assertEquals(1,deleted.total);assertEquals("Sal",deleted.courses.single().title)
        } finally {job.cancelAndJoin()}
    }
    @Test fun repeatedCompletionDoesNotBringTaskBack(){
        val task=TaskEntity(id=1,title="Tamamlanan",completed=true)
        assertTrue(taskWidgetSnapshot(listOf(task),WidgetSettings(filter=3)).tasks.isEmpty())
    }
}
