package com.mehmetarif.sadegorev.widget

import android.content.SharedPreferences
import com.mehmetarif.sadegorev.AppPreferences
import com.mehmetarif.sadegorev.data.CourseEntity
import com.mehmetarif.sadegorev.data.TaskEntity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.time.*
import java.util.Locale

data class WidgetSettings(val filter:Int=3,val sort:Int=0,val theme:Int=0,val limit:Int=5,
    val order:List<Int> = listOf(0,1,2,3),val courseScope:Int=0,val courseLimit:Int=0,val accent:Int=0,val priority:Int=-1,val courseDay:Int=-1)

fun SharedPreferences.widgetSettings()=WidgetSettings(
    getInt(AppPreferences.WIDGET_FILTER,3).coerceIn(0,3),
    getInt(AppPreferences.WIDGET_SORT,0).coerceIn(0,2),
    getInt(AppPreferences.WIDGET_THEME,0).coerceIn(0,2),
    getInt(AppPreferences.WIDGET_COUNT,5).let{if(it in listOf(3,5,8))it else 5},
    getString(AppPreferences.WIDGET_FILTER_ORDER,"0,1,2,3")!!.split(",").mapNotNull{it.toIntOrNull()?.takeIf{n->n in 0..3}}.distinct().let{if(it.size==4)it else listOf(0,1,2,3)},
    getInt(AppPreferences.SCHEDULE_WIDGET_FILTER,0).coerceIn(0,1),
    getInt(AppPreferences.SCHEDULE_WIDGET_COUNT,0).let{if(it in listOf(0,3,5))it else 0},
    getInt(AppPreferences.APP_ACCENT,0).coerceIn(0,4),
    getInt(AppPreferences.WIDGET_PRIORITY,-1).coerceIn(-1,2),
    getInt(AppPreferences.SCHEDULE_WIDGET_DAY,0).coerceIn(0,7))

fun SharedPreferences.observeWidgetSettings():Flow<WidgetSettings> = callbackFlow {
    val listener=SharedPreferences.OnSharedPreferenceChangeListener { _,_->trySend(widgetSettings()) }
    registerOnSharedPreferenceChangeListener(listener)
    trySend(widgetSettings())
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}.distinctUntilChanged().conflate()

data class TaskWidgetSnapshot(val settings:WidgetSettings,val tasks:List<TaskEntity>,val pending:Int,val completed:Int)
fun taskWidgetSnapshot(all:List<TaskEntity>,settings:WidgetSettings,now:Long=System.currentTimeMillis(),zone:ZoneId=ZoneId.systemDefault()):TaskWidgetSnapshot {
    val today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val filtered=all.filter { t->when(settings.filter){
        0->t.dueAt==null || t.dueAt?.let{Instant.ofEpochMilli(it).atZone(zone).toLocalDate()==today}==true
        1->t.dueAt?.let{Instant.ofEpochMilli(it).atZone(zone).toLocalDate().isAfter(today)}==true
        2->t.dueAt?.let{it<now}==true
        else->true
    } }
    val scoped=filtered.filter{settings.priority<0 || it.priority==settings.priority}
    val pending=scoped.filter{!it.completed}
    val sorted=when(settings.sort){1->pending.sortedByDescending{it.priority};2->pending.sortedBy{it.title.lowercase(Locale.forLanguageTag("tr"))};else->pending.sortedWith(compareBy<TaskEntity>{it.dueAt==null}.thenBy{it.dueAt})}
    return TaskWidgetSnapshot(settings,sorted,pending.size,scoped.count{it.completed})
}
fun taskWidgetSnapshots(tasks:Flow<List<TaskEntity>>,settings:Flow<WidgetSettings>)=combine(tasks,settings){all,prefs->taskWidgetSnapshot(all,prefs)}

data class CourseWidgetSnapshot(val settings:WidgetSettings,val courses:List<CourseEntity>,val total:Int)
fun courseWidgetSnapshot(all:List<CourseEntity>,settings:WidgetSettings,day:Int=LocalDate.now().dayOfWeek.value):CourseWidgetSnapshot {
    val filtered=all.filter{when(settings.courseDay){0->true;in 1..7->it.weekday==settings.courseDay;else->settings.courseScope==1||it.weekday==day}}.sortedWith(compareBy<CourseEntity>{it.weekday}.thenBy{it.startMinute})
    return CourseWidgetSnapshot(settings,filtered,filtered.size)
}
fun courseWidgetSnapshots(courses:Flow<List<CourseEntity>>,settings:Flow<WidgetSettings>)=combine(courses,settings){all,prefs->courseWidgetSnapshot(all,prefs)}

internal fun widgetDueLabel(dueAt:Long?,zone:ZoneId=ZoneId.systemDefault()):String? = dueAt?.let {
    Instant.ofEpochMilli(it).atZone(zone).format(java.time.format.DateTimeFormatter.ofPattern("d MMM EEE · HH:mm",Locale.forLanguageTag("tr")))
}
