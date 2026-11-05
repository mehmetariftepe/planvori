package com.mehmetarif.sadegorev.drive

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.concurrent.TimeUnit

class DriveBackupWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    override suspend fun doWork():Result {
        if(!DriveConnection.auto(applicationContext)||DriveConnection.email(applicationContext)==null)return Result.success()
        return try {
            val token=DriveConnection.silentToken(applicationContext)
            DriveBackupService.upload(applicationContext,token,automatic=true)
            Result.success()
        } catch(e:CancellationException){throw e}
        catch(e:Exception){
            val transient=e is IOException||(e is DriveHttpError&&(e.status==429||e.status>=500))
            DriveConnection.prefs(applicationContext).edit().putString("status",if(transient)"İnternet/Drive erişimi bekleniyor; yedek henüz alınmadı." else "Otomatik yedek alınamadı. Yedekleme ekranından Drive bağlantısını kontrol et.").commit()
            if(transient&&runAttemptCount<3)Result.retry() else Result.failure()
        }
    }
    companion object {
        private const val NAME="planvori-drive-daily"
        fun enable(context:Context){
            check(DriveConnection.email(context)!=null)
            check(DriveConnection.prefs(context).edit().putBoolean("auto",true).commit())
            val work=PeriodicWorkRequestBuilder<DriveBackupWorker>(24,TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME,ExistingPeriodicWorkPolicy.UPDATE,work)
        }
        fun disable(context:Context){
            check(DriveConnection.prefs(context).edit().putBoolean("auto",false).commit())
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
