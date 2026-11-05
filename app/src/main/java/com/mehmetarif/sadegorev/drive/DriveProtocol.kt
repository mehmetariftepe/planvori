package com.mehmetarif.sadegorev.drive

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

internal const val DRIVE_SCOPE="https://www.googleapis.com/auth/drive.appdata"
internal const val EMAIL_SCOPE="https://www.googleapis.com/auth/userinfo.email"
internal const val MAX_CLOUD_BYTES=20*1024*1024
internal data class DriveBackup(val id:String,val name:String,val createdAt:String)
internal fun backupMetadata(name:String)=JSONObject().put("name",name).put("mimeType","application/zip")
    .put("parents",JSONArray().put("appDataFolder")).put("appProperties",JSONObject().put("format","planvori-backup-v2"))
internal fun readBounded(input:InputStream,limit:Int=MAX_CLOUD_BYTES):ByteArray {
    val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
    while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=limit){"Drive yedeği çok büyük."};out.write(buffer,0,n)}
    return out.toByteArray()
}
internal fun parseBackups(json:JSONObject):List<DriveBackup> {
    val rows=json.getJSONArray("files")
    return (0 until rows.length()).map{rows.getJSONObject(it)}.map{DriveBackup(it.getString("id"),it.getString("name"),it.getString("createdTime"))}
}
