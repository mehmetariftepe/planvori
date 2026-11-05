@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mehmetarif.sadegorev

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.clip
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mehmetarif.sadegorev.data.*
import com.mehmetarif.sadegorev.reminder.AlarmScheduler
import com.mehmetarif.sadegorev.widget.refreshWidgets
import com.mehmetarif.sadegorev.widget.refreshScheduleWidgets
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*

private val Bg: Color @Composable get() = MaterialTheme.colorScheme.background
private val CardColor: Color @Composable get() = MaterialTheme.colorScheme.surface
private val Accent: Color @Composable get() = MaterialTheme.colorScheme.primary
private enum class Screen { HOME, CALENDAR, SCHEDULE, DONE, SETTINGS }
private enum class Filter { TODAY, UPCOMING, OVERDUE, ALL }

class TaskViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = TaskDatabase.get(app).taskDao()
    private val courseDao = TaskDatabase.get(app).courseDao()
    val tasks = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val courses = courseDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun saveCourse(course:CourseEntity)=viewModelScope.launch {
        if(course.id==0L) courseDao.insert(course) else courseDao.update(course)
        refreshScheduleWidgets(getApplication())
    }
    fun deleteCourse(course:CourseEntity)=viewModelScope.launch {
        courseDao.delete(course);refreshScheduleWidgets(getApplication())
    }
    fun save(task: TaskEntity) = viewModelScope.launch {
        val saved = if (task.id == 0L) task.copy(id = dao.insert(task)) else task.also { dao.update(it) }
        try {
            AlarmScheduler.cancel(getApplication(), saved.id)
            AlarmScheduler.schedule(getApplication(), saved)
        } finally { refreshWidgets(getApplication()) }
    }
    fun toggle(task: TaskEntity) = viewModelScope.launch {
        val changed = task.copy(completed = !task.completed); dao.update(changed)
        try {
            if (changed.completed) AlarmScheduler.cancel(getApplication(), changed.id) else AlarmScheduler.schedule(getApplication(), changed)
        } finally { refreshWidgets(getApplication()) }
    }
    fun delete(task: TaskEntity) = viewModelScope.launch {
        AlarmScheduler.cancel(getApplication(), task.id); dao.delete(task); refreshWidgets(getApplication())
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent {
            SadeTheme {
                val vm: TaskViewModel = viewModel()
                TaskApp(vm, intent.getLongExtra("taskId", -1).takeIf { it > 0 }, intent.getBooleanExtra("newTask", false),intent.getBooleanExtra("openSchedule",false),intent.getBooleanExtra("newCourse",false), intent.getIntExtra("taskFilter",-1).takeIf { it in 0..3 },intent.getIntExtra("courseDay",0).takeIf{it in 1..7})
            }
        }
    }
}

