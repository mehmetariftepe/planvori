package com.mehmetarif.sadegorev

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.mehmetarif.sadegorev.drive.DriveBackupActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BackupScreen(onBack:()->Unit) {
    val context=LocalContext.current; val scope=rememberCoroutineScope()
    var busy by remember{mutableStateOf(false)}; var status by remember{mutableStateOf("")}
    var exported by remember{mutableStateOf<File?>(null)}; var pending by remember{mutableStateOf<BackupSnapshot?>(null)}
    BackHandler { if(pending!=null) pending=null else onBack() }
    fun export(action:(File)->Unit) { busy=true; scope.launch { try { val f=BackupStore.create(context);exported=f;action(f) }catch(e:Exception){status=e.message?:"Yedek oluşturulamadı."}finally{busy=false} } }
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){uri->if(uri!=null){busy=true;scope.launch{try{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use{out->requireNotNull(exported).inputStream().use{it.copyTo(out)}}?:error("Dosya açılamadı.")};status="Yedek kaydedildi."}catch(e:Exception){status=e.message?:"Kayıt başarısız."}finally{busy=false}}}}
    val load=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){busy=true;scope.launch{try{pending=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)?.use{BackupStore.read(it)}?:error("Dosya açılamadı.")}}catch(e:Exception){status=e.message?:"Yedek okunamadı."}finally{busy=false}}}}
    Scaffold(topBar={TopAppBar(title={Text("Veri yedekleme")},navigationIcon={IconButton(onBack){Icon(Icons.Default.ArrowBack,"Geri")}})}){padding->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
            Text("Verilerin seninle kalsın",style=MaterialTheme.typography.headlineSmall)
            Text("Görevler, ders programı, hatırlatmalar, tema ve widget ayarları tek ZIP dosyasına alınır.",color=MaterialTheme.colorScheme.onSurfaceVariant)
            Button(enabled=!busy,onClick={context.startActivity(Intent(context,DriveBackupActivity::class.java))},modifier=Modifier.fillMaxWidth().height(56.dp)){Text("Google Drive’a yedekle / geri yükle")}
            Button(enabled=!busy,onClick={export{save.launch(it.name)}},modifier=Modifier.fillMaxWidth().height(56.dp)){Text("ZIP’i telefona kaydet")}
            FilledTonalButton(enabled=!busy,onClick={export{BackupStore.share(context,it)}},modifier=Modifier.fillMaxWidth().height(56.dp)){Text("ZIP’i paylaş / WhatsApp’a gönder")}
            FilledTonalButton(enabled=!busy,onClick={load.launch(arrayOf("application/zip","application/octet-stream","application/x-zip-compressed"))},modifier=Modifier.fillMaxWidth().height(56.dp)){Text("ZIP’ten geri yükle")}
            val safety=File(context.filesDir,"Tasklist-son-geri-yukleme-oncesi.zip")
            if(safety.exists()) TextButton(enabled=!busy,onClick={val copy=File(context.cacheDir,"backups/Tasklist-guvenlik-yedegi.zip");copy.parentFile?.mkdirs();safety.copyTo(copy,true);BackupStore.share(context,copy)}){Text("Son geri yükleme öncesi yedeği paylaş")}
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(status.isNotEmpty())Text(status)
            Text("ZIP şifrelenmez; kişisel bilgilerin bulunabilir. Güvendiğin bir yerde sakla. Sistem bildirim izinleri yedeklenmez.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    pending?.let{snapshot->AlertDialog(onDismissRequest={pending=null},title={Text("Yedeği geri yükle?")},text={Text("${snapshot.tasks.size} görev ve ${snapshot.courses.size} ders geri yüklenecek. Mevcut kayıtların yerini alır. Önce mevcut verilerin güvenlik yedeği oluşturulur.")},confirmButton={TextButton(onClick={pending=null;busy=true;scope.launch{try{BackupStore.restore(context,snapshot);(context as? Activity)?.recreate()}catch(e:Exception){status=e.message?:"Geri yükleme başarısız."}finally{busy=false}}}){Text("Geri yükle")}},dismissButton={TextButton({pending=null}){Text("Vazgeç")}})}
}
