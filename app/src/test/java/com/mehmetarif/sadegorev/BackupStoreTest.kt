package com.mehmetarif.sadegorev

import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupStoreTest {
    private fun archive(json:String,name:String="tasklist.json"):ByteArray {
        val out=ByteArrayOutputStream()
        ZipOutputStream(out).use{it.putNextEntry(ZipEntry(name));it.write(json.toByteArray());it.closeEntry()}
        return out.toByteArray()
    }
    private val task="""{"id":1,"title":"Türkçe görev ✓","description":"Not","dueAt":null,"priority":2,"reminderAt":null,"completed":true,"createdAt":12}"""
    private fun document(rows:String=task,settings:String="""{"app_theme":2,"widget_filter_order":"3,0,1,2"}""")="""{"format":"tasklist-backup","version":1,"tasks":[$rows],"settings":$settings}"""
    @Test fun restoresNewWidgetSelections(){
        val backup=BackupStore.read(archive(document(settings="""{"widget_priority":2,"schedule_widget_day":7}""")).inputStream())
        assertEquals(2,backup.settings.getInt("widget_priority"));assertEquals(7,backup.settings.getInt("schedule_widget_day"))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsInvalidWeekday(){BackupStore.read(archive(document(settings="""{"schedule_widget_day":8}""")).inputStream())}
    @Test fun readsTasksAndSettings(){val backup=BackupStore.read(archive(document()).inputStream());assertEquals("Türkçe görev ✓",backup.tasks.single().title);assertTrue(backup.tasks.single().completed);assertNull(backup.tasks.single().dueAt);assertEquals(2,backup.settings.getInt("app_theme"))}
    @Test fun acceptsEmptyBackup(){assertTrue(BackupStore.read(archive(document(rows="")).inputStream()).tasks.isEmpty())}
    @Test(expected=IllegalArgumentException::class) fun rejectsDuplicateIds(){BackupStore.read(archive(document(rows="$task,$task")).inputStream())}
    @Test(expected=IllegalArgumentException::class) fun rejectsZipTraversal(){BackupStore.read(archive(document(),"../tasklist.json").inputStream())}
    @Test(expected=IllegalArgumentException::class) fun rejectsInvalidTheme(){BackupStore.read(archive(document(settings="""{"app_theme":7}""")).inputStream())}
    @Test(expected=IllegalArgumentException::class) fun rejectsUnknownFormat(){BackupStore.read(archive(document().replace("tasklist-backup","other")).inputStream())}
    @Test fun readsCoursesFromVersionTwo(){
        val json="""{"format":"tasklist-backup","version":2,"tasks":[],"courses":[{"id":1,"title":"Matematik","weekday":2,"startMinute":540,"endMinute":600,"location":"B12","instructor":"","color":2}],"settings":{}}"""
        val course=BackupStore.read(archive(json).inputStream()).courses.single()
        assertEquals("Matematik",course.title);assertEquals(2,course.weekday);assertEquals(540,course.startMinute)
    }
    @Test fun readsCourseReminderAndDefaultsOldBackupsToNone(){
        val json="""{"format":"tasklist-backup","version":2,"tasks":[],"courses":[{"id":1,"title":"Fizik","weekday":3,"startMinute":600,"endMinute":660,"location":"","instructor":"","color":0,"reminderMinutes":15},{"id":2,"title":"Kimya","weekday":4,"startMinute":600,"endMinute":660,"location":"","instructor":"","color":0}],"settings":{}}"""
        val courses=BackupStore.read(archive(json).inputStream()).courses
        assertEquals(15,courses[0].reminderMinutes);assertEquals(-1,courses[1].reminderMinutes)
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsInvalidCourseHours(){
        val json="""{"format":"tasklist-backup","version":2,"tasks":[],"courses":[{"id":1,"title":"Ders","weekday":2,"startMinute":600,"endMinute":540,"location":"","instructor":"","color":0}],"settings":{}}"""
        BackupStore.read(archive(json).inputStream())
    }
    @Test fun readsScheduleWidgetSettings(){
        val backup=BackupStore.read(archive(document(settings="""{"schedule_widget_filter":1,"schedule_widget_count":0,"widget_theme":2}""")).inputStream())
        assertEquals(1,backup.settings.getInt("schedule_widget_filter"))
        assertEquals(0,backup.settings.getInt("schedule_widget_count"))
    }
    @Test fun preservesNewAppearancePreferences(){
        val backup=BackupStore.read(archive(document(settings="""{"app_theme":3,"app_accent":4}""")).inputStream())
        assertEquals(3,backup.settings.getInt("app_theme"));assertEquals(4,backup.settings.getInt("app_accent"))
    }
}
