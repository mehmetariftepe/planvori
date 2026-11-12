package com.mehmetarif.sadegorev

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.mehmetarif.sadegorev.data.*
import com.mehmetarif.sadegorev.reminder.AlarmScheduler
import com.mehmetarif.sadegorev.reminder.CourseAlarmScheduler
import com.mehmetarif.sadegorev.widget.refreshWidgets
import com.mehmetarif.sadegorev.widget.refreshScheduleWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.zip.*

data class BackupSnapshot(val tasks: List<TaskEntity>, val settings: JSONObject, val courses: List<CourseEntity> = emptyList())

object BackupStore {
    private const val LIMIT = 16 * 1024 * 1024
    private val keys = setOf(AppPreferences.APP_THEME, AppPreferences.APP_ACCENT, AppPreferences.HOME_FILTER, AppPreferences.HOME_SORT,
        AppPreferences.HOME_FILTER_ORDER, AppPreferences.WIDGET_FILTER, AppPreferences.WIDGET_SORT,
        AppPreferences.WIDGET_THEME, AppPreferences.WIDGET_COUNT, AppPreferences.WIDGET_FILTER_ORDER,
        AppPreferences.SCHEDULE_WIDGET_FILTER, AppPreferences.SCHEDULE_WIDGET_COUNT,
        AppPreferences.WIDGET_PRIORITY,AppPreferences.SCHEDULE_WIDGET_DAY)

    suspend fun create(context: Context): File = withContext(Dispatchers.IO) {
        val db = TaskDatabase.get(context)
        val (tasks,courses) = db.withTransaction { db.taskDao().getAll() to db.courseDao().getAll() }
        val settings = JSONObject()
        AppPreferences.prefs(context).all.filterKeys { it in keys }.forEach { (k,v) -> settings.put(k,v) }
        val rows = JSONArray()
        tasks.forEach { t -> rows.put(JSONObject().put("id",t.id).put("title",t.title)
            .put("description",t.description).put("dueAt",t.dueAt ?: JSONObject.NULL)
            .put("priority",t.priority).put("reminderAt",t.reminderAt ?: JSONObject.NULL)
            .put("completed",t.completed).put("createdAt",t.createdAt).put("completedAt",t.completedAt ?: JSONObject.NULL).put("reminders",t.reminders)) }
        val classes = JSONArray()
        courses.forEach { c -> classes.put(JSONObject().put("id",c.id).put("title",c.title)
            .put("weekday",c.weekday).put("startMinute",c.startMinute).put("endMinute",c.endMinute)
            .put("location",c.location).put("instructor",c.instructor).put("color",c.color).put("reminderMinutes",c.reminderMinutes).put("reminders",c.reminders)) }
        val json = JSONObject().put("format","tasklist-backup").put("version",2)
            .put("exportedAt",System.currentTimeMillis()).put("tasks",rows).put("courses",classes).put("settings",settings)
        val dir = File(context.cacheDir,"backups").apply { mkdirs() }
        val file = File(dir,"Planvori-yedek-${System.currentTimeMillis()}.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("tasklist.json")); zip.write(json.toString().toByteArray(Charsets.UTF_8)); zip.closeEntry()
        }
        file
    }

