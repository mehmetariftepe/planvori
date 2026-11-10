package com.mehmetarif.sadegorev

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mehmetarif.sadegorev.data.TaskEntity
import com.mehmetarif.sadegorev.reminder.*
import java.time.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TaskEditorScreen(existing: TaskEntity?, onBack: () -> Unit, onSave: (TaskEntity) -> Unit, onDelete: ((TaskEntity) -> Unit)? = null) {
    var title by rememberSaveable(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var desc by rememberSaveable(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
    var due by rememberSaveable(existing?.id) { mutableStateOf(existing?.dueAt) }
    var reminder by rememberSaveable(existing?.id) { mutableStateOf(existing?.reminderAt) }
    var reminderChoice by rememberSaveable(existing?.id) { mutableIntStateOf(initialReminderChoice(existing != null, existing?.dueAt, existing?.reminderAt)) }
    var priority by rememberSaveable(existing?.id) { mutableIntStateOf(existing?.priority ?: 1) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var custom by rememberSaveable { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun leave() { if (page != 0) page = 0 else if (title != existing?.title.orEmpty() || desc != existing?.description.orEmpty() || due != existing?.dueAt || reminder != existing?.reminderAt || priority != (existing?.priority ?: 1)) discard = true else onBack() }
    BackHandler { leave() }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val source = if (custom) reminder ?: due else due
    val date = key(custom, page) { rememberDatePickerState(initialSelectedDateMillis = source?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }) }
    val local = source?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() } ?: LocalTime.of(18, 0)
    val time = key(custom, page) { rememberTimePickerState(local.hour, local.minute, true) }
    /** Tarihi hızlı seçer; mevcut saat korunur. */
    fun quickDate(d: LocalDate) {
        val t = due?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() } ?: LocalTime.of(18, 0)
        val picked = d.atTime(t).atZone(zone).toInstant().toEpochMilli()
        if (reminderChoice == CUSTOM_REMINDER && reminder == null) reminderChoice = 0
        due = picked; reminder = reminderTime(reminderChoice, picked, reminder)
    }
    val dueDate = due?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(when (page) { 1 -> if (custom) "Hatırlatma zamanı" else "Tarih ve saat"; 2 -> "Hatırlatma"; else -> if (existing == null) "Yeni görev" else "Görevi düzenle" }, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton({ leave() }) { Icon(if (page == 0) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack, "Geri") } },
                actions = { if (page == 0 && existing != null && onDelete != null) IconButton({ confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, "Görevi sil", tint = MaterialTheme.colorScheme.error) } })
        },
        bottomBar = {
            if (page == 0) Button(enabled = title.isNotBlank(), onClick = { onSave((existing ?: TaskEntity(title = title)).copy(title = title.trim(), description = desc.trim(), dueAt = due, reminderAt = reminderTime(reminderChoice, due, reminder), priority = priority)) },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp).height(56.dp), shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Rounded.Check, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                Text(if (existing == null) "Görevi oluştur" else "Değişiklikleri kaydet", style = MaterialTheme.typography.titleMedium)
            }
        }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            AnimatedContent(page, transitionSpec = { (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { if (targetState > initialState) it / 7 else -it / 7 }) togetherWith (fadeOut(tween(130)) + slideOutHorizontally(tween(200)) { if (targetState > initialState) -it / 7 else it / 7 }) }, label = "editor-page") { activePage ->
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when (activePage) {
                        0 -> {
                            AppCard(Modifier.fillMaxWidth()) {
                                BareTextField(title, { title = it }, "Ne yapılacak?", style = MaterialTheme.typography.headlineSmall)
                                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                BareTextField(desc, { desc = it }, "Not ekle", minLines = 2, style = MaterialTheme.typography.bodyLarge)
                            }
                            SettingsGroup(title = "Planlama") {
                                SettingsRow(Icons.Rounded.Event, "Tarih ve saat", tint = Color(0xFF3E8BFF), value = due?.let { friendlyDateTime(it) } ?: "Ekle",
                                    onClick = { custom = false; page = 1 },
                                    trailing = if (due != null) ({ IconButton({ due = null; reminder = null }, Modifier.size(32.dp)) { Icon(Icons.Rounded.Close, "Tarihi kaldır", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) } }) else null)
                                Row(Modifier.padding(start = 66.dp, end = 14.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("Bugün" to today, "Yarın" to today.plusDays(1), "Haftaya" to today.plusWeeks(1)).forEach { (label, d) ->
                                        FilterChip(selected = dueDate == d, onClick = { quickDate(d) }, label = { Text(label) }, shape = RoundedCornerShape(10.dp))
                                    }
                                }
                                GroupDivider()
                                SettingsRow(Icons.Rounded.NotificationsActive, "Hatırlatma", tint = Color(0xFFF08A24),
                                    value = if (due == null) "Önce tarih seç" else reminderLabel(reminderChoice, reminder),
                                    onClick = if (due != null) ({ page = 2 }) else null)
                            }
                            if (due == null) Text("Tarih eklediğinde görev saatinde otomatik hatırlatılır.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                            if (due != null && reminderChoice != NO_REMINDER) ReminderPermissionHint()
                            reminderTime(reminderChoice, due, reminder)?.let { if (it <= System.currentTimeMillis()) Text("Seçilen hatırlatma zamanı geçmişte. Geçmiş zamanlar için bildirim gönderilmez.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                            SectionHeader("Öncelik", Modifier.padding(start = 4.dp))
                            SegmentedOptions(listOf("Düşük", "Orta", "Yüksek"), priority, { priority = it }, colors = listOf(priorityColor(0), priorityColor(1), priorityColor(2)))
                            Spacer(Modifier.height(12.dp))
                        }
                        1 -> {
                            AppCard(Modifier.fillMaxWidth()) {
                                DatePicker(date, title = null, headline = null, showModeToggle = false, colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
                            }
                            AppCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Saat", style = MaterialTheme.typography.titleSmall, modifier = Modifier.align(Alignment.Start).padding(bottom = 10.dp))
                                    TimeInput(time)
                                }
                            }
                            Button(enabled = date.selectedDateMillis != null, onClick = {
                                val d = Instant.ofEpochMilli(date.selectedDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                                val picked = d.atTime(time.hour, time.minute).atZone(zone).toInstant().toEpochMilli()
                                if (custom) { reminderChoice = CUSTOM_REMINDER; reminder = picked } else { if (reminderChoice == CUSTOM_REMINDER && reminder == null) reminderChoice = 0; due = picked; reminder = reminderTime(reminderChoice, picked, reminder) }
                                page = 0
                            }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) { Text("Uygula", style = MaterialTheme.typography.titleMedium) }
                        }
                        2 -> {
                            Text("Ne zaman hatırlatalım?", style = MaterialTheme.typography.headlineSmall)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                reminderOptions.forEach { (choice, label) -> ReminderOption(label, reminderChoice == choice) { reminderChoice = choice; reminder = reminderTime(choice, due, reminder) } }
                                ReminderOption(reminderLabel(CUSTOM_REMINDER, if (reminderChoice == CUSTOM_REMINDER) reminder else null), reminderChoice == CUSTOM_REMINDER) { custom = true; page = 1 }
                            }
                            reminderTime(reminderChoice, due, reminder)?.let { if (it <= System.currentTimeMillis()) Text("Seçilen hatırlatma zamanı geçmişte. Geçmiş zamanlar için bildirim gönderilmez.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                            Button({ page = 0 }, Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) { Text("Tamam", style = MaterialTheme.typography.titleMedium) }
                        }
                    }
                }
            }
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Değişiklikler kaydedilmedi") }, text = { Text("Bu ekrandan çıkarsan yaptığın değişiklikler kaybolacak.") },
        confirmButton = { TextButton(onBack) { Text("Kaydetmeden çık", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton({ discard = false }) { Text("Düzenlemeye devam et") } })
    if (confirmDelete && existing != null && onDelete != null) AlertDialog(onDismissRequest = { confirmDelete = false }, icon = { Icon(Icons.Rounded.DeleteOutline, null) },
        title = { Text("Görev silinsin mi?") }, text = { Text("“${existing.title}” silinecek. Hemen ardından Geri al ile kurtarabilirsin.") },
        confirmButton = { TextButton({ confirmDelete = false; onDelete(existing) }) { Text("Sil", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton({ confirmDelete = false }) { Text("Vazgeç") } })
}

@Composable private fun ReminderOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(10.dp))
            Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f), color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}
