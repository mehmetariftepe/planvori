package com.mehmetarif.sadegorev.drive

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.mehmetarif.sadegorev.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class DriveBackupActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        setContent{SadeTheme{DriveScreen(this)}}
    }
}
private fun failureText(e:Exception):String=when(e){
    is ApiException->when(e.statusCode){10->"Google Cloud Android OAuth ayarı eksik veya paket/imza eşleşmiyor. Kurulum kılavuzunu kontrol et.";7->"İnternet bağlantını kontrol et.";16->"Google bağlantısı iptal edildi.";else->"Google bağlantısı kurulamadı (${e.statusCode})."}
    else->e.message?:"İşlem tamamlanamadı. Tekrar dene."
}
private fun dateLabel(value:String)=runCatching{Instant.parse(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm",Locale.forLanguageTag("tr")))}.getOrDefault(value)

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DriveScreen(activity:DriveBackupActivity){
    val scope=rememberCoroutineScope();val prefs=remember{DriveConnection.prefs(activity)}
    var revision by remember{mutableIntStateOf(0)}
    DisposableEffect(prefs){val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,_->revision++};prefs.registerOnSharedPreferenceChangeListener(listener);onDispose{prefs.unregisterOnSharedPreferenceChangeListener(listener)}}
    val email=remember(revision){DriveConnection.email(activity)}
    val automatic=remember(revision){DriveConnection.auto(activity)}
    val last=remember(revision){prefs.getLong("last",0)}
    var busy by remember{mutableStateOf(false)};var message by rememberSaveable{mutableStateOf("")}
    var operation by rememberSaveable{mutableStateOf("list")};var selectedId by rememberSaveable{mutableStateOf("")}
    var backups by remember{mutableStateOf(emptyList<DriveBackup>())};var pending by remember{mutableStateOf<BackupSnapshot?>(null)}
    suspend fun execute(token:String){
        when(operation){
            "connect"->{DriveBackupService.connect(activity,token);DriveBackupService.upload(activity,token);message="Drive bağlandı ve ilk yedek kaydedildi."}
            "upload"->{DriveBackupService.upload(activity,token);message="Yedek doğrudan Drive’a kaydedildi."}
            "restore"->{pending=DriveBackupService.download(activity,token,selectedId)}
        }
        backups=DriveBackupService.list(activity,token)
    }
    val resolution=rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()){result->
        if(result.resultCode!=Activity.RESULT_OK){busy=false;message="Google bağlantısı iptal edildi."}
        else scope.launch{busy=true;try{val auth=Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(result.data);execute(auth.accessToken?:throw DrivePermissionRequired())}catch(e:CancellationException){throw e}catch(e:Exception){message=failureText(e)}finally{busy=false}}
    }
    fun authorize(action:String){
        operation=action;busy=true;message=""
        scope.launch{try{
            val auth=Identity.getAuthorizationClient(activity).authorize(DriveConnection.request(activity,action=="connect")).awaitDrive()
            if(auth.hasResolution())resolution.launch(IntentSenderRequest.Builder(requireNotNull(auth.pendingIntent).intentSender).build())
            else {execute(auth.accessToken?:throw DrivePermissionRequired());busy=false}
        }catch(e:CancellationException){throw e}catch(e:Exception){message=failureText(e);busy=false}}
    }
    // Do not trigger consent on entry. Silent refresh is permitted only for an already linked account.
    LaunchedEffect(Unit){if(email!=null){busy=true;try{backups=DriveBackupService.list(activity,DriveConnection.silentToken(activity))}catch(e:CancellationException){throw e}catch(e:Exception){message=failureText(e)}finally{busy=false}}}
    Scaffold(topBar={TopAppBar(title={Text("Google Drive yedeği")},navigationIcon={IconButton(onClick={activity.finish()}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Geri")}})}){padding->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            Text("Verilerin hesabında",style=MaterialTheme.typography.headlineSmall)
            Text("Görevler, dersler, hatırlatmalar ve görünüm ayarları doğrudan Drive’daki Planvori’ye özel alana kaydedilir. Yedekler Drive’ın normal dosya listesinde görünmez; buradan geri yüklenir.",color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(email==null){
                Button(onClick={authorize("connect")},enabled=!busy,modifier=Modifier.fillMaxWidth().height(56.dp)){Text("Google hesabını bağla ve yedekle")}
                Text("İlk kullanımda Google Cloud OAuth kurulumu gerekir. Uygulamanın Play Store’da yayınlanması gerekmez.",style=MaterialTheme.typography.bodySmall)
            }else{
                Card(Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(email,style=MaterialTheme.typography.titleMedium)
                    Text(if(last>0)"Son yedek: ${dateLabel(Instant.ofEpochMilli(last).toString())}" else "Henüz doğrulanmış yedek yok.",style=MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth()){
                        Column(Modifier.weight(1f)){Text("Günlük otomatik yedek");Text("İnternet ve yeterli pil olduğunda",style=MaterialTheme.typography.bodySmall)}
                        Switch(checked=automatic,enabled=!busy,onCheckedChange={if(it)DriveBackupWorker.enable(activity) else DriveBackupWorker.disable(activity)})
                    }
                    if(prefs.getString("status","").orEmpty().isNotBlank())Text(prefs.getString("status","").orEmpty(),style=MaterialTheme.typography.bodySmall)
                }}
                Button(onClick={authorize("upload")},enabled=!busy,modifier=Modifier.fillMaxWidth().height(56.dp)){Text("Şimdi Drive’a yedekle")}
                Text("Drive’daki yedekler",style=MaterialTheme.typography.titleLarge)
                TextButton(onClick={authorize("list")},enabled=!busy){Text("Listeyi yenile")}
                if(backups.isEmpty()&&!busy)Text("Yedek listesi boş. Yedek al veya listeyi yenile.")
                backups.forEach{backup->OutlinedCard(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(dateLabel(backup.createdAt),style=MaterialTheme.typography.titleMedium)
                        Text(backup.name,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick={selectedId=backup.id;authorize("restore")},enabled=!busy){Text("Bu yedeği geri yükle")}
                    }
                }}
                TextButton(enabled=!busy,onClick={busy=true;scope.launch{try{DriveConnection.disconnect(activity);backups=emptyList();message="Drive bağlantısı kaldırıldı."}catch(e:CancellationException){throw e}catch(e:Exception){message=failureText(e)}finally{busy=false}}}){Text("Drive bağlantısını kaldır")}
            }
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(message.isNotEmpty())Text(message,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Yedekler ZIP biçimindedir. Aktarım HTTPS ile korunur; bu sürüm ayrıca parola ile ZIP şifrelemez. Google şifren ve erişim anahtarların yedeğe eklenmez. Otomatik yedekleme saati Android’in pil kısıtlamalarına göre değişebilir.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    pending?.let{snapshot->AlertDialog(onDismissRequest={pending=null},title={Text("Drive yedeğini geri yükle?")},text={Text("${snapshot.tasks.size} görev ve ${snapshot.courses.size} ders yüklenecek. Mevcut kayıtların yerini alır; önce cihazda güvenlik yedeği oluşturulur.")},confirmButton={TextButton(onClick={pending=null;busy=true;scope.launch{try{DriveBackupService.lock.withLock{BackupStore.restore(activity,snapshot)};message="Yedek geri yüklendi."}catch(e:CancellationException){throw e}catch(e:Exception){message=failureText(e)}finally{busy=false}}}){Text("Geri yükle")}},dismissButton={TextButton(onClick={pending=null}){Text("Vazgeç")}})}
}
