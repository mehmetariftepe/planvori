package com.mehmetarif.sadegorev.drive

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.api.identity.*
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.mehmetarif.sadegorev.BackupSnapshot
import com.mehmetarif.sadegorev.BackupStore
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class DrivePermissionRequired:Exception("Google Drive bağlantısını yeniden onayla.")
internal class DriveHttpError(val status:Int):Exception(when(status){401->"Google izni yenilenmeli. Drive’ı yeniden bağla.";403->"Drive erişimi reddedildi. API, test kullanıcısı ve OAuth ayarlarını kontrol et.";429->"Drive çok yoğun; biraz sonra tekrar dene.";else->"Drive işlemi tamamlanamadı ($status)."})
internal suspend fun <T> Task<T>.awaitDrive():T=suspendCancellableCoroutine{continuation->
    addOnSuccessListener{if(continuation.isActive)continuation.resume(it)}
    addOnFailureListener{if(continuation.isActive)continuation.resumeWithException(it)}
    addOnCanceledListener{continuation.cancel()}
}

internal object DriveConnection {
    fun prefs(context:Context)=context.getSharedPreferences("drive_connection",Context.MODE_PRIVATE)
    fun email(context:Context)=prefs(context).getString("email",null)
    fun auto(context:Context)=prefs(context).getBoolean("auto",false)
    fun request(context:Context,choose:Boolean=false):AuthorizationRequest {
        val builder=AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_SCOPE),Scope(EMAIL_SCOPE)))
        if(choose)builder.setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
        else email(context)?.let{builder.setAccount(Account(it,"com.google"))}
        return builder.build()
    }
    suspend fun silentToken(context:Context):String {
        check(email(context)!=null){"Önce Google Drive’ı bağla."}
        val result=Identity.getAuthorizationClient(context).authorize(request(context)).awaitDrive()
        if(result.hasResolution())throw DrivePermissionRequired()
        return result.accessToken?.takeIf{it.isNotBlank()}?:throw DrivePermissionRequired()
    }
    suspend fun disconnect(context:Context) {
        DriveBackupWorker.disable(context)
        DriveBackupService.lock.withLock {
            val mail=email(context)
            if(mail!=null)Identity.getAuthorizationClient(context).revokeAccess(RevokeAccessRequest.builder()
                .setAccount(Account(mail,"com.google")).setScopes(listOf(Scope(DRIVE_SCOPE),Scope(EMAIL_SCOPE))).build()).awaitDrive()
            check(prefs(context).edit().clear().commit())
        }
    }
}

/** Access tokens live only in memory. No client secret, refresh token or account password is stored. */
internal object DriveBackupService {
    val lock=Mutex()
    private const val ROOT="https://www.googleapis.com"
    private fun enc(value:String)=URLEncoder.encode(value,"UTF-8")
    private suspend fun request(token:String,path:String,method:String="GET",body:ByteArray?=null,type:String?=null):ByteArray=withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val connection=URL(ROOT+path).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod=method;connection.connectTimeout=20000;connection.readTimeout=30000
            connection.instanceFollowRedirects=false
            connection.setRequestProperty("Authorization","Bearer $token")
            if(body!=null){connection.doOutput=true;connection.setRequestProperty("Content-Type",type!!);connection.setFixedLengthStreamingMode(body.size);connection.outputStream.use{it.write(body)}}
            val status=connection.responseCode
            if(status !in 200..299)throw DriveHttpError(status)
            connection.inputStream.use{readBounded(it)}.also{currentCoroutineContext().ensureActive()}
        } finally {connection.disconnect()}
    }
    private suspend fun profile(token:String)=JSONObject(String(request(token,"/oauth2/v3/userinfo"),Charsets.UTF_8))
    suspend fun connect(context:Context,token:String) {
        val user=profile(token);val email=user.getString("email");val sub=user.getString("sub")
        check(email.isNotBlank()&&sub.isNotBlank()&&user.optBoolean("email_verified")){"Google hesabı doğrulanamadı."}
        check(DriveConnection.prefs(context).edit().putString("email",email).putString("sub",sub).remove("last").remove("status").commit())
    }
    private suspend fun verifyAccount(context:Context,token:String) {
        val expected=DriveConnection.prefs(context).getString("sub",null)?:throw DrivePermissionRequired()
        check(profile(token).getString("sub")==expected){"Seçilen Google hesabı bağlı hesaptan farklı. Drive’ı yeniden bağla."}
    }
    suspend fun upload(context:Context,token:String,automatic:Boolean=false)=lock.withLock {
        if(automatic&&!DriveConnection.auto(context))return@withLock
        verifyAccount(context,token)
        val file=BackupStore.create(context)
        try {
            check(file.length()<=MAX_CLOUD_BYTES){"Yedek çok büyük."}
            // Validate before upload; unsuccessful uploads never replace previous backups.
            withContext(Dispatchers.IO){file.inputStream().use{BackupStore.read(it)}}
            val boundary="planvori-${UUID.randomUUID()}"
            val body=withContext(Dispatchers.IO){ByteArrayOutputStream().apply{
                write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${backupMetadata(file.name)}\r\n--$boundary\r\nContent-Type: application/zip\r\n\r\n".toByteArray(Charsets.UTF_8))
                file.inputStream().use{it.copyTo(this)}
                write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
            }.toByteArray()}
            val response=JSONObject(String(request(token,"/upload/drive/v3/files?uploadType=multipart&fields=id", "POST",body,"multipart/related; boundary=$boundary"),Charsets.UTF_8))
            check(response.getString("id").isNotBlank())
            check(DriveConnection.prefs(context).edit().putLong("last",System.currentTimeMillis()).putString("status","Son yedek Drive’a kaydedildi.").commit())
        } finally {file.delete()}
    }
    suspend fun list(context:Context,token:String):List<DriveBackup> {
        verifyAccount(context,token)
        val query="trashed = false and appProperties has { key='format' and value='planvori-backup-v2' }"
        val path="/drive/v3/files?spaces=appDataFolder&pageSize=100&orderBy=createdTime%20desc&q=${enc(query)}&fields=${enc("files(id,name,createdTime)")}"
        return parseBackups(JSONObject(String(request(token,path),Charsets.UTF_8)))
    }
    suspend fun download(context:Context,token:String,id:String):BackupSnapshot {
        verifyAccount(context,token)
        require(id.matches(Regex("[A-Za-z0-9_-]+")))
        // Only IDs from this app's current backup listing can be restored.
        require(list(context,token).any{it.id==id}){"Yedek bulunamadı. Listeyi yenile."}
        val data=request(token,"/drive/v3/files/$id?alt=media")
        return withContext(Dispatchers.IO){data.inputStream().use{BackupStore.read(it)}}
    }
}
