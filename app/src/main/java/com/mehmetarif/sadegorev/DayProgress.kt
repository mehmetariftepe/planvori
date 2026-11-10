package com.mehmetarif.sadegorev

import com.mehmetarif.sadegorev.data.TaskEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Görevler ekranındaki "Bugünkü ilerleme" kartının sayıları. */
internal data class DayProgress(val done: Int, val total: Int, val overdue: Int, val upcoming: Int)

/**
 * Bugünün planı = bugün yapılması gereken açık görevler (bugüne tarihli, tarihi geçmiş veya tarihsiz)
 * + bugün tamamlanan görevler. Böylece bir görevi tamamlamak toplamı düşürmez, tamamlananı artırır.
 * Tamamlanma anı bilinmeyen eski görevler, tarihleri bugünse bugün tamamlanmış sayılır.
 */
internal fun dayProgress(all: List<TaskEntity>, now: Long, zone: ZoneId = ZoneId.systemDefault()): DayProgress {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val open = all.filter { !it.completed }
    val dueForToday = open.count { t -> t.dueAt == null || !dateOf(t.dueAt).isAfter(today) }
    val doneToday = all.count { t -> t.completed && (t.completedAt?.let(::dateOf) ?: t.dueAt?.let(::dateOf)) == today }
    return DayProgress(
        done = doneToday,
        total = dueForToday + doneToday,
        overdue = open.count { (it.dueAt ?: Long.MAX_VALUE) < now },
        upcoming = open.count { t -> t.dueAt?.let { dateOf(it).isAfter(today) } == true },
    )
}
