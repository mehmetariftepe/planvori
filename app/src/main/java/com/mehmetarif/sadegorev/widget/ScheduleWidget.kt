package com.mehmetarif.sadegorev.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.mehmetarif.sadegorev.AppPreferences
import com.mehmetarif.sadegorev.MainActivity
import com.mehmetarif.sadegorev.data.TaskDatabase

internal val weekdayLabels=listOf("Tüm günler","Pazartesi","Salı","Çarşamba","Perşembe","Cuma","Cumartesi","Pazar")
private fun clock(minute:Int)="%02d:%02d".format(minute/60,minute%60)
class ScheduleWidget:GlanceAppWidget() {
    override val sizeMode=SizeMode.Exact
    override suspend fun provideGlance(context:Context,id:GlanceId) {
        val prefs=AppPreferences.prefs(context)
        val dao=TaskDatabase.get(context).courseDao()
        val initial=courseWidgetSnapshot(dao.getAll(),prefs.widgetSettings())
        provideContent {
            val snapshots=remember {courseWidgetSnapshots(dao.observeAll(),prefs.observeWidgetSettings())}
            val snapshot by snapshots.collectAsState(initial)
            val style=widgetAppearance(snapshot.settings.theme,snapshot.settings.accent)
            val day=snapshot.settings.courseDay.coerceIn(0,7)
            val open=Intent(context,MainActivity::class.java).putExtra("openSchedule",true)
            val menu=Intent(context,WidgetFilterActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("schedule",true)
            GlanceTheme {
                Column(GlanceModifier.fillMaxSize().then(style.panel()).padding(12.dp)) {
                    Row(GlanceModifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(menu))) {
                            Text("Ders Programı ▾",style=TextStyle(ColorProvider(Color.White),fontSize=17.sp,fontWeight=FontWeight.Bold),maxLines=1)
                            Text("${weekdayLabels[day]} · ${snapshot.total} ders",style=TextStyle(ColorProvider(style.muted),fontSize=11.sp),maxLines=1)
                        }
                        Box(GlanceModifier.size(36.dp).then(style.chip(true)).clickable(actionStartActivity(Intent(context,MainActivity::class.java).putExtra("openSchedule",true).putExtra("newCourse",true).putExtra("courseDay",day))),contentAlignment=Alignment.Center) {
                            Text("＋",style=TextStyle(ColorProvider(style.accent),fontSize=24.sp))
                        }
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    if(snapshot.courses.isEmpty()) Text("Bu seçimde ders yok",GlanceModifier.defaultWeight().fillMaxWidth().clickable(actionStartActivity(open)).padding(top=8.dp),style=TextStyle(ColorProvider(style.muted),fontSize=12.sp))
                    else LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                        items(snapshot.courses,itemId={it.id}) {lesson ->
                            Column(GlanceModifier.fillMaxWidth().clickable(actionStartActivity(Intent(context,MainActivity::class.java).putExtra("openSchedule",true).putExtra("courseDay",lesson.weekday))).padding(vertical=7.dp)) {
                                Text(lesson.title,style=TextStyle(ColorProvider(Color.White),fontSize=14.sp,fontWeight=FontWeight.Medium),maxLines=2)
                                Text("${weekdayLabels[lesson.weekday]} · ${clock(lesson.startMinute)}–${clock(lesson.endMinute)}${if(lesson.location.isBlank())"" else " · ${lesson.location}"}",style=TextStyle(ColorProvider(style.muted),fontSize=11.sp),maxLines=1)
                            }
                        }
                    }
                }
            }
        }
    }
}
class ScheduleWidgetReceiver:GlanceAppWidgetReceiver(){override val glanceAppWidget:GlanceAppWidget=ScheduleWidget()}
suspend fun refreshScheduleWidgets(context:Context)=ScheduleWidget().updateAll(context)
