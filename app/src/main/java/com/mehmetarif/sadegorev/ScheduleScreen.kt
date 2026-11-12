package com.mehmetarif.sadegorev

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mehmetarif.sadegorev.data.CourseEntity
import com.mehmetarif.sadegorev.reminder.DEFAULT_COURSE_REMINDER
import com.mehmetarif.sadegorev.reminder.courseReminderSummary
import com.mehmetarif.sadegorev.reminder.formatCourseReminders
import com.mehmetarif.sadegorev.reminder.reminderOffsets
import com.mehmetarif.sadegorev.reminder.courseReminderOptions
import java.time.LocalDate
import java.time.LocalTime

private val days = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
private val fullDays = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")

@Composable fun ScheduleScreen(courses: List<CourseEntity>, onAdd: (Int) -> Unit, onEdit: (CourseEntity) -> Unit, onDelete: (CourseEntity) -> Unit, initialDay: Int = LocalDate.now().dayOfWeek.value) {
    var selected by rememberSaveable { mutableIntStateOf(initialDay.coerceIn(1, 7)) }
    val today = LocalDate.now()
    val weekStart = today.minusDays(today.dayOfWeek.value - 1L)
    val dayCourses = courses.filter { it.weekday == selected }.sortedBy { it.startMinute }
    val nowMinute = LocalTime.now().let { it.hour * 60 + it.minute }
    val isToday = selected == today.dayOfWeek.value
    val current = if (isToday) dayCourses.firstOrNull { nowMinute in it.startMinute until it.endMinute } else null
    val next = if (isToday && current == null) dayCourses.firstOrNull { it.startMinute > nowMinute } else null
    val todayCount = courses.count { it.weekday == today.dayOfWeek.value }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp)) {
        item {
            PageHeader("Ders programı", subtitle = if (todayCount == 0) "Bugün dersin yok" else "Bugün $todayCount ders · haftada ${courses.size}") {
                FilledTonalIconButton({ onAdd(selected) }, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                    Icon(Icons.Rounded.Add, "Ders ekle")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                days.forEachIndexed { index, label ->
                    val day = index + 1
                    val active = selected == day
                    val date = weekStart.plusDays(index.toLong())
                    val bg by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, tween(200), label = "weekday")
                    Surface(onClick = { selected = day }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), color = bg,
                        border = if (active) null else BorderStroke(1.dp, if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = if (active) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.size(5.dp).clip(CircleShape).background(if (courses.any { it.weekday == day }) (if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary) else Color.Transparent))
                        }
                    }
                }
            }
        }
        item { SectionHeader(fullDays[selected - 1] + if (isToday) " · Bugün" else "", count = dayCourses.size) }
        if (dayCourses.isEmpty()) item {
            EmptyState(Icons.Rounded.School, "Bu güne ders eklenmemiş", "${fullDays[selected - 1]} programını oluşturmak için ders ekle.") {
                FilledTonalButton({ onAdd(selected) }) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Ders ekle") }
            }
        } else items(dayCourses, key = { it.id }) { course ->
            CourseCard(course, when (course) { current -> "Şimdi"; next -> "Sıradaki"; else -> null }, Modifier.animateItem().padding(bottom = 10.dp)) { onEdit(course) }
        }
    }
}