@Composable internal fun SadeTheme(content: @Composable () -> Unit) {
    val context=LocalContext.current
    val prefs=remember{AppPreferences.prefs(context)}
    var mode by remember{mutableIntStateOf(prefs.getInt(AppPreferences.APP_THEME,0))}
    var accentIndex by remember{mutableIntStateOf(prefs.getInt(AppPreferences.APP_ACCENT,0))}
    DisposableEffect(prefs){
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener { p,key->
            if(key==AppPreferences.APP_THEME)mode=p.getInt(key,0)
            if(key==AppPreferences.APP_ACCENT)accentIndex=p.getInt(key,0)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose{prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    val dark=if(mode==3)isSystemInDarkTheme() else mode!=1
    val accent=if(dark)AccentColors[accentIndex.coerceIn(0,4)] else LightAccentColors[accentIndex.coerceIn(0,4)]
    val palette=if(!dark)lightColorScheme(background=Color(0xFFF7F5F1),surface=Color(0xFFEFEBE5),surfaceVariant=Color(0xFFE1DCD3),primary=accent,onPrimary=Color.White,primaryContainer=accent.copy(alpha=0.12f),onPrimaryContainer=accent,onSurface=Color(0xFF22201C),onBackground=Color(0xFF22201C),onSurfaceVariant=Color(0xFF77716A),outline=Color(0xFFD5CFC6))
    else darkColorScheme(background=if(mode==2)Color(0xFF0C1524) else Color(0xFF100F0D),surface=if(mode==2)Color(0xFF18253A) else Color(0xFF211E1A),surfaceVariant=if(mode==2)Color(0xFF2A3A54) else Color(0xFF34302B),primary=accent,onPrimary=Color(0xFF101724),primaryContainer=if(accentIndex==0)Color(0xFF203353) else accent.copy(alpha=0.15f),onPrimaryContainer=accent,onSurface=Color(0xFFF2EEE7),onBackground=Color(0xFFF2EEE7),onSurfaceVariant=Color(0xFFAAA39A),outline=Color(0xFF3B3731))
    SideEffect{(context as? ComponentActivity)?.enableEdgeToEdge(statusBarStyle=androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT){dark},navigationBarStyle=androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT){dark})}
    val animated=palette.copy(background=animateColorAsState(palette.background,tween(300),label="theme-background").value,surface=animateColorAsState(palette.surface,tween(300),label="theme-surface").value,primary=animateColorAsState(palette.primary,tween(300),label="theme-accent").value)
    val type=Typography()
    MaterialTheme(colorScheme=animated,typography=type.copy(headlineSmall=type.headlineSmall.copy(fontWeight=FontWeight.Medium),titleMedium=type.titleMedium.copy(fontSize=18.sp),bodyLarge=type.bodyLarge.copy(fontSize=17.sp)),shapes=Shapes(small=RoundedCornerShape(18.dp),medium=RoundedCornerShape(24.dp),large=RoundedCornerShape(30.dp)),content=content)
}

@Composable private fun TaskApp(vm: TaskViewModel, openId: Long?, newTask: Boolean, openSchedule:Boolean,newCourse:Boolean,openFilter:Int?,openDay:Int?) {
    val context = LocalContext.current
    val preferences = remember { AppPreferences.prefs(context) }
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val courses by vm.courses.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(if(openSchedule)Screen.SCHEDULE else Screen.HOME) }
    var filter by remember { mutableStateOf(Filter.entries.getOrElse(openFilter ?: preferences.getInt(AppPreferences.HOME_FILTER, 0)) { Filter.TODAY }) }
    var sort by remember { mutableIntStateOf(preferences.getInt(AppPreferences.HOME_SORT, 0).coerceIn(0, 2)) }
    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var showEditor by remember { mutableStateOf(newTask) }
    var editingCourse by remember { mutableStateOf<CourseEntity?>(null) }
    var courseWeekday by remember { mutableIntStateOf(openDay ?: LocalDate.now().dayOfWeek.value) }
    var showCourseEditor by remember { mutableStateOf(newCourse) }
    var backup by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TaskEntity?>(null) }
    var handledOpen by remember { mutableStateOf(false) }
    LaunchedEffect(openId, tasks) { if (!handledOpen && openId != null) tasks.firstOrNull { it.id == openId }?.let { editing = it; showEditor = true; handledOpen=true } }
    val now = System.currentTimeMillis(); val zone = ZoneId.systemDefault(); val today = LocalDate.now(zone)
    fun dateOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val visible = when (screen) {
        Screen.DONE -> tasks.filter { it.completed }
        Screen.HOME -> tasks.filter { !it.completed }.filter {
            when (filter) {
                Filter.TODAY -> it.dueAt == null || it.dueAt?.let(::dateOf) == today
                Filter.UPCOMING -> it.dueAt?.let { ms -> dateOf(ms).isAfter(today) } == true
                Filter.OVERDUE -> it.dueAt?.let { ms -> ms < now } == true
                Filter.ALL -> true
            }
        }
        else -> emptyList()
    }
    AnimatedContent(targetState=if(showEditor) "editor" else if(showCourseEditor) "course" else if(backup) "backup" else "main", transitionSpec={ (fadeIn(tween(230))+slideInHorizontally(tween(300)){it/5}) togetherWith (fadeOut(tween(140))+slideOutHorizontally(tween(240)){-it/7}) },label="page") { page ->
    if(page=="editor") TaskEditorScreen(editing,onBack={showEditor=false},onSave={vm.save(it);showEditor=false})
    else if(page=="course") CourseEditorScreen(editingCourse,courseWeekday,onBack={showCourseEditor=false},onSave={vm.saveCourse(it);showCourseEditor=false},onDelete={vm.deleteCourse(it);showCourseEditor=false})
    else if(page=="backup") BackupScreen{backup=false}
    else Scaffold(containerColor = Bg,
        bottomBar = { Surface(Modifier.navigationBarsPadding().padding(horizontal=12.dp,vertical=8.dp).fillMaxWidth(),shape=RoundedCornerShape(50),color=CardColor,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=0.6f)),tonalElevation=0.dp) {
            Row(Modifier.padding(6.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf(Screen.HOME to Icons.Default.Checklist, Screen.CALENDAR to Icons.Default.DateRange, Screen.SCHEDULE to Icons.Default.School, Screen.DONE to Icons.Default.CheckCircle, Screen.SETTINGS to Icons.Default.Tune).forEach { (s,icon) ->
                    val selected=screen==s
                    val color by animateColorAsState(if(selected)MaterialTheme.colorScheme.primaryContainer else Color.Transparent,label="nav")
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(color).clickable{screen=s}.padding(vertical=18.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(when(s){Screen.HOME->"Görevler";Screen.CALENDAR->"Takvim";Screen.SCHEDULE->"Dersler";Screen.DONE->"Bitenler";Screen.SETTINGS->"Ayarlar"},style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Medium,color=if(selected)Accent else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } },
        floatingActionButton = { if (screen == Screen.HOME || screen == Screen.CALENDAR) FloatingActionButton(onClick = { editing = null; showEditor = true },shape=RoundedCornerShape(50),containerColor=Accent,contentColor=MaterialTheme.colorScheme.onPrimary) { Icon(Icons.Default.Add, "Görev ekle") } }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            AnimatedContent(screen,transitionSpec={ (fadeIn(tween(240))+slideInHorizontally(tween(280)){if(targetState.ordinal>initialState.ordinal)it/12 else -it/12}) togetherWith (fadeOut(tween(130))+slideOutHorizontally(tween(230)){if(targetState.ordinal>initialState.ordinal)-it/12 else it/12}) },label="tab") { currentScreen ->
            when (currentScreen) {
                Screen.HOME, Screen.DONE -> TaskListScreen(currentScreen, filter, { filter = it; preferences.edit().putInt(AppPreferences.HOME_FILTER, it.ordinal).apply() }, sort, { sort = it; preferences.edit().putInt(AppPreferences.HOME_SORT, it).apply() }, tasks, visible, vm::toggle, { editing=it; showEditor=true }, { deleting=it })
                Screen.CALENDAR -> CalendarScreen(tasks.filter { !it.completed }, { editing=it; showEditor=true })
                Screen.SCHEDULE -> ScheduleScreen(courses,{day->editingCourse=null;courseWeekday=day;showCourseEditor=true},{editingCourse=it;courseWeekday=it.weekday;showCourseEditor=true},{vm.deleteCourse(it)},initialDay=openDay ?: LocalDate.now().dayOfWeek.value)
                Screen.SETTINGS -> SettingsScreen(onBackup={backup=true},onHomeFilterChanged = { filter = Filter.entries[it] }, onHomeSortChanged = { sort = it })
            }
            }
        }
    }
    }
    deleting?.let { task -> AlertDialog(onDismissRequest={deleting=null}, title={Text("Görev silinsin mi?")}, text={Text("“${task.title}” kalıcı olarak silinecek.")}, confirmButton={TextButton(onClick={vm.delete(task);deleting=null}){Text("Sil",color=MaterialTheme.colorScheme.error)}}, dismissButton={TextButton(onClick={deleting=null}){Text("Vazgeç")}}) }
}

@Composable private fun TaskListScreen(screen: Screen, filter: Filter, onFilter:(Filter)->Unit, sort:Int, onSort:(Int)->Unit, all:List<TaskEntity>, tasks:List<TaskEntity>, onToggle:(TaskEntity)->Unit, onOpen:(TaskEntity)->Unit, onDelete:(TaskEntity)->Unit) {
    val context=LocalContext.current
    val prefs=remember{AppPreferences.prefs(context)}
    val initialOrder=remember{prefs.getString(AppPreferences.HOME_FILTER_ORDER,"0,1,2,3")!!.split(",").mapNotNull{it.toIntOrNull()?.takeIf{i->i in 0..3}}.distinct().let{if(it.size==4)it else listOf(0,1,2,3)}}
    val filterOrder=remember{mutableStateListOf<Filter>().apply{addAll(initialOrder.map{Filter.entries[it]})}}
    var dragX by remember{mutableFloatStateOf(0f)}
    var draggedFilter by remember{mutableStateOf<Filter?>(null)}
    var targetFilter by remember{mutableStateOf<Filter?>(null)}
    val filterPositions=remember{mutableMapOf<Filter,Pair<Float,Float>>()}
    val active = all.count { !it.completed }; val done = all.count { it.completed }
    var sortMenu by remember { mutableStateOf(false) }
    val shown = when(sort){1->tasks.sortedByDescending{it.priority};2->tasks.sortedBy{it.title.lowercase(Locale("tr"))};else->tasks.sortedWith(compareBy<TaskEntity>{it.dueAt==null}.thenBy{it.dueAt})}
    Column(Modifier.fillMaxSize().padding(horizontal=20.dp)) {
        Spacer(Modifier.height(24.dp)); Text(if(screen==Screen.DONE) "Tamamlananlar" else "Görevler", style=MaterialTheme.typography.headlineLarge, fontWeight=FontWeight.Medium);Spacer(Modifier.height(6.dp));Text(LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM, EEEE",Locale.forLanguageTag("tr"))),color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(screen==Screen.HOME){ Text("$active bekleyen • $done tamamlanan", color=MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(12.dp)); LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){ items(filterOrder,key={it.name}){ f ->
            val lifted=draggedFilter==f
            FilterChip(shape=RoundedCornerShape(50),border=null,colors=FilterChipDefaults.filterChipColors(containerColor=CardColor,selectedContainerColor=MaterialTheme.colorScheme.primaryContainer,selectedLabelColor=Accent),modifier=Modifier.animateItem()
                .onGloballyPositioned { filterPositions[f]=it.positionInParent().x to it.size.width.toFloat() }
                .zIndex(if(lifted)2f else 0f)
                .graphicsLayer { translationX=if(lifted)dragX else 0f;scaleX=if(lifted)1.08f else 1f;scaleY=if(lifted)1.08f else 1f;alpha=if(draggedFilter!=null&&!lifted&&targetFilter==f)0.55f else 1f }
                .background(if(targetFilter==f&&!lifted)Accent.copy(alpha=0.30f) else Color.Transparent,RoundedCornerShape(16.dp))
                .pointerInput(f){detectDragGesturesAfterLongPress(
                    onDragStart={draggedFilter=f;targetFilter=f;dragX=0f},
                    onDragEnd={val from=filterOrder.indexOf(f);val to=filterOrder.indexOf(targetFilter);if(from>=0&&to>=0&&from!=to){filterOrder.removeAt(from);filterOrder.add(to,f);prefs.edit().putString(AppPreferences.HOME_FILTER_ORDER,filterOrder.joinToString(","){it.ordinal.toString()}).apply()};draggedFilter=null;targetFilter=null;dragX=0f},
                    onDragCancel={draggedFilter=null;targetFilter=null;dragX=0f}
                ){change,amount->change.consume();dragX+=amount.x;val origin=filterPositions[f];if(origin!=null){val center=origin.first+origin.second/2+dragX;targetFilter=filterOrder.minByOrNull { candidate -> val pos=filterPositions[candidate];if(pos==null)Float.MAX_VALUE else kotlin.math.abs(center-(pos.first+pos.second/2)) }}}},
                selected=filter==f,onClick={onFilter(f)},label={Text(when(f){Filter.TODAY->"Bugün";Filter.UPCOMING->"Yaklaşan";Filter.OVERDUE->"Geciken";Filter.ALL->"Tümü"})})
        } };Text(if(draggedFilter!=null)"Bırak: ${targetFilter?.let{when(it){Filter.TODAY->"Bugün";Filter.UPCOMING->"Yaklaşan";Filter.OVERDUE->"Geciken";Filter.ALL->"Tümü"}}}" else "Kartları basılı tutup sürükleyerek sırala",style=MaterialTheme.typography.labelSmall,color=Accent);Box{TextButton({sortMenu=true}){Icon(Icons.Default.Sort,null);Spacer(Modifier.width(4.dp));Text("Sırala: ${listOf("Tarih","Öncelik","Ad")[sort]}")};DropdownMenu(sortMenu,{sortMenu=false}){listOf("Tarih ve saat","Öncelik","Görev adı").forEachIndexed{i,label->DropdownMenuItem({Text(label)},{onSort(i);sortMenu=false})}}} }
        Spacer(Modifier.height(12.dp))
        if(shown.isEmpty()) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(if(screen==Screen.DONE)"Henüz tamamlanan görev yok" else "Burada görev yok",color=MaterialTheme.colorScheme.onSurfaceVariant)}
        else LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=90.dp)){items(shown,key={it.id}){task->Box(Modifier.animateItem()){TaskCard(task,onToggle,onOpen,onDelete)}}}
    }
}

