package br.com.redclaw.zelda64player.randomizer.settings

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [PlandomizerValidator]: structural type checks, unknown-key
 * warnings, `:`-key preservation, and value-type rules.
 */
class PlandomizerValidatorTest {

    @Test
    fun emptyStringIsInvalid() {
        val r = PlandomizerValidator.validate("")
        assertFalse(r.valid)
        assertTrue(r.errors.any { "Empty JSON" in it })
        assertNull(r.parsed)
    }

    @Test
    fun malformedJsonIsInvalid() {
        val r = PlandomizerValidator.validate("{ not json ")
        assertFalse(r.valid)
        assertTrue(r.errors.any { "Invalid JSON" in it })
        assertNull(r.parsed)
    }

    @Test
    fun emptyObjectIsValid() {
        val r = PlandomizerValidator.validate("{}")
        assertTrue(r.valid)
        assertTrue(r.errors.isEmpty())
        assertTrue(r.warnings.isEmpty())
    }

    @Test
    fun unknownKeyProducesWarningButIsValid() {
        val r = PlandomizerValidator.validate("{\"some_future_key\": 1}")
        assertTrue(r.valid)
        assertTrue(r.warnings.any { "some_future_key" in it })
    }

    @Test
    fun colonPrefixedKeyIsIgnoredSilently() {
        val r = PlandomizerValidator.validate("{\":spoiler_concept\": {\"a\":1}}")
        assertTrue(r.valid)
        assertTrue(r.warnings.isEmpty())
    }

    @Test
    fun fileHashWrongTypeIsError() {
        val r = PlandomizerValidator.validate("{\"file_hash\": \"oops\"}")
        assertFalse(r.valid)
        assertTrue(r.errors.any { "file_hash" in it && "array" in it })
    }

    @Test
    fun fileHashTooLongIsError() {
        val arr = JSONArray().apply { for (i in 1..6) put("x$i") }
        val r = PlandomizerValidator.validate(JSONObject().put("file_hash", arr).toString())
        assertFalse(r.valid)
        assertTrue(r.errors.any { "at most 5" in it })
    }

    @Test
    fun fileHashWithNullsIsValid() {
        val arr = JSONArray().apply {
            put("a"); put(JSONObject.NULL); put("c"); put(JSONObject.NULL); put("e")
        }
        val r = PlandomizerValidator.validate(JSONObject().put("file_hash", arr).toString())
        assertTrue(r.valid)
    }

    @Test
    fun startingItemsNonNumberIsError() {
        val obj = JSONObject().put(
            "starting_items",
            JSONObject().put("Bombs", "many")
        )
        val r = PlandomizerValidator.validate(obj.toString())
        assertFalse(r.valid)
        assertTrue(r.errors.any { "starting_items" in it })
    }

    @Test
    fun itemPoolWrongTypeIsError() {
        val obj = JSONObject().put(
            "item_pool",
            JSONObject().put("X", true)
        )
        val r = PlandomizerValidator.validate(obj.toString())
        assertFalse(r.valid)
        assertTrue(r.errors.any { "item_pool" in it })
    }

    @Test
    fun itemPoolObjectMissingTypeIsError() {
        val obj = JSONObject().put(
            "item_pool",
            JSONObject().put("X", JSONObject().put("count", 5))
        )
        val r = PlandomizerValidator.validate(obj.toString())
        assertFalse(r.valid)
        assertTrue(r.errors.any { "type" in it && "count" in it })
    }

    @Test
    fun dungeonsWrongValueIsWarning() {
        val obj = JSONObject().put(
            "dungeons",
            JSONObject().put("Deku Tree", "weird")
        )
        val r = PlandomizerValidator.validate(obj.toString())
        assertTrue(r.valid)
        assertTrue(r.warnings.any { "Deku Tree" in it })
    }

    @Test
    fun dungeonsNonStringIsError() {
        val obj = JSONObject().put(
            "dungeons",
            JSONObject().put("Deku Tree", 5)
        )
        val r = PlandomizerValidator.validate(obj.toString())
        assertFalse(r.valid)
    }

    @Test
    fun objectSectionWrongTypeIsError() {
        val obj = JSONObject().put("locations", "notanobject")
        val r = PlandomizerValidator.validate(obj.toString())
        assertFalse(r.valid)
        assertTrue(r.errors.any { "locations" in it && "object" in it })
    }

    @Test
    fun fullValidPlacementIsValid() {
        val obj = JSONObject()
            .put("settings", JSONObject().put("logic_rules", "standard"))
            .put("starting_items", JSONObject().put("Bombs", 5))
            .put(
                "item_pool",
                JSONObject().put("Rupee", JSONObject().put("type", "add").put("count", 10))
            )
            .put("dungeons", JSONObject().put("Deku Tree", "mq"))
            .put("trials", JSONObject().put("Fire Trial", "inactive"))
            .put("locations", JSONObject().put("Link's House", "Kokiri Sword"))
            .put("entrances", JSONObject().put("Link's House", "Kokiri Forest"))
            .put("custom_groups", JSONObject().put("My Group", JSONArray().put("Loc A").put("Loc B")))
        val arr = JSONArray().apply { put("a"); put(JSONObject.NULL); put("c"); put(JSONObject.NULL); put("e") }
        obj.put("file_hash", arr)
        val r = PlandomizerValidator.validate(obj.toString())
        assertTrue(r.valid)
        assertTrue(r.errors.isEmpty())
    }
}
