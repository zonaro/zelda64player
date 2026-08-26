package br.com.redclaw.zelda64player.data.model

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HackCatalogTest {

    private fun validHackJson(id: String, name: String): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("description", "desc")
        put("author", "auth")
        put("version", "1.0")
        put(
            "baseRom", JSONObject().apply {
                put("name", "ROM")
                put("gameCode", "CZLE")
                put("versionByte", 0)
                put("checksums", JSONObject().apply { put("crc32", "0xABCDEF12") })
            }
        )
        put(
            "patch", JSONObject().apply {
                put("url", "https://example.com/$id.bps")
                put("filename", "$id.bps")
                put("size", 100)
                put("checksums", JSONObject().apply { put("crc32", "0x12345678") })
            }
        )
    }

    @Test
    fun parsesValidCatalog() {
        val root = JSONObject().apply {
            put("catalogVersion", 2)
            put("lastUpdated", "2026-08-22T10:30:00Z")
            put(
                "hacks", JSONArray().apply {
                    put(validHackJson("hack_a", "Hack A"))
                    put(validHackJson("hack_b", "Hack B"))
                }
            )
        }
        val catalog = HackCatalog.parse(root)
        assertEquals(2, catalog.catalogVersion)
        assertEquals(2, catalog.hacks.size)
        assertEquals("hack_a", catalog.hacks[0].id)
        // crc32 is normalized: 0x prefix stripped and lowercased.
        assertEquals("abcdef12", catalog.hacks[0].baseRom.checksums.crc32)
        assertEquals("12345678", catalog.hacks[0].patch!!.checksums.crc32)
    }

    @Test
    fun skipsMalformedEntry() {
        val root = JSONObject().apply {
            put("catalogVersion", 1)
            put("lastUpdated", "x")
            put(
                "hacks", JSONArray().apply {
                    put(validHackJson("good", "Good"))
                    put(JSONObject().apply { put("id", "bad") }) // missing required fields
                    put("notAnObject") // not even a JSON object
                }
            )
        }
        val catalog = HackCatalog.parse(root)
        assertEquals(1, catalog.hacks.size)
        assertEquals("good", catalog.hacks[0].id)
    }

    @Test
    fun skipsEntryMissingRequiredField() {
        val incomplete = JSONObject().apply {
            put("id", "no_name")
            put("description", "x")
            // missing name, author, version, baseRom, patch
        }
        val root = JSONObject().apply {
            put("hacks", JSONArray().apply { put(incomplete) })
        }
        val catalog = HackCatalog.parse(root)
        assertTrue(catalog.hacks.isEmpty())
    }

    @Test
    fun ignoresUnknownFields() {
        val hack = validHackJson("h1", "H1")
        hack.put("extraField", "should be ignored")
        hack.getJSONObject("baseRom").put("unknown", 42)
        val root = JSONObject().apply {
            put("hacks", JSONArray().apply { put(hack) })
            put("unknownTop", "x")
        }
        val catalog = HackCatalog.parse(root)
        assertEquals(1, catalog.hacks.size)
        assertEquals("h1", catalog.hacks[0].id)
    }
}
