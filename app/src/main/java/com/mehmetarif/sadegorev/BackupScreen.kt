package com.mehmetarif.sadegorev

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mehmetarif.sadegorev.drive.DriveBackupActivity
import kotlinx.coroutines.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }; var status by remember { mutableStateOf("") }
    var exported by remember { mutableStateOf<File?>(null) }; var pending by remember { mutableStateOf<BackupSnapshot?>(null) }
    BackHandler { if (pending != null) pending = null else onBack() }
    fun export(action: (File) -> Unit) { busy = true; scope.launch { try { val f = BackupStore.create(context); exported = f; action(f) } catch (e: Exception) { status = e.message ?: "Yedek oluşturulamadı." } finally { busy = false } } }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) { busy = true; scope.launch { try { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { out -> requireNotNull(exported).inputStream().use { it.copyTo(out) } } ?: error("Dosya açılamadı.") }; status = "Yedek kaydedildi." } catch (e: Exception) { status = e.message ?: "Kayıt başarısız." } finally { busy = false } } } }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { busy = true; scope.launch { try { pending = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { BackupStore.read(it) } ?: error("Dosya açılamadı.") } } catch (e: Exception) { status = e.message ?: "Yedek okunamadı." } finally { busy = false } } } }
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background), title = { Text("Yedekleme", style = MaterialTheme.typography.titleLarge) }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Geri") } }) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.Shield, MaterialTheme.colorScheme.primary, size = 48.dp, iconSize = 26.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Verilerin seninle kalsın", style = MaterialTheme.typography.titleMedium)
                    Text("Görevler, dersler, hatırlatmalar ve ayarlar tek dosyada.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SettingsGroup(title = "Bulut") {
                SettingsRow(Icons.Rounded.CloudUpload, "Google Drive", subtitle = "Yedekle, geri yükle, otomatik günlük yedek", tint = Color(0xFF1FA463), onClick = if (busy) null else ({ context.startActivity(Intent(context, DriveBackupActivity::class.java)) }))
            }
            SettingsGroup(title = "Dosya") {
                SettingsRow(Icons.Rounded.Download, "Telefona kaydet", subtitle = "ZIP dosyası olarak dışa aktar", tint = Color(0xFF3E8BFF), onClick = if (busy) null else ({ export { save.launch(it.name) } }))
                GroupDivider()
                SettingsRow(Icons.Rounded.Share, "Paylaş", subtitle = "WhatsApp, e-posta veya başka uygulama", tint = Color(0xFF9B6BFF), onClick = if (busy) null else ({ export { BackupStore.share(context, it) } }))
                GroupDivider()
                SettingsRow(Icons.Rounded.Restore, "ZIP'ten geri yükle", subtitle = "Mevcut kayıtların yerini alır", tint = Color(0xFFF08A24), onClick = if (busy) null else ({ load.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) }))
                val safety = File(context.filesDir, "Tasklist-son-geri-yukleme-oncesi.zip")
                if (safety.exists()) {
                    GroupDivider()
                    SettingsRow(Icons.Rounded.History, "Güvenlik yedeğini paylaş", subtitle = "Son geri yükleme öncesindeki veriler", tint = Color(0xFF7D8596), onClick = if (busy) null else ({ val copy = File(context.cacheDir, "backups/Tasklist-guvenlik-yedegi.zip"); copy.parentFile?.mkdirs(); safety.copyTo(copy, true); BackupStore.share(context, copy) }))
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(50)))
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 14.dp, start = 4.dp))
            Text("ZIP şifrelenmez; kişisel bilgiler içerebilir. Güvendiğin bir yerde sakla. Sistem bildirim izinleri yedeklenmez.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 24.dp))
        }
    }
    pending?.let { snapshot -> AlertDialog(onDismissRequest = { pending = null }, icon = { Icon(Icons.Rounded.Restore, null) }, title = { Text("Yedeği geri yükle?") }, text = { Text("${snapshot.tasks.size} görev ve ${snapshot.courses.size} ders geri yüklenecek. Mevcut kayıtların yerini alır. Önce mevcut verilerin güvenlik yedeği oluşturulur.") }, confirmButton = { TextButton(onClick = { pending = null; busy = true; scope.launch { try { BackupStore.restore(context, snapshot); (context as? Activity)?.recreate() } catch (e: Exception) { status = e.message ?: "Geri yükleme başarısız." } finally { busy = false } } }) { Text("Geri yükle") } }, dismissButton = { TextButton({ pending = null }) { Text("Vazgeç") } }) }
}
