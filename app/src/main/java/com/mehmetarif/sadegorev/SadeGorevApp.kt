package com.mehmetarif.sadegorev

import android.app.*
import android.os.Build

class SadeGorevApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel("course_reminders", "Ders hatırlatmaları", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Başlamak üzere olan dersler için bildirimler"
                }
            )
            manager.createNotificationChannel(
                NotificationChannel("task_reminders", "Görev hatırlatmaları", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Zamanı gelen görevler için bildirimler"
                }
            )
        }
    }
}
