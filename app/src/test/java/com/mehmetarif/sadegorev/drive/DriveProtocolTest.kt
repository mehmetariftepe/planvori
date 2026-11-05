package com.mehmetarif.sadegorev.drive

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class DriveProtocolTest {
    @Test fun uploadUsesPrivateAppFolderAndExplicitBackupMarker(){
        val json=backupMetadata("Planvori-yedek-42.zip")
        assertEquals("appDataFolder",json.getJSONArray("parents").getString(0))
        assertEquals("application/zip",json.getString("mimeType"))
        assertEquals("planvori-backup-v2",json.getJSONObject("appProperties").getString("format"))
        assertFalse(json.has("permissions"))
    }
    @Test fun oversizedCloudResponseIsRejectedBeforeRestore(){
        try{readBounded(ByteArrayInputStream(ByteArray(11)),10);fail("Oversized response accepted")}
        catch(e:IllegalArgumentException){assertTrue(e.message!!.contains("büyük"))}
    }
    @Test fun exactLimitAndEmptyResponsesAreAccepted(){
        assertArrayEquals(byteArrayOf(1,2,3),readBounded(ByteArrayInputStream(byteArrayOf(1,2,3)),3))
        assertEquals(0,readBounded(ByteArrayInputStream(byteArrayOf()),3).size)
    }
    @Test fun backupListPreservesServerOrderAndIdentifiers(){
        val json=JSONObject().put("files",JSONArray().put(JSONObject().put("id","second").put("name","new.zip").put("createdTime","2026-10-07T06:00:00Z"))
            .put(JSONObject().put("id","first").put("name","old.zip").put("createdTime","2026-10-06T06:00:00Z")))
        val parsed=parseBackups(json)
        assertEquals(listOf("second","first"),parsed.map{it.id})
        assertEquals("new.zip",parsed.first().name)
    }
    @Test fun malformedBackupEntryIsRejected(){
        try{parseBackups(JSONObject().put("files",JSONArray().put(JSONObject().put("name","missing-id.zip"))));fail("Missing ID accepted")}
        catch(e:org.json.JSONException){assertNotNull(e.message)}
    }
}