    fun read(input: InputStream): BackupSnapshot {
        var payload: ByteArray? = null
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                require(entry.name == "tasklist.json" && payload == null) { "Geçerli bir Planvori veya Tasklist ZIP yedeği seçin." }
                val bytes = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                var n = zip.read(buffer)
                while(n != -1) { require(bytes.size()+n <= LIMIT) { "Yedek çok büyük (en fazla 16 MB)." }; bytes.write(buffer,0,n); n=zip.read(buffer) }
                payload = bytes.toByteArray(); zip.closeEntry(); entry=zip.nextEntry
            }
        }
        val json = JSONObject(String(requireNotNull(payload) { "ZIP boş." },Charsets.UTF_8))
        val version=json.getInt("version")
        require(json.getString("format")=="tasklist-backup" && version in 1..2) { "Yedek biçimi desteklenmiyor." }
        val rows = json.getJSONArray("tasks"); require(rows.length()<=50000)
        fun nullable(row: JSONObject,key: String) = if(row.isNull(key)) null else row.getLong(key)
        val tasks = (0 until rows.length()).map { i ->
            val row=rows.getJSONObject(i); val title=row.getString("title"); val id=row.getLong("id"); val priority=row.getInt("priority")
            require(id>0 && title.isNotBlank() && title.length<=10000 && priority in 0..2) { "Yedekte geçersiz görev var." }
            TaskEntity(id,title,row.getString("description"),nullable(row,"dueAt"),priority,nullable(row,"reminderAt"),row.getBoolean("completed"),row.getLong("createdAt"),
                if(row.has("completedAt")) nullable(row,"completedAt") else null,
                row.optString("reminders","").also { r -> require(r.length<=200 && (r.isEmpty() || r.split(",").all{it.toLongOrNull()!=null})) { "Yedekte geçersiz hatırlatma var." } })
        }
        require(tasks.map{it.id}.distinct().size==tasks.size) { "Yedekte yinelenen görev var." }
        val classRows = if(version>=2) json.getJSONArray("courses") else JSONArray()
        require(classRows.length()<=10000) { "Ders listesi çok büyük." }
        val courses = (0 until classRows.length()).map { i ->
            val row=classRows.getJSONObject(i)
            CourseEntity(row.getLong("id"),row.getString("title"),row.getInt("weekday"),
                row.getInt("startMinute"),row.getInt("endMinute"),row.getString("location"),
                row.getString("instructor"),row.getInt("color"),row.optInt("reminderMinutes",-1),row.optString("reminders","")).also { c ->
                require(c.id>0 && c.title.isNotBlank() && c.title.length<=10000 && c.weekday in 1..7 &&
                    c.startMinute in 0..1439 && c.endMinute in 1..1440 && c.endMinute>c.startMinute && c.color in 0..4 &&
                    c.reminderMinutes in -1..120 && c.reminders.length<=60 &&
                    (c.reminders.isEmpty() || c.reminders.split(",").all{(it.toIntOrNull()?:-1) in 0..120})) { "Yedekte geçersiz ders var." }
            }
        }
        require(courses.map{it.id}.distinct().size==courses.size) { "Yedekte yinelenen ders var." }
        val settings=json.getJSONObject("settings")
        settings.keys().forEach { k ->
            require(k in keys)
            if(k.endsWith("order")) require(settings.getString(k).split(",").map{it.toInt()}.sorted()==listOf(0,1,2,3))
            else { val v=settings.getInt(k); require(when(k){AppPreferences.WIDGET_PRIORITY->v in -1..2;AppPreferences.SCHEDULE_WIDGET_DAY->v in 0..7;AppPreferences.APP_THEME->v in 0..3;AppPreferences.APP_ACCENT->v in 0..4;AppPreferences.WIDGET_COUNT->v in listOf(3,5,8);AppPreferences.SCHEDULE_WIDGET_COUNT->v in listOf(0,3,5);AppPreferences.HOME_FILTER,AppPreferences.WIDGET_FILTER->v in 0..3;AppPreferences.SCHEDULE_WIDGET_FILTER->v in 0..1;else->v in 0..2}) }
        }
        return BackupSnapshot(tasks,settings,courses)
    }

    suspend fun restore(context: Context,snapshot: BackupSnapshot) = withContext(Dispatchers.IO) {
        val safety=create(context)
        safety.copyTo(File(context.filesDir,"Tasklist-son-geri-yukleme-oncesi.zip"),overwrite=true)
        val db=TaskDatabase.get(context); val old=db.taskDao().getAll(); val oldCourses=db.courseDao().getAll()
        db.withTransaction {
            db.taskDao().clearAll(); db.taskDao().insertAll(snapshot.tasks)
            db.courseDao().clearAll(); db.courseDao().insertAll(snapshot.courses)
        }
        val edit=AppPreferences.prefs(context).edit()
        keys.forEach { edit.remove(it) }
        snapshot.settings.keys().forEach { k -> if(k.endsWith("order")) edit.putString(k,snapshot.settings.getString(k)) else edit.putInt(k,snapshot.settings.getInt(k)) }
        check(edit.commit()) { "Ayarlar kaydedilemedi." }
        old.forEach { AlarmScheduler.cancel(context,it.id) }
        snapshot.tasks.forEach { AlarmScheduler.schedule(context,it) }
        oldCourses.forEach { CourseAlarmScheduler.cancel(context,it.id) }
        snapshot.courses.forEach { CourseAlarmScheduler.schedule(context,it) }
        refreshWidgets(context)
        refreshScheduleWidgets(context)
    }

    fun share(context: Context,file: File) {
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type="application/zip"; putExtra(Intent.EXTRA_STREAM,uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },"Yedeği paylaş"))
    }
}
