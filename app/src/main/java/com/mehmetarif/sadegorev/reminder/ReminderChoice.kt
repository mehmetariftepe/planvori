package com.mehmetarif.sadegorev.reminder

import com.mehmetarif.sadegorev.data.CourseEntity
import com.mehmetarif.sadegorev.data.TaskEntity
import com.mehmetarif.sadegorev.friendlyDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal const val NO_REMINDER = -1
/** Bir görev veya ders için en fazla hatırlatma sayısı. */
internal const val MAX_REMINDERS = 5

/** Görev saatine göre hatırlatma seçenekleri: dakika → etiket. */
internal val reminderOptions = listOf(0 to "Görev saatinde", 5 to "5 dakika önce", 15 to "15 dakika önce", 30 to "30 dakika önce", 60 to "1 saat önce", 1440 to "1 gün önce")

/**
 * Görevin hatırlatma seçimi. [offsets] görev saatine bağlıdır ve tarih değişince onu izler;
 * [custom] mutlak tarih/saatlerdir ve yerinde kalır.
 */
internal data class ReminderSelection(val offsets: List<Int> = emptyList(), val custom: List<Long> = emptyList()) {
    val count get() = offsets.size + custom.size
}

internal fun parseReminderTimes(text: String): List<Long> = text.split(",").mapNotNull { it.trim().toLongOrNull() }.distinct().sorted()
internal fun formatReminderTimes(times: List<Long>): String = times.distinct().sorted().joinToString(",")

/** Görevin tüm hatırlatma zamanları; eski kayıtlarda yalnızca [TaskEntity.reminderAt] bulunur. */
internal fun TaskEntity.reminderTimes(): List<Long> = parseReminderTimes(reminders).ifEmpty { listOfNotNull(reminderAt) }

/** Yeni görevler görev saatinde hatırlatır; kayıtlı görevlerde kapatılmış hatırlatma kapalı kalır. */
internal fun initialReminderSelection(existing: Boolean, due: Long?, reminders: List<Long>): ReminderSelection {
    if (!existing || due == null) return ReminderSelection(offsets = listOf(0))
    val offsets = mutableListOf<Int>(); val custom = mutableListOf<Long>()
    reminders.forEach { time ->
        val option = reminderOptions.firstOrNull { it.first * 60000L == due - time }?.first
        if (option != null) offsets += option else custom += time
    }
    return ReminderSelection(offsets.distinct().sorted(), custom.distinct().sorted())
}

/** Seçimin mutlak zamanları (sıralı). Tarihsiz görevde hatırlatma olmaz. */
internal fun reminderTimes(selection: ReminderSelection, due: Long?): List<Long> {
    if (due == null) return emptyList()
    return (selection.offsets.map { due - it * 60000L } + selection.custom).distinct().sorted()
}

internal fun customReminderLabel(time: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(time).atZone(zone).format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.forLanguageTag("tr")))

/** Düzenleme ekranındaki kısa özet. */
internal fun reminderSummary(selection: ReminderSelection, zone: ZoneId = ZoneId.systemDefault()): String = when {
    selection.count == 0 -> "Hatırlatma yok"
    selection.count > 1 -> "${selection.count} hatırlatma"
    selection.offsets.isNotEmpty() -> reminderOptions.first { it.first == selection.offsets[0] }.second
    else -> "Özel · " + customReminderLabel(selection.custom[0], zone)
}

/** Görev bildirimi metni, ör. "15 dk sonra · Bugün · 17:00". */
internal fun taskNotificationText(due: Long?, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    if (due == null) return "Görev zamanı"
    val minutes = Math.round((due - now) / 60000.0)
    val whenText = when {
        minutes < -1 -> "Zamanı geçti"
        minutes <= 0 -> "Şimdi zamanı"
        minutes < 60 -> "$minutes dk sonra"
        minutes < 1440 -> (if (minutes % 60 == 0L) "${minutes / 60} saat" else "${minutes / 60} sa ${minutes % 60} dk") + " sonra"
        else -> "${minutes / 1440} gün sonra"
    }
    return "$whenText · " + friendlyDateTime(due, zone, Instant.ofEpochMilli(now).atZone(zone).toLocalDate())
}

/* ───────── Dersler ───────── */

internal fun parseCourseReminders(text: String): List<Int> = text.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it >= 0 }.distinct().sortedDescending()
internal fun formatCourseReminders(minutes: List<Int>): String = minutes.filter { it >= 0 }.distinct().sortedDescending().joinToString(",")

/** Dersin tüm hatırlatmaları (dakika önce, erkenden geçe); eski kayıtlarda yalnızca [CourseEntity.reminderMinutes] bulunur. */
internal fun CourseEntity.reminderOffsets(): List<Int> = parseCourseReminders(reminders).ifEmpty { if (reminderMinutes >= 0) listOf(reminderMinutes) else emptyList() }
