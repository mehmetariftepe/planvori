package com.mehmetarif.sadegorev.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import androidx.glance.*
import androidx.glance.action.*
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.mehmetarif.sadegorev.AppPreferences
import com.mehmetarif.sadegorev.MainActivity
import com.mehmetarif.sadegorev.data.TaskDatabase
import com.mehmetarif.sadegorev.reminder.AlarmScheduler
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TaskIdKey = ActionParameters.Key<Long>("taskId")
internal val priorityLabels=listOf("Düşük öncelik","Orta öncelik","Yüksek öncelik")

class TaskWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs=AppPreferences.prefs(context)
        val dao=TaskDatabase.get(context).taskDao()
        val initial=taskWidgetSnapshot(dao.getAll(),prefs.widgetSettings())
        provideContent {
            val snapshots=remember { taskWidgetSnapshots(dao.observeAll(),prefs.observeWidgetSettings()) }
            val snapshot by snapshots.collectAsState(initial)
            val style=widgetAppearance(snapshot.settings.theme,snapshot.settings.accent)
            val menu=Intent(context,WidgetFilterActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("schedule",false)
            val open=Intent(context,MainActivity::class.java).putExtra("taskFilter",snapshot.settings.filter)
            GlanceTheme {
                Column(GlanceModifier.fillMaxSize().then(style.panel()).padding(12.dp)) {
                    Row(GlanceModifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(menu))) {
                            Text("Görevlerim ▾",style=TextStyle(ColorProvider(Color.White),fontSize=17.sp,fontWeight=FontWeight.Bold),maxLines=1)
                            val priority=snapshot.settings.priority
                            val dateScope=if(snapshot.settings.filter==3)"" else listOf("Bugün","Yaklaşan","Geciken")[snapshot.settings.filter]+" · "
                            Text("$dateScope${if(priority<0)"Tüm öncelikler" else priorityLabels[priority]} · ${snapshot.pending} görev",style=TextStyle(ColorProvider(style.muted),fontSize=11.sp),maxLines=1)
                        }
                        Box(GlanceModifier.size(36.dp).then(style.chip(true)).clickable(actionStartActivity(Intent(context,MainActivity::class.java).putExtra("newTask",true))),contentAlignment=Alignment.Center) {
                            Text("＋",style=TextStyle(ColorProvider(style.accent),fontSize=24.sp))
                        }
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    if(snapshot.tasks.isEmpty()) Text("Bu seçimde bekleyen görev yok",GlanceModifier.defaultWeight().fillMaxWidth().clickable(actionStartActivity(open)).padding(top=8.dp),style=TextStyle(ColorProvider(style.muted),fontSize=12.sp))
                    else LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                        items(snapshot.tasks,itemId={it.id}) { task ->
                            Row(GlanceModifier.fillMaxWidth().padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
                                Box(GlanceModifier.size(34.dp).clickable(actionRunCallback<ToggleTaskAction>(actionParametersOf(TaskIdKey to task.id))),contentAlignment=Alignment.Center) {
                                    Text("○",style=TextStyle(ColorProvider(style.accent),fontSize=25.sp))
                                }
                                Spacer(GlanceModifier.width(7.dp))
                                Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(Intent(context,MainActivity::class.java).putExtra("taskId",task.id)))) {
                                    Text(task.title,style=TextStyle(ColorProvider(Color.White),fontSize=14.sp,fontWeight=FontWeight.Medium),maxLines=2)
                                    val due=widgetDueLabel(task.dueAt)
                                    Text(listOfNotNull(due,priorityLabels[task.priority]).joinToString(" · "),style=TextStyle(ColorProvider(style.muted),fontSize=11.sp),maxLines=1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
class TaskWidgetReceiver:GlanceAppWidgetReceiver(){override val glanceAppWidget:GlanceAppWidget=TaskWidget()}
class ToggleTaskAction:ActionCallback {
    override suspend fun onAction(context:Context,glanceId:GlanceId,parameters:ActionParameters) {
        val id=parameters[TaskIdKey]?:return
        val dao=TaskDatabase.get(context).taskDao()
        try {
            dao.get(id)?.takeIf{!it.completed}?.let {dao.update(it.copy(completed=true));AlarmScheduler.cancel(context,id)}
        } finally {refreshWidgets(context)}
    }
}
suspend fun refreshWidgets(context:Context)=TaskWidget().updateAll(context)
