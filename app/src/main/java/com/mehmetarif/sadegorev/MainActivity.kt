@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mehmetarif.sadegorev

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mehmetarif.sadegorev.data.*
import com.mehmetarif.sadegorev.reminder.AlarmScheduler
import com.mehmetarif.sadegorev.reminder.CourseAlarmScheduler
import com.mehmetarif.sadegorev.widget.refreshScheduleWidgets
import com.mehmetarif.sadegorev.widget.refreshWidgets
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter

internal enum class Screen(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("Görevler", Icons.Outlined.TaskAlt, Icons.Rounded.TaskAlt),
    CALENDAR("Takvim", Icons.Outlined.CalendarMonth, Icons.Rounded.CalendarMonth),
    SCHEDULE("Dersler", Icons.Outlined.School, Icons.Rounded.School),
    DONE("Bitenler", Icons.Outlined.CheckCircle, Icons.Rounded.CheckCircle),
    SETTINGS("Ayarlar", Icons.Outlined.Settings, Icons.Rounded.Settings),
}
internal enum class Filter(val label: String) { TODAY("Bugün"), UPCOMING("Yaklaşan"), OVERDUE("Geciken"), ALL("Tümü") }
private enum class Bucket(val label: String) { OVERDUE("Gecikmiş"), TODAY("Bugün"), TOMORROW("Yarın"), WEEK("Bu hafta"), LATER("Daha sonra"), NODATE("Tarihsiz") }

class TaskViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = TaskDatabase.get(app).taskDao()
    private val courseDao = TaskDatabase.get(app).courseDao()
    val tasks = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val courses = courseDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun saveCourse(course: CourseEntity) = viewModelScope.launch {
        val saved = if (course.id == 0L) course.copy(id = courseDao.insert(course)) else course.also { courseDao.update(it) }
        try { CourseAlarmScheduler.schedule(getApplication(), saved) } finally { refreshScheduleWidgets(getApplication()) }
    }
    fun deleteCourse(course: CourseEntity) = viewModelScope.launch {
        CourseAlarmScheduler.cancel(getApplication(), course.id); courseDao.delete(course); refreshScheduleWidgets(getApplication())
    }
    fun save(task: TaskEntity) = viewModelScope.launch {
        val saved = if (task.id == 0L) task.copy(id = dao.insert(task)) else task.also { dao.update(it) }
        try {
            AlarmScheduler.cancel(getApplication(), saved.id)
            AlarmScheduler.schedule(getApplication(), saved)
        } finally { refreshWidgets(getApplication()) }
    }
    fun toggle(task: TaskEntity) = viewModelScope.launch {
        val changed = task.copy(completed = !task.completed, completedAt = if (task.completed) null else System.currentTimeMillis()); dao.update(changed)
        try {
            if (changed.completed) AlarmScheduler.cancel(getApplication(), changed.id) else AlarmScheduler.schedule(getApplication(), changed)
        } finally { refreshWidgets(getApplication()) }
    }
    fun delete(task: TaskEntity) = viewModelScope.launch {
        AlarmScheduler.cancel(getApplication(), task.id); dao.delete(task); refreshWidgets(getApplication())
    }
    /** Silinen görevi aynı kimlikle geri getirir (Geri al). */
    fun restore(task: TaskEntity) = viewModelScope.launch {
        dao.insert(task)
        try { AlarmScheduler.schedule(getApplication(), task) } finally { refreshWidgets(getApplication()) }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent {
            SadeTheme {
                val vm: TaskViewModel = viewModel()
                TaskApp(vm, intent.getLongExtra("taskId", -1).takeIf { it > 0 }, intent.getBooleanExtra("newTask", false), intent.getBooleanExtra("openSchedule", false), intent.getBooleanExtra("newCourse", false), intent.getIntExtra("taskFilter", -1).takeIf { it in 0..3 }, intent.getIntExtra("courseDay", 0).takeIf { it in 1..7 })
            }
        }
    }
}

/* ───────────────────────── Uygulama iskeleti ───────────────────────── */

