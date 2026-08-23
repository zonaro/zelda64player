package br.com.redclaw.zelda64player.randomizer.settings

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [SettingsStateBuilder]: cosmetic stripping, list emission as JSON
 * arrays, and correct native JSON types for every option type.
 */
class SettingsStateBuilderTest {

    private fun schemaWith(options: List<SchemaOption>): RandomizerSettingsSchema =
        RandomizerSettingsSchema(1, "test", listOf(SchemaCategory("main", options)))

    @Test
    fun stripsCosmeticOptionsWhenRequested() {
        val cosmetic = SchemaOption("cos", SchemaOptionType.BOOL, "Cos", null, false, cosmetic = true)
        val normal = SchemaOption("norm", SchemaOptionType.BOOL, "Norm", null, true)
        val schema = schemaWith(listOf(cosmetic, normal))
        val values = mapOf("cos" to true, "norm" to true)

        val stripped = SettingsStateBuilder.build(schema, values, stripCosmetics = true)
        assertFalse("cosmetic option must be omitted when stripping", stripped.has("cos"))
        assertTrue("non-cosmetic option must remain", stripped.getBoolean("norm"))

        val kept = SettingsStateBuilder.build(schema, values, stripCosmetics = false)
        assertTrue("cosmetic option must remain when not stripping", kept.has("cos"))
        assertTrue(kept.getBoolean("cos"))
    }

    @Test
    fun emitsListsAsJsonArrays() {
        val listOpt = SchemaOption(
            "tricks", SchemaOptionType.LIST, "Tricks", null, emptyList<String>(),
            choices = listOf(SchemaChoice("a", "A"), SchemaChoice("b", "B"))
        )
        val schema = schemaWith(listOf(listOpt))
        val values = mapOf("tricks" to listOf("a", "b"))

        val json = SettingsStateBuilder.build(schema, values)
        assertTrue(json.has("tricks"))
        val arr = json.get("tricks")
        assertTrue("list must serialize to a JSONArray", arr is JSONArray)
        assertEquals(2, (arr as JSONArray).length())
        assertEquals("a", arr.getString(0))
        assertEquals("b", arr.getString(1))
    }

    @Test
    fun emitsAllOptionsWithNativeTypes() {
        val boolOpt = SchemaOption("b", SchemaOptionType.BOOL, "B", null, false)
        val intOpt = SchemaOption("i", SchemaOptionType.INT, "I", null, 0, min = 0, max = 10)
        val strOpt = SchemaOption("s", SchemaOptionType.STRING, "S", null, "")
        val enumOpt = SchemaOption(
            "e", SchemaOptionType.ENUM, "E", null, "x",
            choices = listOf(SchemaChoice("x", "X"))
        )
        val schema = schemaWith(listOf(boolOpt, intOpt, strOpt, enumOpt))
        val values = mapOf("b" to true, "i" to 7, "s" to "hi", "e" to "x")

        val json = SettingsStateBuilder.build(schema, values)
        assertEquals(4, json.length())
        assertEquals(true, json.getBoolean("b"))
        assertEquals(7, json.getInt("i"))
        assertEquals("hi", json.getString("s"))
        assertEquals("x", json.getString("e"))
    }

    @Test
    fun fallsBackToDefaultsForMissingValues() {
        val opt = SchemaOption("x", SchemaOptionType.STRING, "X", null, "def")
        val schema = schemaWith(listOf(opt))
        val json = SettingsStateBuilder.build(schema, emptyMap())
        assertEquals("def", json.getString("x"))
    }
}
