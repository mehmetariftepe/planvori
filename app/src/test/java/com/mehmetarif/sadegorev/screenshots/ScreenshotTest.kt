package com.mehmetarif.sadegorev.screenshots

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mehmetarif.sadegorev.AppPreferences
import com.mehmetarif.sadegorev.SadeTheme
import com.mehmetarif.sadegorev.TaskApp
import com.mehmetarif.sadegorev.TaskViewModel
import com.mehmetarif.sadegorev.data.CourseEntity
import com.mehmetarif.sadegorev.data.TaskDatabase
import com.mehmetarif.sadegorev.data.TaskEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Uygulama ekranlarının PNG görüntülerini üretir (cihaz gerekmez).
 * Çalıştırma: ./gradlew testDebugUnitTest -Pscreenshots=1 --tests '*ScreenshotTest*'
 * Çıktı: build/screenshots/
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val prefix = System.getProperty("screenshotPrefix") ?: ""

    @Before fun seed() {
        assumeTrue(System.getProperty("screenshots").orEmpty().isNotEmpty())
        val zone = ZoneId.systemDefault()
        fun at(daysFromToday: Long, hour: Int, minute: Int = 0) =
            LocalDate.now(zone).plusDays(daysFromToday).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        // Her testte yeni Application geldiği için önbellekteki veritabanını sıfırla
        TaskDatabase::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
        val db = TaskDatabase.get(app)
        runBlocking {
            db.taskDao().clearAll(); db.courseDao().clearAll()
            db.taskDao().insertAll(listOf(
                TaskEntity(1, "Fizik ödevini teslim et", "3. bölüm soruları", at(0, 17), 2, at(0, 16, 30)),
                TaskEntity(2, "Markete uğra", "Süt, ekmek, meyve", at(0, 19), 1, at(0, 19)),
                TaskEntity(3, "Kitap okumak · 30 sayfa", "", null, 0),
                TaskEntity(4, "Proje sunumunu hazırla", "Slaytlar + demo", at(1, 10), 2, at(1, 9)),
                TaskEntity(5, "Diş randevusu", "", at(3, 14, 30), 1, at(3, 14)),
                TaskEntity(6, "Faturayı öde", "", at(-1, 12), 2, null),
                TaskEntity(7, "Spor salonu", "", at(0, 8), 0, null, completed = true, completedAt = at(0, 8, 30)),
                TaskEntity(8, "E-postaları yanıtla", "", at(0, 9), 1, null, completed = true, completedAt = at(0, 9, 30)),
            ))
            val today = LocalDate.now(zone).dayOfWeek.value
            db.courseDao().insertAll(listOf(
                CourseEntity(1, "Matematik", today, 9 * 60, 10 * 60 + 30, "B-204", "Dr. Yılmaz", 0, 10),
                CourseEntity(2, "Fizik", today, 11 * 60, 12 * 60 + 30, "Lab 3", "Prof. Kaya", 1, -1),
                CourseEntity(3, "Algoritmalar", today, 14 * 60, 15 * 60 + 30, "A-101", "", 3, 15),
                CourseEntity(4, "İngilizce", (today % 7) + 1, 10 * 60, 11 * 60, "C-12", "", 4, -1),
            ))
        }
    }

    private fun theme(mode: Int) = AppPreferences.prefs(app).edit().putInt(AppPreferences.APP_THEME, mode).commit()

    private fun shoot(name: String) {
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        val dir = File(System.getProperty("screenshotDir")!!).apply { mkdirs() }
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(android.graphics.Canvas(bitmap)) }
        File(dir, "$prefix$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun render(mode: Int) {
        theme(mode)
        val vm = TaskViewModel(app)
        compose.setContent { SadeTheme { TaskApp(vm, null, false, false, false, 3, null) } }
        compose.waitForIdle()
    }

    private fun tab(label: String) { compose.onAllNodesWithText(label, useUnmergedTree = true).onLast().performClick() }

    @Test fun light() { render(1); shoot("1-gorevler-acik"); tab("Takvim"); shoot("2-takvim-acik"); tab("Dersler"); shoot("3-dersler-acik"); tab("Bitenler"); shoot("4-bitenler-acik"); tab("Ayarlar"); shoot("5-ayarlar-acik") }
    @Test fun dark() { render(0); shoot("6-gorevler-koyu"); tab("Takvim"); shoot("7-takvim-koyu"); tab("Dersler"); shoot("8-dersler-koyu") }
    /** İlerleme kartı: tarihsiz görevi tamamlayınca 0/1 → 1/1 olmalı. */
    @Test fun progressAfterCompleting() {
        val dao = TaskDatabase.get(app).taskDao()
        runBlocking { dao.clearAll(); dao.insertAll(listOf(TaskEntity(1, "Tarihsiz görev", "", null, 1))) }
        theme(1)
        val vm = TaskViewModel(app)
        compose.setContent { SadeTheme { TaskApp(vm, null, false, false, false, 0, null) } }
        shoot("12-ilerleme-once")
        compose.runOnUiThread { vm.toggle(runBlocking { dao.get(1)!! }) }
        compose.waitUntil(10_000) { runBlocking { dao.get(1)!!.completed } }
        shoot("13-ilerleme-sonra")
    }
    @Test fun multipleReminders() {
        theme(1)
        val zone = ZoneId.systemDefault()
        val due = LocalDate.now(zone).plusDays(1).atTime(17, 0).atZone(zone).toInstant().toEpochMilli()
        val task = TaskEntity(1, "Fizik ödevini teslim et", "", due, 2, due - 1440 * 60000L, reminders = "${due - 1440 * 60000L},${due - 60 * 60000L},$due")
        compose.setContent { SadeTheme { com.mehmetarif.sadegorev.TaskEditorScreen(task, {}, {}) } }
        shoot("14-gorev-hatirlatma-ozet")
        compose.onNodeWithText("Hatırlatma").performClick()
        shoot("15-gorev-coklu-hatirlatma")
    }
    @Test fun courseMultipleReminders() {
        theme(1)
        val course = CourseEntity(1, "Matematik", 1, 9 * 60, 10 * 60 + 30, "B-204", "Dr. Yılmaz", 0, 30, "30,10,0")
        compose.setContent { SadeTheme { com.mehmetarif.sadegorev.CourseEditorScreen(course, 1, {}, {}, {}) } }
        shoot("16-ders-coklu-hatirlatma")
    }
    @Test fun navy() { render(2); shoot("9-gorevler-gece") }
    @Test fun courseEditor() {
        theme(1)
        compose.setContent { SadeTheme { com.mehmetarif.sadegorev.CourseEditorScreen(null, 1, {}, {}, {}) } }
        shoot("11-yeni-ders-acik")
    }
    @Test fun editor() {
        theme(1)
        compose.setContent { SadeTheme { com.mehmetarif.sadegorev.TaskEditorScreen(null, {}, {}) } }
        shoot("10-yeni-gorev-acik")
    }
}
