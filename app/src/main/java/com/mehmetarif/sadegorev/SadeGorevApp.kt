package com.mehmetarif.sadegorev

import android.app.Application
import com.mehmetarif.sadegorev.reminder.ReminderNotifications

class SadeGorevApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderNotifications.createChannels(this)
    }
}