@Composable private fun CourseCard(course: CourseEntity, badge: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val color = CourseColors[course.color.coerceIn(0, 4)]
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.width(52.dp).padding(top = 14.dp)) {
            Text(clockText(course.startMinute), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(clockText(course.endMinute), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AppCard(Modifier.weight(1f), onClick = onClick, shape = RoundedCornerShape(18.dp),
            color = if (badge == "Şimdi") color.copy(alpha = 0.12f).compositeOverSurface() else MaterialTheme.colorScheme.surface) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(5.dp).fillMaxHeight().background(color))
                Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(course.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (badge != null) {
                            Spacer(Modifier.width(8.dp))
                            Text(badge, style = MaterialTheme.typography.labelMedium, color = Color.White,
                                modifier = Modifier.clip(CircleShape).background(if (badge == "Şimdi") color else MaterialTheme.colorScheme.primary).padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                    val meta = buildList {
                        if (course.location.isNotBlank()) add(Icons.Outlined.Place to course.location)
                        if (course.instructor.isNotBlank()) add(Icons.Outlined.Person to course.instructor)
                    }
                    if (meta.isNotEmpty()) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { meta.forEach { (icon, text) -> CourseMeta(icon, text) } }
                    if (course.reminderOffsets().isNotEmpty()) Row(Modifier.padding(top = 6.dp)) { CourseMeta(Icons.Rounded.NotificationsActive, courseReminderSummary(course.reminderOffsets()), MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}

@Composable private fun Color.compositeOverSurface(): Color = this.compositeOver(MaterialTheme.colorScheme.surface)

@Composable private fun CourseMeta(icon: ImageVector, text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = color)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun CourseEditorScreen(existing: CourseEntity?, initialWeekday: Int, onBack: () -> Unit, onSave: (CourseEntity) -> Unit, onDelete: (CourseEntity) -> Unit, initialDay: Int = LocalDate.now().dayOfWeek.value) {
    var title by rememberSaveable(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var location by rememberSaveable(existing?.id) { mutableStateOf(existing?.location.orEmpty()) }
    var instructor by rememberSaveable(existing?.id) { mutableStateOf(existing?.instructor.orEmpty()) }
    var weekday by rememberSaveable(existing?.id, initialWeekday) { mutableIntStateOf(existing?.weekday ?: initialWeekday) }
    var start by rememberSaveable(existing?.id) { mutableIntStateOf(existing?.startMinute ?: 9 * 60) }
    var end by rememberSaveable(existing?.id) { mutableIntStateOf(existing?.endMinute ?: 10 * 60) }
    var color by rememberSaveable(existing?.id) { mutableIntStateOf(existing?.color ?: 0) }
    var reminderOffsets by rememberSaveable(existing?.id) { mutableStateOf(existing?.reminderOffsets() ?: listOf(DEFAULT_COURSE_REMINDER)) }
    var settingTime by remember { mutableIntStateOf(-1) }
    var confirmDelete by remember { mutableStateOf(false) }
    val time = key(settingTime) { rememberTimePickerState(initialHour = (if (settingTime == 0) start else end) / 60, initialMinute = (if (settingTime == 0) start else end) % 60, is24Hour = true) }
    BackHandler { if (settingTime != -1) settingTime = -1 else onBack() }
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(if (settingTime != -1) (if (settingTime == 0) "Başlangıç saati" else "Bitiş saati") else if (existing == null) "Yeni ders" else "Dersi düzenle", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton({ if (settingTime != -1) settingTime = -1 else onBack() }) { Icon(if (settingTime != -1) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.Close, "Geri") } },
                actions = { if (settingTime == -1 && existing != null) IconButton({ confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, "Dersi sil", tint = MaterialTheme.colorScheme.error) } })
        },
        bottomBar = {
            Button(enabled = settingTime != -1 || (title.isNotBlank() && end > start), onClick = {
                if (settingTime != -1) { val value = time.hour * 60 + time.minute; if (settingTime == 0) start = value else end = value; settingTime = -1 }
                else onSave((existing ?: CourseEntity(title = title, weekday = weekday, startMinute = start, endMinute = end)).copy(title = title.trim(), weekday = weekday, startMinute = start, endMinute = end, location = location.trim(), instructor = instructor.trim(), color = color, reminderMinutes = reminderOffsets.maxOrNull() ?: -1, reminders = formatCourseReminders(reminderOffsets)))
            }, modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp).height(56.dp), shape = RoundedCornerShape(18.dp)) {
                Text(if (settingTime != -1) "Saati uygula" else if (existing == null) "Dersi ekle" else "Değişiklikleri kaydet", style = MaterialTheme.typography.titleMedium)
            }
        }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (settingTime != -1) {
                AppCard(Modifier.fillMaxWidth()) { Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { TimeInput(time) } }
            } else {
                AppCard(Modifier.fillMaxWidth()) {
                    BareTextField(title, { title = it }, "Ders adı", style = MaterialTheme.typography.headlineSmall, singleLine = true)
                }
                SectionHeader("Gün ve saat", Modifier.padding(start = 4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    days.forEachIndexed { i, label ->
                        val active = weekday == i + 1
                        Surface(onClick = { weekday = i + 1 }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Text(label, style = MaterialTheme.typography.labelLarge, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeTile("Başlangıç", clockText(start), Modifier.weight(1f)) { settingTime = 0 }
                    TimeTile("Bitiş", clockText(end), Modifier.weight(1f)) { settingTime = 1 }
                }
                if (end <= start) Text("Bitiş saati başlangıçtan sonra olmalı.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                SettingsGroup(title = "Ayrıntılar") {
                    TextField(location, { location = it }, Modifier.fillMaxWidth(), placeholder = { Text("Derslik / yer") }, leadingIcon = { Icon(Icons.Outlined.Place, null) }, singleLine = true, colors = softFieldColors())
                    GroupDivider(16.dp)
                    TextField(instructor, { instructor = it }, Modifier.fillMaxWidth(), placeholder = { Text("Öğretim görevlisi") }, leadingIcon = { Icon(Icons.Outlined.Person, null) }, singleLine = true, colors = softFieldColors())
                }
                SectionHeader("Hatırlatma", Modifier.padding(start = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    courseReminderOptions.forEach { (minutes, label) ->
                        val on = if (minutes < 0) reminderOffsets.isEmpty() else minutes in reminderOffsets
                        FilterChip(selected = on, onClick = { reminderOffsets = if (minutes < 0) emptyList() else if (on) reminderOffsets - minutes else (reminderOffsets + minutes).sortedDescending() }, label = { Text(label) }, shape = RoundedCornerShape(10.dp),
                            leadingIcon = if (on) ({ Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }) else null)
                    }
                }
                Text(if (reminderOffsets.isEmpty()) "Bu ders için bildirim gönderilmez." else "Birden fazla seçebilirsin. Her hafta ${fullDays[weekday - 1]} günü: ${courseReminderSummary(reminderOffsets).replaceFirstChar { it.lowercase() }}.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                if (reminderOffsets.isNotEmpty()) ReminderPermissionHint()
                SectionHeader("Renk", Modifier.padding(start = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CourseColors.forEachIndexed { i, c ->
                        Box(Modifier.size(42.dp).clip(CircleShape).background(c).clickable { color = i }, contentAlignment = Alignment.Center) {
                            if (color == i) Icon(Icons.Rounded.Check, null, tint = Color.White)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, icon = { Icon(Icons.Rounded.DeleteOutline, null) }, title = { Text("Ders silinsin mi?") }, text = { Text("${existing?.title} programdan kaldırılacak.") },
        confirmButton = { TextButton({ existing?.let(onDelete); confirmDelete = false }) { Text("Sil", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton({ confirmDelete = false }) { Text("Vazgeç") } })
}

@Composable private fun TimeTile(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    AppCard(modifier, onClick = onClick) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.Schedule, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
