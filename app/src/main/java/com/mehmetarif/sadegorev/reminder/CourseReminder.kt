package com.mehmetarif.sadegorev.reminder

import android.app.*
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mehmetarif.sadegorev.MainActivity
import com.mehmetarif.sadegorev.R
import com.mehmetarif.sadegorev.data.CourseEntity
import com.mehmetarif.sadegorev.data.TaskDatabase
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId

object CourseAlarmScheduler {
    private fun pending(context: Context, id: Long) = PendingIntent.getBroadcast(
        context, id.hashCode(), Intent(context, CourseAlarmReceiver::class.java).putExtra("courseId", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Bir sonraki hatırlatmayı kurar; hatırlatma kapalıysa mevcut alarmı iptal eder. */
    fun schedule(context: Context, course: CourseEntity, now: Long = System.currentTimeMillis()) {
        val intent = pending(context, course.id)
        val manager = context.getSystemService(AlarmManager::class.java)
        val at = nextCourseReminder(course.weekday, course.startMinute, course.reminderMinutes,
            Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()))?.toInstant()?.toEpochMilli()
        if (at == null) { manager.cancel(intent); return }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms())
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
    }

    fun cancel(context: Context, id: Long) = context.getSystemService(AlarmManager::class.java).cancel(pending(context, id))

    suspend fun rescheduleAll(context: Context) =
        TaskDatabase.get(context).courseDao().withReminders().forEach { schedule(context, it) }
}

class CourseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val id = intent.getLongExtra("courseId", -1)
                val course = TaskDatabase.get(context).courseDao().get(id) ?: return@launch
                if (course.reminderMinutes < 0) return@launch
                val notifyId = ("course" + id).hashCode()
                val open = PendingIntent.getActivity(context, notifyId, Intent(context, MainActivity::class.java)
                    .putExtra("openSchedule", true).putExtra("courseDay", course.weekday)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val text = courseNotificationText(course)
                val notification = NotificationCompat.Builder(context, COURSE_REMINDER_CHANNEL)
                    .setSmallIcon(R.drawable.ic_brand_mark)
                    .setContentTitle(course.title)
                    .setContentText(text)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setContentIntent(open).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()
                try { NotificationManagerCompat.from(context).notify(notifyId, notification) } catch (_: SecurityException) { }
            } finally {
                // Haftalık tekrar: bir sonraki hafta için yeniden kur.
                try {
                    val id = intent.getLongExtra("courseId", -1)
                    TaskDatabase.get(context).courseDao().get(id)?.let { CourseAlarmScheduler.schedule(context, it, System.currentTimeMillis() + 1000) }
                } finally { pendingResult.finish() }
            }
        }
    }
}