@Composable internal fun TaskApp(vm: TaskViewModel, openId: Long?, newTask: Boolean, openSchedule: Boolean, newCourse: Boolean, openFilter: Int?, openDay: Int?) {
    val context = LocalContext.current
    val preferences = remember { AppPreferences.prefs(context) }
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val courses by vm.courses.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(if (openSchedule) Screen.SCHEDULE else Screen.HOME) }
    var filter by remember { mutableStateOf(Filter.entries.getOrElse(openFilter ?: preferences.getInt(AppPreferences.HOME_FILTER, 0)) { Filter.TODAY }) }
    var sort by remember { mutableIntStateOf(preferences.getInt(AppPreferences.HOME_SORT, 0).coerceIn(0, 2)) }
    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var showEditor by remember { mutableStateOf(newTask) }
    var editingCourse by remember { mutableStateOf<CourseEntity?>(null) }
    var courseWeekday by remember { mutableIntStateOf(openDay ?: LocalDate.now().dayOfWeek.value) }
    var showCourseEditor by remember { mutableStateOf(newCourse) }
    var backup by remember { mutableStateOf(false) }
    var handledOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(openId, tasks) { if (!handledOpen && openId != null) tasks.firstOrNull { it.id == openId }?.let { editing = it; showEditor = true; handledOpen = true } }

    fun undoable(message: String, undo: () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(message, "Geri al", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) undo()
        }
    }
    val onToggle: (TaskEntity) -> Unit = { t ->
        vm.toggle(t)
        if (!t.completed) undoable("Tamamlandı: ${t.title}") { vm.toggle(t.copy(completed = true)) }
    }
    val onDelete: (TaskEntity) -> Unit = { t -> vm.delete(t); undoable("Görev silindi") { vm.restore(t) } }
    val openTask: (TaskEntity?) -> Unit = { editing = it; showEditor = true }

    val now = System.currentTimeMillis(); val zone = ZoneId.systemDefault(); val today = LocalDate.now(zone)
    fun dateOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    fun matches(t: TaskEntity, f: Filter) = when (f) {
        Filter.TODAY -> t.dueAt == null || t.dueAt.let(::dateOf) == today
        Filter.UPCOMING -> t.dueAt?.let { ms -> dateOf(ms).isAfter(today) } == true
        Filter.OVERDUE -> t.dueAt?.let { ms -> ms < now } == true
        Filter.ALL -> true
    }
    val active = tasks.filter { !it.completed }
    val counts = Filter.entries.associateWith { f -> active.count { matches(it, f) } }
    val visible = when (screen) {
        Screen.DONE -> tasks.filter { it.completed }
        Screen.HOME -> active.filter { matches(it, filter) }
        else -> emptyList()
    }

    AnimatedContent(targetState = if (showEditor) "editor" else if (showCourseEditor) "course" else if (backup) "backup" else "main",
        transitionSpec = { (fadeIn(tween(220)) + slideInHorizontally(tween(280)) { it / 6 }) togetherWith (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { -it / 8 }) }, label = "page") { page ->
        when (page) {
            "editor" -> TaskEditorScreen(editing, onBack = { showEditor = false }, onSave = { vm.save(it); showEditor = false },
                onDelete = { t -> showEditor = false; onDelete(t) })
            "course" -> CourseEditorScreen(editingCourse, courseWeekday, onBack = { showCourseEditor = false }, onSave = { vm.saveCourse(it); showCourseEditor = false }, onDelete = { vm.deleteCourse(it); showCourseEditor = false })
            "backup" -> BackupScreen { backup = false }
            else -> Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbar) { Snackbar(it, shape = RoundedCornerShape(14.dp), actionColor = MaterialTheme.colorScheme.inversePrimary) } },
                bottomBar = { AppNavigationBar(screen) { screen = it } },
                floatingActionButton = {
                    AnimatedVisibility(screen == Screen.HOME || screen == Screen.CALENDAR, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                        ExtendedFloatingActionButton(onClick = { openTask(null) }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Yeni görev") },
                            shape = RoundedCornerShape(18.dp), containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                    }
                },
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    AnimatedContent(screen, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "tab") { current ->
                        when (current) {
                            Screen.HOME, Screen.DONE -> TaskListScreen(current, filter, { filter = it; preferences.edit().putInt(AppPreferences.HOME_FILTER, it.ordinal).apply() },
                                sort, { sort = it; preferences.edit().putInt(AppPreferences.HOME_SORT, it).apply() }, tasks, visible, counts, onToggle, openTask, onDelete)
                            Screen.CALENDAR -> CalendarScreen(active, openTask, onToggle, onDelete)
                            Screen.SCHEDULE -> ScheduleScreen(courses, { day -> editingCourse = null; courseWeekday = day; showCourseEditor = true }, { editingCourse = it; courseWeekday = it.weekday; showCourseEditor = true }, { vm.deleteCourse(it) }, initialDay = openDay ?: LocalDate.now().dayOfWeek.value)
                            Screen.SETTINGS -> SettingsScreen(onBackup = { backup = true }, onHomeFilterChanged = { filter = Filter.entries[it] }, onHomeSortChanged = { sort = it })
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun AppNavigationBar(current: Screen, onSelect: (Screen) -> Unit) {
    Column {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            Screen.entries.forEach { s ->
                val selected = current == s
                NavigationBarItem(selected = selected, onClick = { onSelect(s) },
                    icon = { Icon(if (selected) s.selectedIcon else s.icon, null) },
                    label = { Text(s.label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant))
            }
        }
    }
}

/* ───────────────────────── Görev listesi ───────────────────────── */

@Composable private fun TaskListScreen(screen: Screen, filter: Filter, onFilter: (Filter) -> Unit, sort: Int, onSort: (Int) -> Unit, all: List<TaskEntity>, tasks: List<TaskEntity>, counts: Map<Filter, Int>,
                                       onToggle: (TaskEntity) -> Unit, onOpen: (TaskEntity) -> Unit, onDelete: (TaskEntity) -> Unit) {
    val zone = ZoneId.systemDefault(); val today = LocalDate.now(zone); val now = System.currentTimeMillis()
    var sortMenu by remember { mutableStateOf(false) }
    val shown = when (sort) {
        1 -> tasks.sortedWith(compareByDescending<TaskEntity> { it.priority }.thenBy { it.dueAt ?: Long.MAX_VALUE })
        2 -> tasks.sortedBy { it.title.lowercase(TR) }
        else -> if (screen == Screen.DONE) tasks.sortedByDescending { it.completedAt ?: it.dueAt ?: it.createdAt } else tasks.sortedWith(compareBy<TaskEntity> { it.dueAt == null }.thenBy { it.dueAt })
    }
    fun bucketOf(t: TaskEntity): Bucket {
        val due = t.dueAt ?: return Bucket.NODATE
        val d = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
        return when {
            due < now -> Bucket.OVERDUE
            d == today -> Bucket.TODAY
            d == today.plusDays(1) -> Bucket.TOMORROW
            d.isBefore(today.plusDays(7)) -> Bucket.WEEK
            else -> Bucket.LATER
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 110.dp)) {
        item(key = "header") {
            if (screen == Screen.DONE) PageHeader("Tamamlananlar", subtitle = "${tasks.size} görev tamamlandı")
            else PageHeader("Görevler", eyebrow = greeting(), subtitle = longDate(today)) {
                Box {
                    FilledTonalIconButton({ sortMenu = true }, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Icon(Icons.AutoMirrored.Rounded.Sort, "Sırala")
                    }
                    DropdownMenu(sortMenu, { sortMenu = false }, shape = RoundedCornerShape(16.dp)) {
                        Text("Sırala", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        listOf("Tarih ve saat", "Öncelik", "Görev adı").forEachIndexed { i, label ->
                            DropdownMenuItem({ Text(label, fontWeight = if (sort == i) FontWeight.SemiBold else FontWeight.Normal) }, { onSort(i); sortMenu = false },
                                trailingIcon = { if (sort == i) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) })
                        }
                    }
                }
            }
        }
        if (screen == Screen.HOME) {
            item(key = "progress") { TodayCard(all, Modifier.padding(top = 4.dp, bottom = 16.dp)) }
            item(key = "filters") { FilterRow(filter, counts, onFilter) }
        }
        if (shown.isEmpty()) item(key = "empty") {
            if (screen == Screen.DONE) EmptyState(Icons.Rounded.DoneAll, "Henüz tamamlanan yok", "Bitirdiğin görevler burada birikir. İlkini tamamla!")
            else EmptyState(Icons.Rounded.TaskAlt, when (filter) { Filter.OVERDUE -> "Geciken görev yok"; Filter.UPCOMING -> "Yaklaşan görev yok"; else -> "Her şey yolunda" },
                when (filter) { Filter.OVERDUE -> "Harika, takvimin güncel."; Filter.UPCOMING -> "İleri tarihli bir görev eklediğinde burada görünür."; else -> "Yeni görev ekleyerek gününü planla." })
        } else if (screen == Screen.HOME && sort == 0) {
            shown.groupBy(::bucketOf).toSortedMap().forEach { (bucket, list) ->
                item(key = "h-${bucket.name}") {
                    SectionHeader(bucket.label, Modifier.animateItem(), count = list.size, color = if (bucket == Bucket.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(list, key = { it.id }) { task -> SwipeableTaskCard(task, onToggle, onOpen, onDelete, Modifier.animateItem().padding(bottom = 10.dp)) }
            }
        } else {
            item(key = "spacer") { Spacer(Modifier.height(if (screen == Screen.HOME) 14.dp else 4.dp)) }
            items(shown, key = { it.id }) { task -> SwipeableTaskCard(task, onToggle, onOpen, onDelete, Modifier.animateItem().padding(bottom = 10.dp)) }
        }
    }
}

/** Bugünkü ilerleme kartı: tamamlanma halkası ve özet sayılar. */
@Composable private fun TodayCard(all: List<TaskEntity>, modifier: Modifier = Modifier) {
    val (done, total, overdue, upcoming) = dayProgress(all, System.currentTimeMillis())
    val progress by animateFloatAsState(if (total == 0) 0f else done.toFloat() / total, tween(600), label = "progress")
    val primary = MaterialTheme.colorScheme.primary
    val dark = MaterialTheme.colorScheme.background.let { (it.red + it.green + it.blue) / 3 < 0.5f }
    val on = Color.White
    val gradient = if (dark) listOf(lerp(primary, Color.Black, 0.38f), lerp(primary, Color.Black, 0.62f))
        else listOf(primary, lerp(primary, Color(0xFF1A1440), 0.28f))
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(Brush.linearGradient(gradient))
        .padding(20.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bugünkü ilerleme", style = MaterialTheme.typography.labelLarge, color = on.copy(alpha = 0.8f))
                    Spacer(Modifier.height(4.dp))
                    Text(if (total == 0) "Plan boş" else "$done / $total görev", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = on)
                    Text(when { total == 0 -> "Bugün için bir görev ekle"; done == total -> "Harika, hepsini bitirdin!"; else -> "${total - done} görev kaldı" },
                        style = MaterialTheme.typography.bodyMedium, color = on.copy(alpha = 0.85f))
                }
                ProgressRing(progress, on, Modifier.size(68.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill(Icons.Rounded.ErrorOutline, "$overdue geciken", on)
                StatPill(Icons.Rounded.Upcoming, "$upcoming yaklaşan", on)
            }
        }
    }
}

@Composable private fun ProgressRing(progress: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx(); val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(color.copy(alpha = 0.22f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(color, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("%${(progress * 100).toInt()}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable private fun StatPill(icon: ImageVector, text: String, color: Color) {
    Row(Modifier.clip(CircleShape).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(15.dp), tint = color)
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Filtre hapları; basılı tutup sürükleyerek sıralanabilir. */
@Composable private fun FilterRow(filter: Filter, counts: Map<Filter, Int>, onFilter: (Filter) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences.prefs(context) }
    val initialOrder = remember { prefs.getString(AppPreferences.HOME_FILTER_ORDER, "0,1,2,3")!!.split(",").mapNotNull { it.toIntOrNull()?.takeIf { i -> i in 0..3 } }.distinct().let { if (it.size == 4) it else listOf(0, 1, 2, 3) } }
    val filterOrder = remember { mutableStateListOf<Filter>().apply { addAll(initialOrder.map { Filter.entries[it] }) } }
    var dragX by remember { mutableFloatStateOf(0f) }
    var draggedFilter by remember { mutableStateOf<Filter?>(null) }
    var targetFilter by remember { mutableStateOf<Filter?>(null) }
    val filterPositions = remember { mutableMapOf<Filter, Pair<Float, Float>>() }
    val listState = rememberLazyListState()
    LaunchedEffect(filter) {
        // Seçili filtre ekran dışındaysa görünür olacak şekilde kaydır
        val index = filterOrder.indexOf(filter)
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
        if (index >= 0 && (item == null || item.offset < 0 || item.offset + item.size > info.viewportEndOffset)) listState.animateScrollToItem((index - 1).coerceAtLeast(0))
    }
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(filterOrder, key = { it.name }) { f ->
            val lifted = draggedFilter == f
            val selected = filter == f
            val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface, label = "pill")
            val fg = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
            Surface(onClick = { onFilter(f) }, shape = CircleShape, color = bg,
                border = if (selected) null else BorderStroke(1.dp, if (targetFilter == f && !lifted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                modifier = Modifier.animateItem()
                    .onGloballyPositioned { filterPositions[f] = it.positionInParent().x to it.size.width.toFloat() }
                    .zIndex(if (lifted) 2f else 0f)
                    .graphicsLayer { translationX = if (lifted) dragX else 0f; scaleX = if (lifted) 1.08f else 1f; scaleY = if (lifted) 1.08f else 1f; alpha = if (draggedFilter != null && !lifted && targetFilter == f) 0.55f else 1f }
                    .pointerInput(f) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { draggedFilter = f; targetFilter = f; dragX = 0f },
                            onDragEnd = { val from = filterOrder.indexOf(f); val to = filterOrder.indexOf(targetFilter); if (from >= 0 && to >= 0 && from != to) { filterOrder.removeAt(from); filterOrder.add(to, f); prefs.edit().putString(AppPreferences.HOME_FILTER_ORDER, filterOrder.joinToString(",") { it.ordinal.toString() }).apply() }; draggedFilter = null; targetFilter = null; dragX = 0f },
                            onDragCancel = { draggedFilter = null; targetFilter = null; dragX = 0f }
                        ) { change, amount -> change.consume(); dragX += amount.x; val origin = filterPositions[f]; if (origin != null) { val center = origin.first + origin.second / 2 + dragX; targetFilter = filterOrder.minByOrNull { c -> val pos = filterPositions[c]; if (pos == null) Float.MAX_VALUE else kotlin.math.abs(center - (pos.first + pos.second / 2)) } } }
                    }) {
                Row(Modifier.padding(start = 16.dp, end = 10.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(f.label, style = MaterialTheme.typography.labelLarge, color = fg)
                    Spacer(Modifier.width(7.dp))
                    val n = counts[f] ?: 0
                    Box(Modifier.clip(CircleShape).background(if (selected) fg.copy(alpha = 0.18f) else if (f == Filter.OVERDUE && n > 0) MaterialTheme.colorScheme.error.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 7.dp, vertical = 1.dp)) {
                        Text(n.toString(), style = MaterialTheme.typography.labelMedium, color = if (!selected && f == Filter.OVERDUE && n > 0) MaterialTheme.colorScheme.error else fg)
                    }
                }
            }
        }
    }
}

/** Sağa kaydır: tamamla · Sola kaydır: sil. */
@Composable private fun SwipeableTaskCard(task: TaskEntity, onToggle: (TaskEntity) -> Unit, onOpen: (TaskEntity) -> Unit, onDelete: (TaskEntity) -> Unit, modifier: Modifier = Modifier) {
    val currentTask by rememberUpdatedState(task)
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        when (value) {
            SwipeToDismissBoxValue.StartToEnd -> { onToggle(currentTask); false }
            SwipeToDismissBoxValue.EndToStart -> { onDelete(currentTask); true }
            else -> false
        }
    })
    SwipeToDismissBox(state, modifier = modifier, backgroundContent = {
        val direction = state.dismissDirection
        val (color, icon, label) = when (direction) {
            SwipeToDismissBoxValue.StartToEnd -> Triple(Color(0xFF1FA463), if (task.completed) Icons.Rounded.Undo else Icons.Rounded.Check, if (task.completed) "Geri al" else "Tamamla")
            SwipeToDismissBoxValue.EndToStart -> Triple(MaterialTheme.colorScheme.error, Icons.Rounded.DeleteOutline, "Sil")
            else -> Triple(Color.Transparent, null, "")
        }
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(color).padding(horizontal = 22.dp),
            contentAlignment = if (direction == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart) {
            if (icon != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Color.White); Spacer(Modifier.width(8.dp))
                Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }) { TaskCard(task, onToggle, onOpen) }
}

@Composable private fun TaskCard(task: TaskEntity, onToggle: (TaskEntity) -> Unit, onOpen: (TaskEntity) -> Unit) {
    val overdue = !task.completed && (task.dueAt ?: Long.MAX_VALUE) < System.currentTimeMillis()
    AppCard(Modifier.fillMaxWidth(), onClick = { onOpen(task) }, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            TaskCheckbox(task.completed, priorityColor(task.priority)) { onToggle(task) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                    color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                if (task.description.isNotBlank()) Text(task.description.lineSequence().first(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                val hasMeta = task.dueAt != null || (task.reminderAt != null && !task.completed) || task.priority == 2
                if (hasMeta) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    task.dueAt?.let { MetaLabel(if (overdue) Icons.Rounded.EventBusy else Icons.Outlined.Event, friendlyDateTime(it), if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (task.reminderAt != null && !task.completed) Icon(Icons.Outlined.NotificationsActive, "Hatırlatma var", Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (task.priority == 2 && !task.completed) MetaLabel(Icons.Rounded.Flag, "Yüksek", priorityColor(2))
                }
            }
        }
    }
}

@Composable private fun MetaLabel(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(15.dp), tint = color)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Öncelik renginde yuvarlak onay kutusu. */
@Composable internal fun TaskCheckbox(checked: Boolean, color: Color, onClick: () -> Unit) {
    val fill by animateColorAsState(if (checked) color else color.copy(alpha = 0.08f), tween(200), label = "check")
    Box(Modifier.size(26.dp).clip(CircleShape).background(fill).border(2.dp, color, CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        AnimatedVisibility(checked, enter = scaleIn(tween(180)) + fadeIn(), exit = scaleOut(tween(120)) + fadeOut()) {
            Icon(Icons.Rounded.Check, "Tamamlandı", Modifier.size(17.dp), tint = Color.White)
        }
    }
}

/* ───────────────────────── Takvim ───────────────────────── */

@Composable private fun CalendarScreen(tasks: List<TaskEntity>, onOpen: (TaskEntity) -> Unit, onToggle: (TaskEntity) -> Unit, onDelete: (TaskEntity) -> Unit) {
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(today) }
    val zone = ZoneId.systemDefault()
    val byDate = tasks.filter { it.dueAt != null }.groupBy { Instant.ofEpochMilli(it.dueAt!!).atZone(zone).toLocalDate() }
    val dayTasks = byDate[selected].orEmpty().sortedBy { it.dueAt }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 110.dp)) {
        item {
            PageHeader("Takvim", subtitle = "${tasks.count { it.dueAt != null && YearMonth.from(Instant.ofEpochMilli(it.dueAt).atZone(zone)) == month }} görev bu ay") {
                if (month != YearMonth.now() || selected != today) TextButton({ month = YearMonth.now(); selected = today }) { Text("Bugün") }
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", TR)).capitalizeTr(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        FilledTonalIconButton({ month = month.minusMonths(1) }, Modifier.size(36.dp)) { Icon(Icons.Rounded.ChevronLeft, "Önceki ay") }
                        Spacer(Modifier.width(6.dp))
                        FilledTonalIconButton({ month = month.plusMonths(1) }, Modifier.size(36.dp)) { Icon(Icons.Rounded.ChevronRight, "Sonraki ay") }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz").forEach {
                            Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    val blanks = month.atDay(1).dayOfWeek.value - 1
                    val rows = (blanks + month.lengthOfMonth() + 6) / 7
                    for (row in 0 until rows) {
                        Row(Modifier.fillMaxWidth()) {
                            for (col in 0..6) {
                                val day = row * 7 + col - blanks + 1
                                if (day in 1..month.lengthOfMonth()) {
                                    val d = month.atDay(day)
                                    CalendarDay(d, d == selected, d == today, byDate[d].orEmpty(), Modifier.weight(1f)) { selected = d }
                                } else Spacer(Modifier.weight(1f).height(52.dp))
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionHeader(selected.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", TR)), Modifier.padding(top = 6.dp), count = dayTasks.size)
        }
        if (dayTasks.isEmpty()) item { EmptyState(Icons.Rounded.EventAvailable, "Bu gün boş", "Seçili gün için planlanmış görev yok.", Modifier.padding(vertical = 0.dp)) }
        else items(dayTasks, key = { it.id }) { SwipeableTaskCard(it, onToggle, onOpen, onDelete, Modifier.animateItem().padding(bottom = 10.dp)) }
    }
}

@Composable private fun CalendarDay(date: LocalDate, selected: Boolean, isToday: Boolean, tasks: List<TaskEntity>, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, tween(180), label = "day")
    Column(modifier.height(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(bg)
            .then(if (isToday && !selected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
            .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = when { selected -> MaterialTheme.colorScheme.onPrimary; isToday -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurface })
        }
        Row(Modifier.height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            tasks.sortedByDescending { it.priority }.take(3).forEach { Box(Modifier.size(5.dp).clip(CircleShape).background(priorityColor(it.priority))) }
        }
    }
}

/* ───────────────────────── Ayarlar ───────────────────────── */

private val TintBlue = Color(0xFF3E8BFF)
private val TintOrange = Color(0xFFF08A24)
private val TintPurple = Color(0xFF9B6BFF)
private val TintGreen = Color(0xFF1FA463)
private val TintGray = Color(0xFF7D8596)
private val TintPink = Color(0xFFE5508F)

@Composable private fun SettingsScreen(onBackup: () -> Unit, onHomeFilterChanged: (Int) -> Unit, onHomeSortChanged: (Int) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences.prefs(context) }
    val scope = rememberCoroutineScope()
    var panel by rememberSaveable { mutableStateOf("root") }
    BackHandler(panel != "root") { panel = "root" }
    var resumeTick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) { val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumeTick++ }; owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) } }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }
    val notificationsOn = remember(resumeTick) { Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED }
    val exact = remember(resumeTick) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() else true }
    var homeFilter by remember { mutableIntStateOf(prefs.getInt(AppPreferences.HOME_FILTER, 0).coerceIn(0, 3)) }
    var homeSort by remember { mutableIntStateOf(prefs.getInt(AppPreferences.HOME_SORT, 0).coerceIn(0, 2)) }
    var widgetFilter by remember { mutableIntStateOf(prefs.getInt(AppPreferences.WIDGET_FILTER, 3).coerceIn(0, 3)) }
    var widgetSort by remember { mutableIntStateOf(prefs.getInt(AppPreferences.WIDGET_SORT, 0).coerceIn(0, 2)) }
    var widgetTheme by remember { mutableIntStateOf(prefs.getInt(AppPreferences.WIDGET_THEME, 0).coerceIn(0, 2)) }
    var widgetPriority by remember { mutableIntStateOf(prefs.getInt(AppPreferences.WIDGET_PRIORITY, -1).coerceIn(-1, 2)) }
    var scheduleDay by remember { mutableIntStateOf(prefs.getInt(AppPreferences.SCHEDULE_WIDGET_DAY, 0).coerceIn(0, 7)) }
    fun saveWidget(key: String, value: Int) { prefs.edit().putInt(key, value).apply(); scope.launch { refreshWidgets(context); refreshScheduleWidgets(context) } }

    AnimatedContent(panel, transitionSpec = { (fadeIn(tween(200)) + slideInHorizontally(tween(240)) { if (targetState == "root") -it / 10 else it / 10 }) togetherWith fadeOut(tween(100)) }, label = "settings-panel") { current ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp)) {
            item {
                if (current == "root") PageHeader("Ayarlar")
                else Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ panel = "root" }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Ayarlara dön") }
                    Text(when (current) { "widgets" -> "Widget'lar"; "tasks" -> "Görev tercihleri"; "notifications" -> "Bildirimler"; else -> "Hakkında" }, style = MaterialTheme.typography.headlineMedium)
                }
            }
            when (current) {
                "root" -> {
                    item {
                        AppCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                                    Icon(painterResource(R.drawable.ic_brand_mark), null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Planvori", style = MaterialTheme.typography.titleLarge)
                                    Text("Sürüm ${BuildConfig.VERSION_NAME} · Verilerin bu cihazda", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    item { SettingsGroup(title = "Görünüm") { AppearanceSettings() } }
                    item {
                        SettingsGroup(title = "Genel") {
                            SettingsRow(Icons.Rounded.Tune, "Görev tercihleri", subtitle = "Başlangıç görünümü ve sıralama", tint = TintBlue, onClick = { panel = "tasks" })
                            GroupDivider()
                            SettingsRow(Icons.Rounded.NotificationsActive, "Bildirimler", subtitle = if (notificationsOn && exact) "Hatırlatmalar açık" else "İzin gerekiyor", tint = TintOrange, onClick = { panel = "notifications" },
                                trailing = if (notificationsOn && exact) null else ({ Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error)) }))
                            GroupDivider()
                            SettingsRow(Icons.Rounded.Widgets, "Widget'lar", subtitle = "Görevler ve ders programı", tint = TintPurple, onClick = { panel = "widgets" })
                        }
                    }
                    item {
                        SettingsGroup(title = "Veriler") {
                            SettingsRow(Icons.Rounded.CloudUpload, "Yedekleme ve geri yükleme", subtitle = "Google Drive veya ZIP dosyası", tint = TintGreen, onClick = onBackup)
                        }
                    }
                    item {
                        SettingsGroup(title = "Destek") {
                            SettingsRow(Icons.Rounded.Info, "Hakkında", tint = TintGray, value = BuildConfig.VERSION_NAME, onClick = { panel = "about" })
                        }
                    }
                }
                "tasks" -> {
                    item {
                        SettingsGroup {
                            ChoiceRow(Icons.Rounded.Today, "Başlangıç görünümü", listOf("Bugün", "Yaklaşan", "Geciken", "Tüm görevler"), homeFilter, TintBlue) { homeFilter = it; prefs.edit().putInt(AppPreferences.HOME_FILTER, it).apply(); onHomeFilterChanged(it) }
                            GroupDivider()
                            ChoiceRow(Icons.AutoMirrored.Rounded.Sort, "Varsayılan sıralama", listOf("Tarih ve saat", "Öncelik", "Görev adı"), homeSort, TintPurple) { homeSort = it; prefs.edit().putInt(AppPreferences.HOME_SORT, it).apply(); onHomeSortChanged(it) }
                        }
                    }
                    item { Caption("İpucu: Görevler ekranındaki filtreleri basılı tutup sürükleyerek sıralayabilirsin. Bir görevi sağa kaydırınca tamamlanır, sola kaydırınca silinir.") }
                }
                "widgets" -> {
                    item { SettingsGroup(title = "Ortak görünüm") { ChoiceRow(Icons.Rounded.Palette, "Widget rengi", listOf("Koyu gri", "Siyah", "Gece mavisi"), widgetTheme, TintPink) { widgetTheme = it; saveWidget(AppPreferences.WIDGET_THEME, it) } } }
                    item {
                        SettingsGroup(title = "Görevlerim widget'ı") {
                            ChoiceRow(Icons.Rounded.FilterList, "Gösterilecek görevler", listOf("Bugün", "Yaklaşan", "Geciken", "Tüm görevler"), widgetFilter, TintBlue) { widgetFilter = it; saveWidget(AppPreferences.WIDGET_FILTER, it) }
                            GroupDivider()
                            ChoiceRow(Icons.Rounded.Flag, "Öncelik", listOf("Tüm öncelikler", "Düşük", "Orta", "Yüksek"), widgetPriority + 1, TintOrange) { widgetPriority = it - 1; saveWidget(AppPreferences.WIDGET_PRIORITY, widgetPriority) }
                            GroupDivider()
                            ChoiceRow(Icons.AutoMirrored.Rounded.Sort, "Sıralama", listOf("Tarih ve saat", "Öncelik", "Görev adı"), widgetSort, TintPurple) { widgetSort = it; saveWidget(AppPreferences.WIDGET_SORT, it) }
                        }
                    }
                    item {
                        SettingsGroup(title = "Ders programı widget'ı") {
                            ChoiceRow(Icons.Rounded.CalendarViewWeek, "Gün", listOf("Tüm günler", "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar"), scheduleDay, TintGreen) { scheduleDay = it; saveWidget(AppPreferences.SCHEDULE_WIDGET_DAY, it) }
                        }
                    }
                    item { Caption("Widget başlığına dokunarak da öncelik veya gün seçebilirsin. Tüm öğeler widget içinde kaydırılarak görüntülenir.") }
                }
                "notifications" -> {
                    item {
                        SettingsGroup {
                            SettingsRow(Icons.Rounded.Notifications, "Bildirim izni", subtitle = if (notificationsOn) "Açık" else "Kapalı · hatırlatmalar görünmez", tint = TintOrange, showChevron = false,
                                trailing = { if (notificationsOn) Icon(Icons.Rounded.CheckCircle, null, tint = TintGreen) else FilledTonalButton({ if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("İzin ver") } })
                            GroupDivider()
                            SettingsRow(Icons.Rounded.Alarm, "Tam saatinde hatırlat", subtitle = if (exact) "Açık" else "Kapalı · bildirimler gecikebilir", tint = TintBlue, showChevron = false,
                                trailing = { if (exact) Icon(Icons.Rounded.CheckCircle, null, tint = TintGreen) else FilledTonalButton({ context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${context.packageName}"))) }) { Text("Aç") } })
                        }
                    }
                    item {
                        var insistent by remember { mutableStateOf(prefs.getBoolean(AppPreferences.INSISTENT_REMINDERS, false)) }
                        SettingsGroup(title = "Dikkat çekme") {
                            SettingsRow(Icons.Rounded.NotificationImportant, "Israrlı hatırlatma", subtitle = "Bildirimi açana kadar ses tekrarlanır", tint = TintPink, showChevron = false,
                                onClick = { insistent = !insistent; prefs.edit().putBoolean(AppPreferences.INSISTENT_REMINDERS, insistent).apply() },
                                trailing = { Switch(insistent, { insistent = it; prefs.edit().putBoolean(AppPreferences.INSISTENT_REMINDERS, it).apply() }) })
                            GroupDivider()
                            SettingsRow(Icons.Rounded.Send, "Deneme bildirimi gönder", subtitle = "Hatırlatmanın nasıl göründüğünü gör", tint = TintPurple, onClick = { com.mehmetarif.sadegorev.reminder.ReminderNotifications.showTest(context) })
                        }
                    }
                    item { Caption("Bazı telefonlar pil tasarrufu nedeniyle bildirimleri geciktirebilir. Ders hatırlatmaları ayrı bir bildirim kanalı kullanır; Android ayarlarından ayrıca yönetebilirsin.") }
                }
                "about" -> {
                    item {
                        AppCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Planvori ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleLarge)
                                Text("Görevlerin, ders programın ve ayarların yalnızca bu cihazda saklanır. İnternet yalnızca Google Drive yedeği için kullanılır.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Caption(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp))
