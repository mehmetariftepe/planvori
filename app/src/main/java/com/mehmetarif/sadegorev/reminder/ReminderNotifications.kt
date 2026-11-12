package com.mehmetarif.sadegorev.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mehmetarif.sadegorev.AppPreferences
import com.mehmetarif.sadegorev.CourseColors
import com.mehmetarif.sadegorev.MainActivity
import com.mehmetarif.sadegorev.R
import com.mehmetarif.sadegorev.data.CourseEntity
import com.mehmetarif.sadegorev.data.TaskDatabase
import com.mehmetarif.sadegorev.data.TaskEntity
import com.mehmetarif.sadegorev.priorityColor
import com.mehmetarif.sadegorev.widget.refreshWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Kanal ayarları oluşturulduktan sonra değiştirilemediği için yeni kimlikler kullanılır.
internal const val TASK_REMINDER_CHANNEL = "task_reminders_v2"
internal const val COURSE_REMINDER_CHANNEL = "course_reminders_v2"
private const val ACTION_COMPLETE = "com.mehmetarif.sadegorev.action.COMPLETE_TASK"
private const val ACTION_SNOOZE = "com.mehmetarif.sadegorev.action.SNOOZE_TASK"
private const val TEST_TASK_ID = -100L

object ReminderNotifications {
    private val vibration = longArrayOf(0, 350, 180, 350, 180, 600)

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        listOf("task_reminders", "course_reminders").forEach(manager::deleteNotificationChannel)
        fun channel(id: String, name: String, about: String) = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            description = about
            enableVibration(true); vibrationPattern = vibration
            enableLights(true); lightColor = 0xFF4F5BD5.toInt()
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel(TASK_REMINDER_CHANNEL, "Görev hatırlatmaları", "Zamanı gelen görevler için bildirimler"))
        manager.createNotificationChannel(channel(COURSE_REMINDER_CHANNEL, "Ders hatırlatmaları", "Başlamak üzere olan dersler için bildirimler"))
    }

    /** Renkli daire içinde baş harf: bildirimde büyük simge olarak görünür. */
    private fun avatar(text: String, color: Int): Bitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = 0xFFFFFFFF.toInt(); paint.textSize = size * 0.5f; paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val letter = text.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "!"
        canvas.drawText(letter, size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2, paint)
        return bitmap
    }

    private fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        val notification = builder
            .setSmallIcon(R.drawable.ic_brand_mark)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVibrate(vibration)
            .setAutoCancel(true)
            .build()
        // Israrlı mod: bildirim açılana kadar ses tekrarlanır.
        if (AppPreferences.prefs(context).getBoolean(AppPreferences.INSISTENT_REMINDERS, false)) notification.flags = notification.flags or Notification.FLAG_INSISTENT
        try { NotificationManagerCompat.from(context).notify(id, notification) } catch (_: SecurityException) { }
    }

    private fun action(context: Context, action: String, taskId: Long, notificationId: Int) = PendingIntent.getBroadcast(
        context, notificationId,
        Intent(context, ReminderActionReceiver::class.java).setAction(action).putExtra("taskId", taskId).putExtra("notificationId", notificationId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun showTask(context: Context, task: TaskEntity, now: Long = System.currentTimeMillis()) {
        val notificationId = task.id.hashCode()
        val color = priorityColor(task.priority).toArgb()
        val open = PendingIntent.getActivity(context, notificationId, Intent(context, MainActivity::class.java)
            .putExtra("taskId", task.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = taskNotificationText(task.dueAt, now)
        val overdue = task.dueAt != null && task.dueAt < now - 60000L
        val title = (when { overdue -> "⚠️ "; task.priority == 2 -> "🔥 "; else -> "⏰ " }) + task.title
        post(context, notificationId, NotificationCompat.Builder(context, TASK_REMINDER_CHANNEL)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(title)
                .bigText(listOf(text, task.description.trim()).filter { it.isNotEmpty() }.joinToString("\n")))
            .setSubText(if (task.priority == 2) "Yüksek öncelik" else "Görev hatırlatması")
            .setLargeIcon(avatar(task.title, color))
            .setColor(color)
            .apply { task.dueAt?.let { setWhen(it); setShowWhen(true) } }
            .setContentIntent(open)
            .addAction(0, "✓ Tamamla", action(context, ACTION_COMPLETE, task.id, notificationId))
            .addAction(0, "${AlarmScheduler.SNOOZE_MINUTES} dk ertele", action(context, ACTION_SNOOZE, task.id, notificationId)))
    }

    fun showCourse(context: Context, course: CourseEntity, minutes: Int) {
        val notificationId = ("course" + course.id).hashCode()
        val color = CourseColors[course.color.coerceIn(0, CourseColors.lastIndex)].toArgb()
        val open = PendingIntent.getActivity(context, notificationId, Intent(context, MainActivity::class.java)
            .putExtra("openSchedule", true).putExtra("courseDay", course.weekday)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = courseNotificationText(course, minutes)
        val title = "🎓 " + course.title
        post(context, notificationId, NotificationCompat.Builder(context, COURSE_REMINDER_CHANNEL)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(title)
                .bigText(listOf(text, course.instructor.trim()).filter { it.isNotEmpty() }.joinToString("\n")))
            .setSubText("Ders hatırlatması")
            .setLargeIcon(avatar(course.title, color))
            .setColor(color)
            .setContentIntent(open))
    }

    /** Ayarlar'daki "Deneme bildirimi": bildirimin nasıl görüneceğini gösterir. */
    fun showTest(context: Context) = showTask(context, TaskEntity(id = TEST_TASK_ID, title = "Deneme hatırlatması",
        description = "Hatırlatmaların böyle görünecek. Düğmelerle görevi tamamlayabilir veya erteleyebilirsin.",
        dueAt = System.currentTimeMillis() + 15 * 60000L, priority = 2))
}

/** Bildirimdeki "Tamamla" ve "Ertele" düğmeleri. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val id = intent.getLongExtra("taskId", -1)
                NotificationManagerCompat.from(context).cancel(intent.getIntExtra("notificationId", id.hashCode()))
                val dao = TaskDatabase.get(context).taskDao()
                val task = dao.get(id) ?: return@launch
                if (task.completed) return@launch
                when (intent.action) {
                    ACTION_COMPLETE -> {
                        dao.update(task.copy(completed = true, completedAt = System.currentTimeMillis()))
                        AlarmScheduler.cancel(context, id)
                        refreshWidgets(context)
                    }
                    ACTION_SNOOZE -> AlarmScheduler.snooze(context, id)
                }
            } finally { result.finish() }
        }
    }
}
