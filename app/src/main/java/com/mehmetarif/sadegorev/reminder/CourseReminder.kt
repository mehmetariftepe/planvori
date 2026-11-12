package com.mehmetarif.sadegorev.reminder

import android.app.*
import android.content.*
import android.net.Uri
import com.mehmetarif.sadegorev.data.CourseEntity
import com.mehmetarif.sadegorev.data.TaskDatabase
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId

object CourseAlarmScheduler {
    /** minutes = null: önceki sürümlerin tek alarmı (yalnızca iptal etmek için). */
    private fun pending(context: Context, id: Long, minutes: Int?) = PendingIntent.getBroadcast(
        context, id.hashCode(),
        Intent(context, CourseAlarmReceiver::class.java).putExtra("courseId", id).apply {
            if (minutes != null) { data = Uri.parse("planvori:course/$id/$minutes"); putExtra("minutes", minutes) }
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Dersin her hatırlatması için bir sonraki haftalık alarmı kurar; seçili olmayanları iptal eder. */
    fun schedule(context: Context, course: CourseEntity, now: Long = System.currentTimeMillis()) {
        cancel(context, course.id)
        val zoned = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
        course.reminderOffsets().take(MAX_REMINDERS).forEach { minutes ->
            val at = nextCourseReminder(course.weekday, course.startMinute, minutes, zoned)?.toInstant()?.toEpochMilli() ?: return@forEach
            setAlarm(context, at, pending(context, course.id, minutes))
        }
    }

    fun cancel(context: Context, id: Long) {
        val manager = context.getSystemService(AlarmManager::class.java)
        manager.cancel(pending(context, id, null))
        courseReminderOptions.forEach { (minutes, _) -> if (minutes >= 0) manager.cancel(pending(context, id, minutes)) }
    }

    suspend fun rescheduleAll(context: Context) =
        TaskDatabase.get(context).courseDao().withReminders().forEach { schedule(context, it) }
}

class CourseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val id = intent.getLongExtra("courseId", -1)
            try {
                val course = TaskDatabase.get(context).courseDao().get(id) ?: return@launch
                val offsets = course.reminderOffsets()
                val minutes = intent.getIntExtra("minutes", offsets.firstOrNull() ?: -1)
                if (minutes in offsets) ReminderNotifications.showCourse(context, course, minutes)
            } finally {
                // Haftalık tekrar: bir sonraki hafta için yeniden kur.
                try { TaskDatabase.get(context).courseDao().get(id)?.let { CourseAlarmScheduler.schedule(context, it, System.currentTimeMillis() + 1000) } }
                finally { pendingResult.finish() }
            }
        }
    }
}
