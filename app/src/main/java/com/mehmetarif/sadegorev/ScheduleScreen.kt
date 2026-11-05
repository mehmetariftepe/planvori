package com.mehmetarif.sadegorev

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mehmetarif.sadegorev.data.CourseEntity
import java.time.LocalDate

private val days = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
private val colors = listOf(Color(0xFF8FB8F8), Color(0xFF9CDCC4), Color(0xFFE6B6A6), Color(0xFFC2B7EE), Color(0xFFE7D08B))
private fun clock(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

@Composable fun ScheduleScreen(courses: List<CourseEntity>, onAdd:(Int)->Unit, onEdit:(CourseEntity)->Unit, onDelete:(CourseEntity)->Unit,initialDay:Int=LocalDate.now().dayOfWeek.value) {
    var selected by rememberSaveable { mutableIntStateOf(initialDay.coerceIn(1,7)) }
    val today = courses.filter { it.weekday == selected }.sortedBy { it.startMinute }
    Column(Modifier.fillMaxSize().padding(horizontal=20.dp)) {
        Spacer(Modifier.height(22.dp))
        Text("Ders programı",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        Text("Haftan, tek bakışta.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            days.forEachIndexed { index, label ->
                val day = index+1
                val active=selected==day
                val chipColor by animateColorAsState(if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,tween(220),label="weekday-color")
                Surface(Modifier.weight(1f).clickable { selected=day },shape=RoundedCornerShape(16.dp),color=chipColor) {
                    Column(Modifier.padding(vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(label,style=MaterialTheme.typography.labelMedium,color=if(active)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(5.dp))
                        Box(Modifier.size(5.dp).background(if(courses.any { it.weekday==day })if(active)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary else Color.Transparent,RoundedCornerShape(50)))
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("${days[selected-1]} · ${today.size} ders",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            FilledTonalButton(onClick={onAdd(selected)}) { Icon(Icons.Default.Add,"Ders ekle",Modifier.size(18.dp));Spacer(Modifier.width(4.dp));Text("Ders ekle") }
        }
        Spacer(Modifier.height(12.dp))
        Crossfade(targetState=today,animationSpec=tween(220),label="schedule-day") { classes ->
            if(classes.isEmpty()) {
                Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        Text("Bu güne ders eklenmemiş",style=MaterialTheme.typography.titleMedium)
                        Text("Ders ekle ile programını oluşturabilirsin.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=100.dp)) {
                items(classes,key={it.id}) { course ->
                    val color=colors[course.color.coerceIn(0,4)]
                    Surface(Modifier.fillMaxWidth().animateItem().clickable { onEdit(course) },shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface) {
                        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                            Box(Modifier.width(4.dp).height(58.dp).background(color,RoundedCornerShape(4.dp)))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f).animateContentSize()) {
                                Text(course.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                                if(course.location.isNotBlank())Text(course.location,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                if(course.instructor.isNotBlank())Text(course.instructor,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("${clock(course.startMinute)}\n${clock(course.endMinute)}",style=MaterialTheme.typography.labelMedium,color=color)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CourseEditorScreen(existing: CourseEntity?,initialWeekday:Int,onBack:()->Unit,onSave:(CourseEntity)->Unit,onDelete:(CourseEntity)->Unit,initialDay:Int=LocalDate.now().dayOfWeek.value) {
    var title by rememberSaveable(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var location by rememberSaveable(existing?.id) { mutableStateOf(existing?.location.orEmpty()) }
    var instructor by rememberSaveable(existing?.id) { mutableStateOf(existing?.instructor.orEmpty()) }
    var weekday by rememberSaveable(existing?.id,initialWeekday) { mutableIntStateOf(existing?.weekday ?: initialWeekday) }
    var start by rememberSaveable(existing?.id) { mutableIntStateOf(existing?.startMinute ?: 9*60) }
    var end by rememberSaveable(existing?.id) { mutableIntStateOf(existing?.endMinute ?: 10*60) }
    var color by rememberSaveable(existing?.id) { mutableIntStateOf(existing?.color ?: 0) }
    var settingTime by remember { mutableIntStateOf(-1) }
    var confirmDelete by remember { mutableStateOf(false) }
    val time=key(settingTime) { rememberTimePickerState(initialHour=(if(settingTime==0)start else end)/60, initialMinute=(if(settingTime==0)start else end)%60,is24Hour=true) }
    BackHandler { if(settingTime!=-1)settingTime=-1 else onBack() }
    Scaffold(topBar={TopAppBar(title={Text(if(settingTime!=-1)"Ders saati" else if(existing==null)"Yeni ders" else "Dersi düzenle")},navigationIcon={IconButton({if(settingTime!=-1)settingTime=-1 else onBack()}){Icon(Icons.Default.ArrowBack,"Geri")}})},bottomBar={
        Button(enabled=settingTime!=-1||(title.isNotBlank()&&end>start),onClick={if(settingTime!=-1){val value=time.hour*60+time.minute;if(settingTime==0)start=value else end=value;settingTime=-1}else onSave((existing?:CourseEntity(title=title,weekday=weekday,startMinute=start,endMinute=end)).copy(title=title.trim(),weekday=weekday,startMinute=start,endMinute=end,location=location.trim(),instructor=instructor.trim(),color=color))},modifier=Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp).height(54.dp),shape=RoundedCornerShape(50)){Text(if(settingTime!=-1)"Saati uygula" else "Dersi kaydet")}
    }) { padding -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(settingTime!=-1){SectionLabel(if(settingTime==0)"Başlangıç saati" else "Bitiş saati");TimeInput(time)}else{
        SectionLabel("Ders bilgileri")
        TextField(title,{title=it},modifier=Modifier.fillMaxWidth(),label={Text("Ders adı *")},singleLine=true,shape=RoundedCornerShape(22.dp),colors=TextFieldDefaults.colors(focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent))
        Text("Gün",style=MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) { days.forEachIndexed{i,label -> FilterChip(shape=RoundedCornerShape(50),border=null,selected=weekday==i+1,onClick={weekday=i+1},label={Text(label,style=MaterialTheme.typography.labelSmall)})} }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onClick={settingTime=0},modifier=Modifier.weight(1f)){Text("Başlangıç ${clock(start)}")}
            FilledTonalButton(onClick={settingTime=1},modifier=Modifier.weight(1f)){Text("Bitiş ${clock(end)}")}
        }
        if(end<=start)Text("Bitiş saati başlangıçtan sonra olmalı.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        TextField(location,{location=it},modifier=Modifier.fillMaxWidth(),label={Text("Derslik / yer (isteğe bağlı)")},singleLine=true,shape=RoundedCornerShape(22.dp),colors=TextFieldDefaults.colors(focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent))
        TextField(instructor,{instructor=it},modifier=Modifier.fillMaxWidth(),label={Text("Öğretim görevlisi (isteğe bağlı)")},singleLine=true,shape=RoundedCornerShape(22.dp),colors=TextFieldDefaults.colors(focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent))
        Text("Renk işareti",style=MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { colors.forEachIndexed{i,c -> Surface(Modifier.size(40.dp).clickable{color=i},shape=RoundedCornerShape(14.dp),color=c,border=if(color==i)BorderStroke(3.dp,MaterialTheme.colorScheme.onSurface) else null){} } }
        if(existing!=null)TextButton(onClick={confirmDelete=true}){Icon(Icons.Default.DeleteOutline,null);Spacer(Modifier.width(5.dp));Text("Dersi sil")}
    } } }
    if(confirmDelete) AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Ders silinsin mi?")},text={Text("${existing?.title} programdan kaldırılacak.")},confirmButton={TextButton({existing?.let(onDelete);confirmDelete=false}){Text("Sil")}},dismissButton={TextButton({confirmDelete=false}){Text("Vazgeç")}})
}
