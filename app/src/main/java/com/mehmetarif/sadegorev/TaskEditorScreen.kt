package com.mehmetarif.sadegorev

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import com.mehmetarif.sadegorev.reminder.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mehmetarif.sadegorev.data.TaskEntity
import java.time.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TaskEditorScreen(existing:TaskEntity?,onBack:()->Unit,onSave:(TaskEntity)->Unit){
    var title by rememberSaveable(existing?.id){mutableStateOf(existing?.title.orEmpty())}
    var desc by rememberSaveable(existing?.id){mutableStateOf(existing?.description.orEmpty())}
    var due by rememberSaveable(existing?.id){mutableStateOf(existing?.dueAt)}
    var reminder by rememberSaveable(existing?.id){mutableStateOf(existing?.reminderAt)}
    var reminderChoice by rememberSaveable(existing?.id){mutableIntStateOf(initialReminderChoice(existing!=null,existing?.dueAt,existing?.reminderAt))}
    var priority by rememberSaveable(existing?.id){mutableIntStateOf(existing?.priority?:1)}
    var page by rememberSaveable{mutableIntStateOf(0)}
    var custom by rememberSaveable{mutableStateOf(false)}
    var discard by remember { mutableStateOf(false) }
    fun leave() { if(page!=0)page=0 else if(title!=existing?.title.orEmpty() || desc!=existing?.description.orEmpty() || due!=existing?.dueAt || reminder!=existing?.reminderAt || priority!=(existing?.priority?:1))discard=true else onBack() }
    BackHandler { leave() }
    val zone=ZoneId.systemDefault()
    val source=if(custom)reminder?:due else due
    val date=key(custom,page){rememberDatePickerState(initialSelectedDateMillis=source?.let{Instant.ofEpochMilli(it).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()})}
    val local=source?.let{Instant.ofEpochMilli(it).atZone(zone).toLocalTime()}?:LocalTime.of(18,0)
    val time=key(custom,page){rememberTimePickerState(local.hour,local.minute,true)}
    Scaffold(topBar={TopAppBar(title={Text(when(page){1->"Tarih ve saat";2->"Hatırlatma";else->if(existing==null)"Yeni görev" else "Görevi düzenle"})},navigationIcon={IconButton({leave()}){Icon(Icons.Default.ArrowBack,"Geri")}})},bottomBar={
        if(page==0)Button(enabled=title.isNotBlank(),onClick={onSave((existing?:TaskEntity(title=title)).copy(title=title.trim(),description=desc.trim(),dueAt=due,reminderAt=reminderTime(reminderChoice,due,reminder),priority=priority))},modifier=Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(20.dp).height(56.dp),shape=RoundedCornerShape(18.dp)){Text("Görevi kaydet")}
    }){padding->Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)){
        AnimatedContent(page,transitionSpec={(fadeIn(tween(220))+slideInHorizontally(tween(260)){if(targetState>initialState)it/7 else -it/7}) togetherWith (fadeOut(tween(130))+slideOutHorizontally(tween(200)){if(targetState>initialState)-it/7 else it/7})},label="editor-page") { activePage ->
            Column(verticalArrangement=Arrangement.spacedBy(18.dp)) { when(activePage){
                0->{
                    SectionLabel("Görev bilgileri")
                    TextField(title,{title=it},label={Text("Görev başlığı")},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),colors=TextFieldDefaults.colors(focusedIndicatorColor=androidx.compose.ui.graphics.Color.Transparent,unfocusedIndicatorColor=androidx.compose.ui.graphics.Color.Transparent))
                    TextField(desc,{desc=it},label={Text("Not ekle · isteğe bağlı")},minLines=3,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),colors=TextFieldDefaults.colors(focusedIndicatorColor=androidx.compose.ui.graphics.Color.Transparent,unfocusedIndicatorColor=androidx.compose.ui.graphics.Color.Transparent))
                    SectionLabel("Planlama")
                    FilledTonalButton({custom=false;page=1},Modifier.fillMaxWidth().height(56.dp)){Text(due?.let{Instant.ofEpochMilli(it).atZone(zone).format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm",java.util.Locale.forLanguageTag("tr")))}?:"Tarih ve saat ekle")}
                    if(due!=null)TextButton({due=null;reminder=null}){Text("Tarihi kaldır")}
                    FilledTonalButton(enabled=due!=null,onClick={page=2},modifier=Modifier.fillMaxWidth().height(56.dp)){Text("Hatırlatma · "+reminderLabel(reminderChoice,reminder))}
                    if(due==null)Text("Tarih ve saat eklediğinde görev saatinde otomatik hatırlatılır.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(due!=null&&reminderChoice!=NO_REMINDER)ReminderPermissionHint()
                    reminderTime(reminderChoice,due,reminder)?.let{if(it<=System.currentTimeMillis())Text("Seçilen hatırlatma zamanı geçmişte. Geçmiş tarihler için bildirim gönderilmez.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)}
                    SectionLabel("Öncelik")
                    SegmentedOptions(listOf("Düşük","Orta","Yüksek"),priority,{priority=it})
                }
                1->{DatePicker(date);TimeInput(time);Button(enabled=date.selectedDateMillis!=null,onClick={val d=Instant.ofEpochMilli(date.selectedDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate();val picked=d.atTime(time.hour,time.minute).atZone(zone).toInstant().toEpochMilli();if(custom){reminderChoice=CUSTOM_REMINDER;reminder=picked}else{if(reminderChoice==CUSTOM_REMINDER&&reminder==null)reminderChoice=0;due=picked;reminder=reminderTime(reminderChoice,picked,reminder)};page=0},modifier=Modifier.fillMaxWidth()){Text("Uygula")}}
                2->{
                    Text("Ne zaman hatırlatalım?",style=MaterialTheme.typography.headlineSmall)
                    Text("Seçili: "+reminderLabel(reminderChoice,reminder),color=MaterialTheme.colorScheme.primary)
                    reminderOptions.forEach{(choice,label)->ReminderOption(label,reminderChoice==choice){reminderChoice=choice;reminder=reminderTime(choice,due,reminder)}}
                    ReminderOption(reminderLabel(CUSTOM_REMINDER,if(reminderChoice==CUSTOM_REMINDER)reminder else null),reminderChoice==CUSTOM_REMINDER){custom=true;page=1}
                    reminderTime(reminderChoice,due,reminder)?.let{if(it<=System.currentTimeMillis())Text("Seçilen hatırlatma zamanı geçmişte. Geçmiş tarihler için bildirim gönderilmez.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)}
                    Button({page=0},Modifier.fillMaxWidth().height(52.dp)){Text("Tamam")}
                }
            } }
        }
    }}
    if(discard)AlertDialog(onDismissRequest={discard=false},title={Text("Değişiklikler kaydedilmedi")},text={Text("Bu ekrandan çıkarsan yaptığın değişiklikler kaybolacak.")},confirmButton={TextButton(onBack){Text("Kaydetmeden çık")}},dismissButton={TextButton({discard=false}){Text("Devam et")}})
}

@Composable private fun ReminderOption(label:String,selected:Boolean,onSelect:()->Unit){
    Card(modifier=Modifier.fillMaxWidth().selectable(selected=selected,role=Role.RadioButton,onClick=onSelect),
        shape=RoundedCornerShape(20.dp),
        colors=CardDefaults.cardColors(containerColor=if(selected)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer),
        border=BorderStroke(if(selected)2.dp else 1.dp,if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)){
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).padding(horizontal=12.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
            RadioButton(selected=selected,onClick=null)
            Spacer(Modifier.width(10.dp))
            Text(label,fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal,modifier=Modifier.weight(1f),color=if(selected)MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
        }
    }
}
