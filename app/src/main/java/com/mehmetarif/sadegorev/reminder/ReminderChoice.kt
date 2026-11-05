package com.mehmetarif.sadegorev.reminder

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal const val NO_REMINDER=-1
internal const val CUSTOM_REMINDER=-2
internal val reminderOptions=listOf(NO_REMINDER to "Hatırlatma yok",0 to "Görev saatinde",5 to "5 dakika önce",15 to "15 dakika önce",30 to "30 dakika önce",60 to "1 saat önce")

/** New tasks follow their due time; existing explicitly disabled reminders remain disabled. */
internal fun initialReminderChoice(existing:Boolean,due:Long?,reminder:Long?):Int {
    if(!existing||due==null)return 0
    if(reminder==null)return NO_REMINDER
    val offset=due-reminder
    return reminderOptions.firstOrNull{it.first>=0&&it.first*60000L==offset}?.first?:CUSTOM_REMINDER
}
internal fun reminderTime(choice:Int,due:Long?,customTime:Long?):Long? = when {
    due==null||choice==NO_REMINDER->null
    choice==CUSTOM_REMINDER->customTime
    else->due-choice*60000L
}
internal fun reminderLabel(choice:Int,reminder:Long?,zone:ZoneId=ZoneId.systemDefault()):String {
    return if(choice==CUSTOM_REMINDER)reminder?.let{"Özel · "+Instant.ofEpochMilli(it).atZone(zone).format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm",Locale.forLanguageTag("tr")))}?:"Özel tarih ve saat"
    else reminderOptions.first{it.first==choice}.second
}
