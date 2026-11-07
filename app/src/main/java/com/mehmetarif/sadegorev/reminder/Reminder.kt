package com.mehmetarif.sadegorev.reminder

import android.app.*
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mehmetarif.sadegorev.MainActivity
import com.mehmetarif.sadegorev.R
import com.mehmetarif.sadegorev.data.TaskDatabase
import com.mehmetarif.sadegorev.data.TaskEntity
import kotlinx.coroutines.*
import com.mehmetarif.sadegorev.widget.refreshWidgets
import com.mehmetarif.sadegorev.widget.refreshScheduleWidgets

object AlarmScheduler {
    private fun pending(context: Context, id: Long) = PendingIntent.getBroadcast(
        context, id.hashCode(), Intent(context, AlarmReceiver::class.java).putExtra("taskId", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    fun schedule(context: Context, task: TaskEntity): Boolean {
        val at = task.reminderAt ?: return false
        if (task.completed || at <= System.currentTimeMillis()) return false
        val manager = context.getSystemService(AlarmManager::class.java)
        val intent = pending(context, task.id)
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent); true
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent); false
        }
    }
    fun cancel(context: Context, id: Long) = context.getSystemService(AlarmManager::class.java).cancel(pending(context, id))
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val id = intent.getLongExtra("taskId", -1)
                val task = TaskDatabase.get(context).taskDao().get(id) ?: return@launch
                if (task.completed) return@launch
                val open = PendingIntent.getActivity(context, id.hashCode(), Intent(context, MainActivity::class.java)
                    .putExtra("taskId", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val notification = NotificationCompat.Builder(context, "task_reminders")
                    .setSmallIcon(R.drawable.ic_brand_mark)
                    .setContentTitle(task.title)
                    .setContentText(task.dueAt?.let { java.text.SimpleDateFormat("HH:mm", java.util.Locale("tr")).format(java.util.Date(it)) } ?: "Görev zamanı")
                    .setContentIntent(open).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()
                try { NotificationManagerCompat.from(context).notify(id.hashCode(), notification) } catch (_: SecurityException) { }
            } finally { pendingResult.finish() }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                try {
                    try { TaskDatabase.get(context).taskDao().pendingReminders(System.currentTimeMillis()).forEach { AlarmScheduler.schedule(context, it) } }
                    finally { CourseAlarmScheduler.rescheduleAll(context) }
                }
                finally {
                    try { refreshWidgets(context) } finally { refreshScheduleWidgets(context) }
                }
            } finally { result.finish() }
        }
    }
}
