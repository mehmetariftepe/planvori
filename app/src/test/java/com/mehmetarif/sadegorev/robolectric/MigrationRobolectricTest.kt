package com.mehmetarif.sadegorev.robolectric

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mehmetarif.sadegorev.data.TaskDatabase
import com.mehmetarif.sadegorev.reminder.reminderOffsets
import com.mehmetarif.sadegorev.reminder.reminderTimes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Eski sürümlerin veritabanı güncel şemaya veri kaybetmeden taşınıyor mu? (Robolectric; -ProbolectricDir ile çalışır) */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MigrationRobolectricTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val tasksV1 = "CREATE TABLE `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `dueAt` INTEGER, `priority` INTEGER NOT NULL, `reminderAt` INTEGER, `completed` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
    private val coursesV2 = "CREATE TABLE `courses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `weekday` INTEGER NOT NULL, `startMinute` INTEGER NOT NULL, `endMinute` INTEGER NOT NULL, `location` TEXT NOT NULL, `instructor` TEXT NOT NULL, `color` INTEGER NOT NULL)"

    private fun open(version: Int, setup: SQLiteDatabase.() -> Unit) {
        TaskDatabase::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
        val file = app.getDatabasePath("sade-gorev.db").apply { parentFile!!.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(tasksV1)
            db.execSQL("CREATE INDEX `index_tasks_dueAt` ON `tasks` (`dueAt`)"); db.execSQL("CREATE INDEX `index_tasks_completed` ON `tasks` (`completed`)")
            db.execSQL("INSERT INTO tasks VALUES (1,'Eski görev','not',1000,2,900,0,5)")
            db.setup(); db.version = version
        }
    }

    @Test fun migratesFromVersion1() = runBlocking {
        open(1) {}
        val db = TaskDatabase.get(app)
        val task = db.taskDao().getAll().single()
        assertEquals("Eski görev", task.title); assertEquals(900L, task.reminderAt); assertNull(task.completedAt)
        assertEquals(listOf(900L), task.reminderTimes())
        assertTrue(db.courseDao().getAll().isEmpty())
        db.close()
    }

    @Test fun migratesFromVersion2WithCourses() = runBlocking {
        open(2) {
            execSQL(coursesV2); execSQL("CREATE INDEX `index_courses_weekday` ON `courses` (`weekday`)")
            execSQL("INSERT INTO courses VALUES (1,'Matematik',2,540,600,'B12','',1)")
        }
        val db = TaskDatabase.get(app)
        val course = db.courseDao().getAll().single()
        assertEquals("Matematik", course.title); assertEquals(-1, course.reminderMinutes); assertTrue(course.reminderOffsets().isEmpty())
        assertEquals(1, db.taskDao().getAll().size)
        db.close()
    }

    @Test fun migratesFromVersion4KeepingReminders() = runBlocking {
        open(4) {
            execSQL("ALTER TABLE tasks ADD COLUMN completedAt INTEGER")
            execSQL(coursesV2.replace("`color` INTEGER NOT NULL)", "`color` INTEGER NOT NULL, `reminderMinutes` INTEGER NOT NULL DEFAULT -1)")); execSQL("CREATE INDEX `index_courses_weekday` ON `courses` (`weekday`)")
            execSQL("INSERT INTO courses VALUES (1,'Fizik',3,600,660,'','',0,15)")
        }
        val db = TaskDatabase.get(app)
        assertEquals(listOf(15), db.courseDao().getAll().single().reminderOffsets())
        assertEquals(listOf(900L), db.taskDao().getAll().single().reminderTimes())
        db.close()
    }
}
