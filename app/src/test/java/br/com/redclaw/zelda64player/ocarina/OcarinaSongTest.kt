package br.com.redclaw.zelda64player.ocarina

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OcarinaSongTest {

    @Test
    fun fromCatalogJson_parsesValidSong() {
        val obj = JSONObject().apply {
            put("id", "custom_1")
            put("name", "My Song")
            put("notes", JSONArray(listOf("C_LEFT", "A", "C_UP")))
        }
        val song = OcarinaSong.fromCatalogJson(obj)
        assertNotNull(song)
        assertEquals("custom_1", song!!.id)
        assertEquals("My Song", song.rawName)
        assertEquals(3, song.notes.size)
        assertEquals(OcarinaNote.C_LEFT, song.notes[0])
        assertEquals(OcarinaNote.A, song.notes[1])
        assertEquals(OcarinaNote.C_UP, song.notes[2])
    }

    @Test
    fun fromCatalogJson_missingId_returnsNull() {
        val obj = JSONObject().apply {
            put("name", "X")
            put("notes", JSONArray(listOf("A")))
        }
        assertNull(OcarinaSong.fromCatalogJson(obj))
    }

    @Test
    fun fromCatalogJson_missingName_returnsNull() {
        val obj = JSONObject().apply {
            put("id", "x")
            put("notes", JSONArray(listOf("A")))
        }
        assertNull(OcarinaSong.fromCatalogJson(obj))
    }

    @Test
    fun fromCatalogJson_invalidNoteCode_returnsNull() {
        val obj = JSONObject().apply {
            put("id", "x")
            put("name", "X")
            put("notes", JSONArray(listOf("A", "NOT_A_NOTE")))
        }
        assertNull(OcarinaSong.fromCatalogJson(obj))
    }

    @Test
    fun fromCatalogJson_missingNotes_returnsNull() {
        val obj = JSONObject().apply {
            put("id", "x")
            put("name", "X")
        }
        assertNull(OcarinaSong.fromCatalogJson(obj))
    }

    @Test
    fun fromCatalogJson_emptyNotes_returnsNull() {
        val obj = JSONObject().apply {
            put("id", "x")
            put("name", "X")
            put("notes", JSONArray(emptyList<String>()))
        }
        assertNull(OcarinaSong.fromCatalogJson(obj))
    }

    @Test
    fun fromCode_mapsAllNotes() {
        assertEquals(OcarinaNote.A, OcarinaNote.fromCode("A"))
        assertEquals(OcarinaNote.C_UP, OcarinaNote.fromCode("C_UP"))
        assertEquals(OcarinaNote.C_DOWN, OcarinaNote.fromCode("C_DOWN"))
        assertEquals(OcarinaNote.C_LEFT, OcarinaNote.fromCode("C_LEFT"))
        assertEquals(OcarinaNote.C_RIGHT, OcarinaNote.fromCode("C_RIGHT"))
        assertNull(OcarinaNote.fromCode("UNKNOWN"))
    }

    @Test
    fun toJson_roundTrips() {
        val song = OcarinaSong("id1", rawName = "Raw", notes = listOf(OcarinaNote.A, OcarinaNote.C_LEFT))
        val json = song.toJson()
        assertEquals("id1", json.getString("id"))
        assertEquals("Raw", json.getString("name"))
        val notes = json.getJSONArray("notes")
        assertEquals(2, notes.length())
        assertEquals("A", notes.getString(0))
        assertEquals("C_LEFT", notes.getString(1))
        val parsed = OcarinaSong.fromCatalogJson(json)
        assertNotNull(parsed)
        assertEquals(song.notes, parsed!!.notes)
    }
}
