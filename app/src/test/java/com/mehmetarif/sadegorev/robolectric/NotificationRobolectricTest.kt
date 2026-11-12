package com.mehmetarif.sadegorev.robolectric

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mehmetarif.sadegorev.AppPreferences
import com.mehmetarif.sadegorev.data.CourseEntity
import com.mehmetarif.sadegorev.data.TaskEntity
import com.mehmetarif.sadegorev.reminder.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NotificationRobolectricTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val notifications get() = shadowOf(app.getSystemService(NotificationManager::class.java))
    private val alarms get() = shadowOf(app.getSystemService(AlarmManager::class.java))

    @Before fun grant() { shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS); ReminderNotifications.createChannels(app) }

    @Test fun channelsAreHighImportanceWithVibration() {
        val channel = app.getSystemService(NotificationManager::class.java).getNotificationChannel(TASK_REMINDER_CHANNEL)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance); assertTrue(channel.shouldVibrate())
        assertNotNull(app.getSystemService(NotificationManager::class.java).getNotificationChannel(COURSE_REMINDER_CHANNEL))
    }

    @Test fun taskNotificationHasTitleActionsAndColor() {
        val now = System.currentTimeMillis()
        ReminderNotifications.showTask(app, TaskEntity(7, "Fizik ödevi", "3. bölüm", dueAt = now + 15 * 60000L, priority = 2), now)
        val n = notifications.allNotifications.single()
        assertEquals("🔥 Fizik ödevi", n.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(n.extras.getCharSequence(Notification.EXTRA_TEXT).toString().startsWith("15 dk sonra"))
        assertTrue(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("3. bölüm"))
        assertEquals(listOf("✓ Tamamla", "10 dk ertele"), n.actions.map { it.title.toString() })
        assertNotNull(n.getLargeIcon())
        assertEquals(0, n.flags and Notification.FLAG_INSISTENT)
    }

    @Test fun insistentModeSetsFlag() {
        AppPreferences.prefs(app).edit().putBoolean(AppPreferences.INSISTENT_REMINDERS, true).commit()
        ReminderNotifications.showCourse(app, CourseEntity(1, "Matematik", 1, 540, 600, "B12"), 10)
        val n = notifications.allNotifications.single()
        assertEquals("🎓 Matematik", n.extras.getString(Notification.EXTRA_TITLE))
        assertNotEquals(0, n.flags and Notification.FLAG_INSISTENT)
    }

    @Test fun schedulesOneAlarmPerFutureReminderAndCancelsAll() {
        val now = System.currentTimeMillis()
        val times = listOf(now - 1000, now + 60000, now + 120000, now + 180000)
        val task = TaskEntity(3, "Çoklu", dueAt = now + 180000, reminderAt = times[0], reminders = formatReminderTimes(times))
        AlarmScheduler.schedule(app, task)
        assertEquals(times.drop(1), alarms.scheduledAlarms.map { it.triggerAtMs }.sorted())
        AlarmScheduler.schedule(app, task.copy(reminders = formatReminderTimes(times.take(2))))
        assertEquals(listOf(times[1]), alarms.scheduledAlarms.map { it.triggerAtMs })
        AlarmScheduler.snooze(app, 3)
        assertEquals(2, alarms.scheduledAlarms.size)
        AlarmScheduler.cancel(app, 3)
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test fun courseGetsOneWeeklyAlarmPerReminder() {
        val course = CourseEntity(5, "Fizik", 3, 600, 660, reminderMinutes = 30, reminders = "30,10,0")
        CourseAlarmScheduler.schedule(app, course)
        assertEquals(3, alarms.scheduledAlarms.size)
        CourseAlarmScheduler.schedule(app, course.copy(reminderMinutes = 10, reminders = "10"))
        assertEquals(1, alarms.scheduledAlarms.size)
        CourseAlarmScheduler.cancel(app, 5)
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }
}
