package com.mehmetarif.sadegorev.reminder

import android.app.*
import android.content.*
import android.net.Uri
import android.os.Build
import com.mehmetarif.sadegorev.data.TaskDatabase
import com.mehmetarif.sadegorev.data.TaskEntity
import com.mehmetarif.sadegorev.widget.refreshScheduleWidgets
import com.mehmetarif.sadegorev.widget.refreshWidgets
import kotlinx.coroutines.*

internal fun setAlarm(context: Context, at: Long, intent: PendingIntent): Boolean {
    val manager = context.getSystemService(AlarmManager::class.java)
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent); true
    } else {
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent); false
    }
}

object AlarmScheduler {
    private const val SNOOZE = "snooze"
    const val SNOOZE_MINUTES = 10

    /**
     * Her hatırlatma ayrı bir alarm yuvası kullanır. İlk yuva (slot = null) önceki sürümlerin
     * tek alarmıyla aynı kimliğe sahiptir; böylece güncelleme sonrası eski alarm çift çalmaz.
     */
    private fun pending(context: Context, id: Long, slot: String?) = PendingIntent.getBroadcast(
        context, id.hashCode(),
        Intent(context, AlarmReceiver::class.java).putExtra("taskId", id).apply { if (slot != null) data = Uri.parse("planvori:task/$id/$slot") },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    private fun slot(index: Int) = if (index == 0) null else index.toString()

    /** Görevin gelecekteki tüm hatırlatmalarını kurar. Hepsi tam saatinde kurulabildiyse true döner. */
    fun schedule(context: Context, task: TaskEntity): Boolean {
        val manager = context.getSystemService(AlarmManager::class.java)
        repeat(MAX_REMINDERS) { manager.cancel(pending(context, task.id, slot(it))) }
        if (task.completed) return false
        val now = System.currentTimeMillis()
        val times = task.reminderTimes().filter { it > now }.take(MAX_REMINDERS)
        var exact = times.isNotEmpty()
        times.forEachIndexed { i, at -> if (!setAlarm(context, at, pending(context, task.id, slot(i)))) exact = false }
        return exact
    }

    /** Bildirimdeki "ertele" düğmesi: [SNOOZE_MINUTES] dakika sonra yeniden hatırlatır. */
    fun snooze(context: Context, id: Long) {
        setAlarm(context, System.currentTimeMillis() + SNOOZE_MINUTES * 60000L, pending(context, id, SNOOZE))
    }

    fun cancel(context: Context, id: Long) {
        val manager = context.getSystemService(AlarmManager::class.java)
        repeat(MAX_REMINDERS) { manager.cancel(pending(context, id, slot(it))) }
        manager.cancel(pending(context, id, SNOOZE))
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val id = intent.getLongExtra("taskId", -1)
                val task = TaskDatabase.get(context).taskDao().get(id) ?: return@launch
                if (!task.completed) ReminderNotifications.showTask(context, task)
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
                    try { TaskDatabase.get(context).taskDao().pendingReminders().forEach { AlarmScheduler.schedule(context, it) } }
                    finally { CourseAlarmScheduler.rescheduleAll(context) }
                }
                finally {
                    try { refreshWidgets(context) } finally { refreshScheduleWidgets(context) }
                }
            } finally { result.finish() }
        }
    }
}
