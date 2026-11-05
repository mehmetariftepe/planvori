package com.mehmetarif.sadegorev.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tasks", indices = [Index("dueAt"), Index("completed")])
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val dueAt: Long? = null,
    val priority: Int = 1,
    val reminderAt: Long? = null,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "courses", indices = [Index("weekday")])
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val weekday: Int,
    val startMinute: Int,
    val endMinute: Int,
    val location: String = "",
    val instructor: String = "",
    val color: Int = 0
)

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY weekday, startMinute, title") fun observeAll(): Flow<List<CourseEntity>>
    @Query("SELECT * FROM courses ORDER BY weekday, startMinute, title") suspend fun getAll(): List<CourseEntity>
    @Insert suspend fun insert(course: CourseEntity): Long
    @Update suspend fun update(course: CourseEntity)
    @Delete suspend fun delete(course: CourseEntity)
    @Query("DELETE FROM courses") suspend fun clearAll()
    @Insert suspend fun insertAll(courses: List<CourseEntity>)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks") suspend fun getAll(): List<TaskEntity>
    @Query("DELETE FROM tasks") suspend fun clearAll()
    @Insert suspend fun insertAll(tasks: List<TaskEntity>)
    @Query("SELECT * FROM tasks ORDER BY completed ASC, CASE WHEN dueAt IS NULL THEN 1 ELSE 0 END, dueAt ASC, priority DESC")
    fun observeAll(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks WHERE id=:id") suspend fun get(id: Long): TaskEntity?
    @Query("SELECT * FROM tasks WHERE completed=0 AND reminderAt IS NOT NULL AND reminderAt>:now") suspend fun pendingReminders(now: Long): List<TaskEntity>
    @Insert suspend fun insert(task: TaskEntity): Long
    @Update suspend fun update(task: TaskEntity)
    @Delete suspend fun delete(task: TaskEntity)
}

@Database(entities = [TaskEntity::class, CourseEntity::class], version = 2, exportSchema = false)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun courseDao(): CourseDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `courses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `weekday` INTEGER NOT NULL, `startMinute` INTEGER NOT NULL, `endMinute` INTEGER NOT NULL, `location` TEXT NOT NULL, `instructor` TEXT NOT NULL, `color` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_courses_weekday` ON `courses` (`weekday`)")
            }
        }
        @Volatile private var instance: TaskDatabase? = null
        fun get(context: Context): TaskDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TaskDatabase::class.java, "sade-gorev.db").addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
