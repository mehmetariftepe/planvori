package com.mehmetarif.sadegorev.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.mehmetarif.sadegorev.AppPreferences
import com.mehmetarif.sadegorev.MainActivity
import kotlinx.coroutines.launch

/** A permission-free selection sheet shown directly over the launcher. */
class WidgetFilterActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val schedule=intent.getBooleanExtra("schedule",false)
        val prefs=AppPreferences.prefs(this)
        val settings=prefs.widgetSettings()
        val style=widgetAppearance(settings.theme,settings.accent)
        val options=if(schedule)weekdayLabels.mapIndexed{i,label->i to label}
            else listOf(-1 to "Tüm öncelikler",2 to "Yüksek öncelik",1 to "Orta öncelik",0 to "Düşük öncelik")
        val selected=if(schedule)settings.courseDay else settings.priority
        setContent {
            MaterialTheme(colorScheme=darkColorScheme(surface=style.surface,onSurface=Color.White,primary=style.accent)) {
                var busy by remember{mutableStateOf(false)}
                BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.35f)).clickable(indication=null,interactionSource=remember{MutableInteractionSource()}){if(!busy)finish()},contentAlignment=Alignment.BottomCenter) {
                    Surface(Modifier.navigationBarsPadding().padding(16.dp).widthIn(max=440.dp).fillMaxWidth().heightIn(max=maxHeight*0.85f)
                        .clickable(indication=null,interactionSource=remember{MutableInteractionSource()}){},shape=RoundedCornerShape(28.dp),color=style.surface) {
                        Column(Modifier.padding(vertical=12.dp)) {
                            Text(if(schedule)"Ders Programı" else "Görevlerim",Modifier.padding(horizontal=24.dp,vertical=12.dp),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                            LazyColumn(Modifier.weight(1f,fill=false)) {
                                items(options,key={it.first}) { (value,label) ->
                                    Row(Modifier.fillMaxWidth().background(if(value==selected)style.accent.copy(alpha=0.10f) else Color.Transparent)
                                        .clickable(enabled=!busy) {
                                            busy=true
                                            lifecycleScope.launch {
                                                val edit=prefs.edit()
                                                if(schedule)edit.putInt(AppPreferences.SCHEDULE_WIDGET_DAY,value)
                                                else edit.putInt(AppPreferences.WIDGET_PRIORITY,value).putInt(AppPreferences.WIDGET_SORT,1).putInt(AppPreferences.WIDGET_FILTER,3)
                                                if(edit.commit()) {
                                                    try {if(schedule)refreshScheduleWidgets(this@WidgetFilterActivity) else refreshWidgets(this@WidgetFilterActivity)}
                                                    finally {finish()}
                                                } else busy=false
                                            }
                                        }.padding(horizontal=24.dp,vertical=15.dp),verticalAlignment=Alignment.CenterVertically) {
                                        RadioButton(selected=value==selected,onClick=null,colors=RadioButtonDefaults.colors(selectedColor=style.accent,unselectedColor=style.muted))
                                        Spacer(Modifier.width(18.dp))
                                        Text(label,style=MaterialTheme.typography.bodyLarge,color=if(value==selected)style.accent else Color.White)
                                    }
                                }
                            }
                            TextButton(enabled=!busy,onClick={
                                startActivity(Intent(this@WidgetFilterActivity,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).apply {
                                    if(schedule){putExtra("openSchedule",true);putExtra("courseDay",selected)} else putExtra("taskFilter",settings.filter)
                                });finish()
                            },modifier=Modifier.padding(horizontal=16.dp)) {Text(if(schedule)"Ders programını aç" else "Görevleri aç")}
                        }
                    }
                }
            }
        }
    }
}
