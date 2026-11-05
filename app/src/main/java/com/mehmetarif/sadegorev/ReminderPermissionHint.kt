package com.mehmetarif.sadegorev

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable internal fun ReminderPermissionHint(){
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    var notifications by remember{mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())}
    var exact by remember{mutableStateOf(Build.VERSION.SDK_INT<31||context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms())}
    fun refresh(){notifications=NotificationManagerCompat.from(context).areNotificationsEnabled();exact=Build.VERSION.SDK_INT<31||context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()}
    DisposableEffect(owner){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)refresh()};owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer)}}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){refresh()}
    if(!notifications){
        Text("Hatırlatma kaydedilir, ancak bildirim izni kapalı olduğu için bildirim görünmez.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
        TextButton(onClick={if(Build.VERSION.SDK_INT>=33)permission.launch(Manifest.permission.POST_NOTIFICATIONS) else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}){Text("Bildirim iznini aç")}
    }
    if(!exact&&Build.VERSION.SDK_INT>=31){
        Text("Tam saatinde hatırlatmak için alarm izni gerekli. İzin olmadan Android bildirimi geciktirebilir.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick={context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${context.packageName}")))}){Text("Alarm iznini aç")}
    }
}
