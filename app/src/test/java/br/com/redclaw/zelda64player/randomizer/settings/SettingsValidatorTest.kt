package br.com.redclaw.zelda64player.randomizer.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [SettingsValidator]: enum non-empty / valid-choice checks and int
 * min/max bounds. BOOL / STRING / LIST options are not client-validated.
 */
class SettingsValidatorTest {

    private fun schemaWith(options: List<SchemaOption>): RandomizerSettingsSchema =
        RandomizerSettingsSchema(1, "test", listOf(SchemaCategory("main", options)))

    @Test
    fun validInputProducesNoOffenders() {
        val enumOpt = SchemaOption(
            "logic", SchemaOptionType.ENUM, "Logic", null, "std",
            choices = listOf(SchemaChoice("std", "Std"), SchemaChoice("glitch", "Glitch"))
        )
        val intOpt = SchemaOption("count", SchemaOptionType.INT, "Count", null, 0, min = 0, max = 10, step = 1)
        val schema = schemaWith(listOf(enumOpt, intOpt))

        val result = SettingsValidator.validate(schema, mapOf("logic" to "std", "count" to 5))
        assertTrue(result.isEmpty())
    }

    @Test
    fun emptyEnumIsOffending() {
        val enumOpt = SchemaOption(
            "logic", SchemaOptionType.ENUM, "Logic", null, "std",
            choices = listOf(SchemaChoice("std", "Std"))
        )
        val schema = schemaWith(listOf(enumOpt))

        val result = SettingsValidator.validate(schema, mapOf("logic" to ""))
        assertEquals(listOf("logic"), result)
    }

    @Test
    fun unknownEnumValueIsOffending() {
        val enumOpt = SchemaOption(
            "logic", SchemaOptionType.ENUM, "Logic", null, "std",
            choices = listOf(SchemaChoice("std", "Std"))
        )
        val schema = schemaWith(listOf(enumOpt))

        val result = SettingsValidator.validate(schema, mapOf("logic" to "nope"))
        assertEquals(listOf("logic"), result)
    }

    @Test
    fun intOutOfRangeIsOffending() {
        val intOpt = SchemaOption("count", SchemaOptionType.INT, "Count", null, 0, min = 0, max = 10, step = 1)
        val schema = schemaWith(listOf(intOpt))

        assertEquals(listOf("count"), SettingsValidator.validate(schema, mapOf("count" to 99)))
        assertEquals(listOf("count"), SettingsValidator.validate(schema, mapOf("count" to -5)))
        assertTrue(SettingsValidator.validate(schema, mapOf("count" to 10)).isEmpty())
    }

    @Test
    fun boolAndStringAreNotValidated() {
        val boolOpt = SchemaOption("b", SchemaOptionType.BOOL, "B", null, false)
        val strOpt = SchemaOption("s", SchemaOptionType.STRING, "S", null, "")
        val schema = schemaWith(listOf(boolOpt, strOpt))

        assertTrue(SettingsValidator.validate(schema, mapOf("b" to false, "s" to "")).isEmpty())
    }
}
