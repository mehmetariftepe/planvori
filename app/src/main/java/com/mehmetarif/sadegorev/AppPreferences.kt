package com.mehmetarif.sadegorev

import android.content.Context

object AppPreferences {
    const val APP_THEME = "app_theme"
    const val APP_ACCENT = "app_accent"
    const val FILE = "tasklist_preferences"
    const val HOME_FILTER = "home_filter"
    const val HOME_SORT = "home_sort"
    const val HOME_FILTER_ORDER = "home_filter_order"
    const val WIDGET_FILTER = "widget_filter"
    const val WIDGET_SORT = "widget_sort"
    const val WIDGET_THEME = "widget_theme"
    const val WIDGET_COUNT = "widget_count"
    const val WIDGET_FILTER_ORDER = "widget_filter_order"
    const val SCHEDULE_WIDGET_FILTER = "schedule_widget_filter"
    const val SCHEDULE_WIDGET_COUNT = "schedule_widget_count"

    const val WIDGET_PRIORITY = "widget_priority"
    const val SCHEDULE_WIDGET_DAY = "schedule_widget_day"
    /** Bildirim açılana kadar sesi tekrarla (Boolean). */
    const val INSISTENT_REMINDERS = "insistent_reminders"

    fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