@Composable private fun TaskCard(task:TaskEntity,onToggle:(TaskEntity)->Unit,onOpen:(TaskEntity)->Unit,onDelete:(TaskEntity)->Unit){
    Card(Modifier.fillMaxWidth().clickable{onOpen(task)},colors=CardDefaults.cardColors(containerColor=CardColor),shape=RoundedCornerShape(20.dp)){
        Row(Modifier.padding(start=16.dp,end=8.dp,top=12.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(50)).background(if(task.completed)Accent else MaterialTheme.colorScheme.surfaceVariant).clickable{onToggle(task)},contentAlignment=Alignment.Center){AnimatedContent(task.completed,transitionSpec={scaleIn(tween(170)) togetherWith scaleOut(tween(110))},label="completion") { checked->if(checked)Icon(Icons.Default.Check,"Tamamlandı",Modifier.size(19.dp),tint=MaterialTheme.colorScheme.onPrimary) else Box(Modifier.size(19.dp).background(CardColor,RoundedCornerShape(50))) }}
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)){Text(task.title,fontWeight=FontWeight.SemiBold,textDecoration=if(task.completed)TextDecoration.LineThrough else null,color=if(task.completed)MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface);task.dueAt?.let{Text(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM • HH:mm",Locale("tr"))),style=MaterialTheme.typography.bodySmall,color=if(!task.completed&&it<System.currentTimeMillis())MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)}}
            Box(Modifier.size(8.dp).background(when(task.priority){2->Color(0xFFFF6B6B);1->Color(0xFFFFC857);else->Color(0xFF72D6A0)},RoundedCornerShape(50)))
            IconButton({onDelete(task)}){Icon(Icons.Default.MoreVert,"Görevi sil",tint=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
    }
}

@Composable private fun CalendarScreen(tasks:List<TaskEntity>,onOpen:(TaskEntity)->Unit){
    var month by remember{mutableStateOf(YearMonth.now())};var selected by remember{mutableStateOf(LocalDate.now())};val zone=ZoneId.systemDefault();val byDate=tasks.filter{it.dueAt!=null}.groupBy{Instant.ofEpochMilli(it.dueAt!!).atZone(zone).toLocalDate()}
        Column(Modifier.fillMaxSize().padding(16.dp)){Spacer(Modifier.height(16.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){IconButton({month=month.minusMonths(1)}){Icon(Icons.Default.ChevronLeft,null)};Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy",Locale("tr"))),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);IconButton({month=month.plusMonths(1)}){Icon(Icons.Default.ChevronRight,null)}};Row(Modifier.fillMaxWidth()){listOf("Pzt","Sal","Çar","Per","Cum","Cmt","Paz").forEach{Text(it,Modifier.weight(1f),color=MaterialTheme.colorScheme.onSurfaceVariant)}};val blanks=month.atDay(1).dayOfWeek.value-1;for(row in 0 until 6){Row(Modifier.fillMaxWidth()){for(col in 0..6){val day=row*7+col-blanks+1;if(day in 1..month.lengthOfMonth()){val d=month.atDay(day);Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp).background(if(d==selected)Accent else CardColor,RoundedCornerShape(50)).clickable{selected=d},contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(day.toString(),color=if(d==selected)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface);if(byDate[d]?.isNotEmpty()==true)Text("•",color=if(d==selected)MaterialTheme.colorScheme.onPrimary else Accent)}}}else Spacer(Modifier.weight(1f).aspectRatio(1f))}}};Spacer(Modifier.height(10.dp));Text(selected.format(DateTimeFormatter.ofPattern("d MMMM",Locale("tr"))),fontWeight=FontWeight.Bold);LazyColumn{items(byDate[selected].orEmpty()){TaskCard(it,{},onOpen,{})}}}
}

@Composable private fun SettingsScreen(onBackup:()->Unit,onHomeFilterChanged:(Int)->Unit,onHomeSortChanged:(Int)->Unit){
    val context=LocalContext.current
    val prefs=remember{AppPreferences.prefs(context)}
    val scope=rememberCoroutineScope()
    var panel by rememberSaveable{mutableStateOf("root")}
    BackHandler(panel!="root"){panel="root"}
    val notificationLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    val exact=if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S)context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() else true
    var homeFilter by remember{mutableIntStateOf(prefs.getInt(AppPreferences.HOME_FILTER,0).coerceIn(0,3))}
    var homeSort by remember{mutableIntStateOf(prefs.getInt(AppPreferences.HOME_SORT,0).coerceIn(0,2))}
    var widgetFilter by remember{mutableIntStateOf(prefs.getInt(AppPreferences.WIDGET_FILTER,3).coerceIn(0,3))}
    var widgetSort by remember{mutableIntStateOf(prefs.getInt(AppPreferences.WIDGET_SORT,0).coerceIn(0,2))}
    var widgetTheme by remember{mutableIntStateOf(prefs.getInt(AppPreferences.WIDGET_THEME,0).coerceIn(0,2))}
    var widgetPriority by remember{mutableIntStateOf(prefs.getInt(AppPreferences.WIDGET_PRIORITY,-1).coerceIn(-1,2))}
    var scheduleDay by remember{mutableIntStateOf(prefs.getInt(AppPreferences.SCHEDULE_WIDGET_DAY,0).coerceIn(0,7))}
    fun saveWidget(key:String,value:Int){prefs.edit().putInt(key,value).apply();scope.launch{refreshWidgets(context);refreshScheduleWidgets(context)}}
    Column(Modifier.fillMaxSize().padding(horizontal=20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top=18.dp,bottom=14.dp),verticalAlignment=Alignment.CenterVertically){
            if(panel!="root") IconButton({panel="root"}){Icon(Icons.Default.ArrowBack,"Ayarlara dön")}
            Text(when(panel){"widgets"->"Widget yönetimi";"tasks"->"Görev tercihleri";"notifications"->"Bildirimler";"about"->"Hakkında";else->"Ayarlar"},style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        }
        AnimatedContent(panel,transitionSpec={fadeIn(tween(190)) togetherWith fadeOut(tween(100))},label="settings-panel") { current ->
            LazyColumn(verticalArrangement=Arrangement.spacedBy(13.dp),contentPadding=PaddingValues(bottom=30.dp)){
                when(current){
                    "root" -> {
                        item{SettingsLink(Icons.Default.Backup,"Veri yedekleme","ZIP dışa aktar ve geri yükle",onBackup,highlight=true)}
                        item{SectionLabel("Görünüm");AppearanceSettings()}
                        item{SectionLabel("Ana ekran");SettingsLink(Icons.Default.Widgets,"Widget yönetimi","Görevler ve ders programı",{panel="widgets"})}
                        item{SettingsLink(Icons.Default.Tune,"Görev tercihleri","Başlangıç ve sıralama",{panel="tasks"})}
                        item{SectionLabel("Hatırlatıcılar");SettingsLink(Icons.Default.NotificationsNone,"Bildirimler","İzinleri ve hatırlatmaları yönet",{panel="notifications"})}
                        item{SectionLabel("Hakkında");SettingsLink(Icons.Default.Info,"Planvori","Sürüm 1.9",{panel="about"})}
                    }
                    "tasks" -> {
                        item{Text("Görevler",fontWeight=FontWeight.SemiBold,color=Accent)}
                        item{ChoiceSetting("Başlangıç görünümü",listOf("Bugün","Yaklaşan","Geciken","Tüm görevler"),homeFilter){homeFilter=it;prefs.edit().putInt(AppPreferences.HOME_FILTER,it).apply();onHomeFilterChanged(it)}}
                        item{ChoiceSetting("Varsayılan sıralama",listOf("Tarih ve saat","Öncelik","Görev adı"),homeSort){homeSort=it;prefs.edit().putInt(AppPreferences.HOME_SORT,it).apply();onHomeSortChanged(it)}}
                    }
                    "widgets" -> {
                        item{Text("Ortak görünüm",fontWeight=FontWeight.SemiBold,color=Accent)}
                        item{Text("Renk seçimi iki widget'a da uygulanır. Kartlar yuvarlak ve boyuta duyarlıdır.",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)}
                        item{ChoiceSetting("Widget rengi",listOf("Koyu gri","Siyah","Gece mavisi"),widgetTheme){widgetTheme=it;saveWidget(AppPreferences.WIDGET_THEME,it)}}
                        item{HorizontalDivider()}
                        item{Text("Görev widget'ı",fontWeight=FontWeight.SemiBold,color=Accent)}
                        item{ChoiceSetting("Gösterilecek görevler",listOf("Bugün","Yaklaşan","Geciken","Tüm görevler"),widgetFilter){widgetFilter=it;saveWidget(AppPreferences.WIDGET_FILTER,it)}}
                        item{ChoiceSetting("Öncelik seçimi",listOf("Tüm öncelikler","Düşük","Orta","Yüksek"),widgetPriority+1){widgetPriority=it-1;saveWidget(AppPreferences.WIDGET_PRIORITY,widgetPriority)}}
                        item{Text("Görevlerim başlığından öncelik seçebilirsin. Liste sınırı yok; tüm görevler kaydırılarak görüntülenir.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        item{ChoiceSetting("Sıralama",listOf("Tarih ve saat","Öncelik","Görev adı"),widgetSort){widgetSort=it;saveWidget(AppPreferences.WIDGET_SORT,it)}}
                        item{HorizontalDivider()}
                        item{Text("Ders programı widget'ı",fontWeight=FontWeight.SemiBold,color=Accent)}
                        item{ChoiceSetting("Gün seçimi",listOf("Tüm günler","Pazartesi","Salı","Çarşamba","Perşembe","Cuma","Cumartesi","Pazar"),scheduleDay){scheduleDay=it;saveWidget(AppPreferences.SCHEDULE_WIDGET_DAY,it)}}
                        item{Text("Ders Programı başlığından gün seçebilirsin. Her boyutta tüm dersler kaydırılarak görüntülenir.",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)}
                    }
                    "notifications" -> {
                        item{Text("Hatırlatmalar",fontWeight=FontWeight.SemiBold,color=Accent)}
                        item{if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)Button({notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)}){Text("Bildirim izni ver")}else Text("Bildirim izni açık",color=Color(0xFF72D6A0))}
                        item{if(!exact)OutlinedButton({context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:${context.packageName}")))}){Text("Kesin zamanlı hatırlatmaları aç")}else Text("Kesin zamanlı hatırlatmalar açık",color=Color(0xFF72D6A0))}
                        item{Text("Bazı telefonlar pil tasarrufu nedeniyle bildirimleri geciktirebilir.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    }
                    "about" -> {
                        item{Text("Planvori 1.9",fontWeight=FontWeight.SemiBold)}
                        item{Text("Görevlerin ve derslerin yalnızca bu cihazda saklanır; internet bağlantısı gerekmez.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    }
                }
            }
        }
    }
}

@Composable private fun SettingsLink(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,subtitle:String,onClick:()->Unit,highlight:Boolean=false){
    Surface(Modifier.fillMaxWidth().clickable(onClick=onClick),shape=RoundedCornerShape(22.dp),color=if(highlight)CardColor else Color.Transparent){
        Row(Modifier.padding(horizontal=if(highlight)17.dp else 0.dp,vertical=18.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Normal);Spacer(Modifier.height(4.dp));Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Icon(Icons.Default.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun OrderSetting(title:String,labels:List<String>,order:List<Int>,onOrderChanged:(List<Int>)->Unit){
    val items=remember(order){mutableStateListOf<Int>().apply{addAll(order)}}
    var dragY by remember{mutableFloatStateOf(0f)}
    var dragged by remember{mutableStateOf<Int?>(null)}
    var target by remember{mutableStateOf<Int?>(null)}
    val positions=remember{mutableMapOf<Int,Pair<Float,Float>>()}
    Column(Modifier.fillMaxWidth()){
        Text(title,fontWeight=FontWeight.Medium)
        Text("Basılı tutup yukarı-aşağı sürükle",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        items.forEach{item->
            Row(Modifier.fillMaxWidth().padding(vertical=3.dp)
                .onGloballyPositioned{positions[item]=it.positionInParent().y to it.size.height.toFloat()}
                .zIndex(if(dragged==item)2f else 0f)
                .graphicsLayer{translationY=if(dragged==item)dragY else 0f;scaleX=if(dragged==item)1.03f else 1f;alpha=if(target==item&&dragged!=item)0.58f else 1f}
                .background(if(target==item)Accent.copy(alpha=0.24f) else CardColor,RoundedCornerShape(12.dp))
                .pointerInput(item){detectDragGesturesAfterLongPress(
                    onDragStart={dragY=0f;dragged=item;target=item},
                    onDragEnd={val from=items.indexOf(item);val to=items.indexOf(target);if(from>=0&&to>=0&&from!=to){items.removeAt(from);items.add(to,item);onOrderChanged(items.toList())};dragY=0f;dragged=null;target=null},
                    onDragCancel={dragY=0f;dragged=null;target=null}
                ){change,amount->change.consume();dragY+=amount.y;val origin=positions[item];if(origin!=null){val center=origin.first+origin.second/2+dragY;target=items.minByOrNull{candidate->val pos=positions[candidate];if(pos==null)Float.MAX_VALUE else kotlin.math.abs(center-(pos.first+pos.second/2))}}}}
                .padding(horizontal=12.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Default.DragHandle,null,tint=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.width(10.dp));Text(labels[item],Modifier.weight(1f));Text("${items.indexOf(item)+1}",color=Accent)
            }
        }
    }
}

@Composable private fun ChoiceSetting(title:String,choices:List<String>,selected:Int,onSelected:(Int)->Unit){
    var expanded by remember{mutableStateOf(false)}
    Row(Modifier.fillMaxWidth().clickable{expanded=true}.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.Medium);Text(choices[selected],style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Icon(Icons.Default.KeyboardArrowDown,null,tint=Accent)
        DropdownMenu(expanded,{expanded=false}){choices.forEachIndexed{i,label->DropdownMenuItem({Text(label)},{onSelected(i);expanded=false})}}
    }
}
