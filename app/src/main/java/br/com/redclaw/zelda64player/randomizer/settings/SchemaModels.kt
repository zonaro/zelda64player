package br.com.redclaw.zelda64player.randomizer.settings

import org.json.JSONArray
import org.json.JSONObject

/**
 * Type of a single settings option, mirroring the `"type"` field of an option
 * in [assets/randomizer/oot_settings_schema.json][RANDOMIZER_SCHEMA_ASSET].
 */
enum class SchemaOptionType {
    /** Checkbox / switch. Value is a [Boolean]. */
    BOOL,

    /** Dropdown of discrete choices. Value is a [String] (the choice value). */
    ENUM,

    /** Integer range (slider). Value is an [Int]. */
    INT,

    /** Free text. Value is a [String]. */
    STRING,

    /** Multi-select list of choices. Value is a [List] of choice values. */
    LIST,
}

/**
 * A single choice for an [SchemaOptionType.ENUM] or [SchemaOptionType.LIST]
 * option.
 *
 * @property value Canonical value sent to the OoTR API (always stored as a
 *   string; numeric enum values are stringified by the converter).
 * @property label Human-readable label shown in the UI (English canonical).
 */
data class SchemaChoice(
    val value: String,
    val label: String
)

/**
 * A single settings option as described by the schema asset.
 *
 * @property name Canonical setting key sent to the OoTR API.
 * @property type Discriminator for rendering and validation.
 * @property label Display label (English canonical, from the asset).
 * @property tooltip Optional help text (English canonical, from the asset).
 * @property default Default value used when the user has not changed it.
 * @property choices Choices for [SchemaOptionType.ENUM]/[SchemaOptionType.LIST].
 * @property min Inclusive lower bound for [SchemaOptionType.INT] ranges.
 * @property max Inclusive upper bound for [SchemaOptionType.INT] ranges.
 * @property step Increment for [SchemaOptionType.INT] sliders.
 * @property cosmetic When true the option is stripped from the submitted
 *   settings map unless cosmetics are explicitly enabled.
 */
data class SchemaOption(
    val name: String,
    val type: SchemaOptionType,
    val label: String,
    val tooltip: String?,
    val default: Any?,
    val choices: List<SchemaChoice> = emptyList(),
    val min: Int? = null,
    val max: Int? = null,
    val step: Int? = null,
    val cosmetic: Boolean = false
)

/**
 * A group of options rendered together (one tab / section in the form).
 *
 * @property id Stable category identifier (e.g. `"main"`, `"open"`).
 * @property options Options belonging to this category, in display order.
 */
data class SchemaCategory(
    val id: String,
    val options: List<SchemaOption>
)

/**
 * The fully parsed randomizer settings schema.
 *
 * @property schemaVersion Version of the asset format (currently 1).
 * @property sourceVersion OoTR version the asset was generated from.
 * @property categories All setting categories, in display order.
 */
data class RandomizerSettingsSchema(
    val schemaVersion: Int,
    val sourceVersion: String,
    val categories: List<SchemaCategory>
)

/** Asset path of the generated settings schema inside the APK. */
const val RANDOMIZER_SCHEMA_ASSET = "randomizer/oot_settings_schema.json"

/**
 * Parse a [RandomizerSettingsSchema] from the raw JSON produced by
 * `tools/randomizer/generate_settings_schema.py`.
 *
 * Pure (no Android dependencies) so it can be unit-tested on the JVM.
 */
fun parseRandomizerSchema(json: String): RandomizerSettingsSchema {
    val root = JSONObject(json)
    val schemaVersion = root.optInt("schemaVersion", 1)
    val sourceVersion = root.optString("sourceVersion", "unknown")
    val categories = mutableListOf<SchemaCategory>()

    val categoriesArr = root.getJSONArray("categories")
    for (i in 0 until categoriesArr.length()) {
        val catObj = categoriesArr.getJSONObject(i)
        val catId = catObj.getString("id")
        val optionsArr = catObj.getJSONArray("options")
        val options = mutableListOf<SchemaOption>()
        for (j in 0 until optionsArr.length()) {
            options += parseOption(optionsArr.getJSONObject(j))
        }
        categories += SchemaCategory(catId, options)
    }
    return RandomizerSettingsSchema(schemaVersion, sourceVersion, categories)
}

private fun parseOption(o: JSONObject): SchemaOption {
    val name = o.getString("name")
    val type = when (o.getString("type")) {
        "bool" -> SchemaOptionType.BOOL
        "enum" -> SchemaOptionType.ENUM
        "int" -> SchemaOptionType.INT
        "string" -> SchemaOptionType.STRING
        "list" -> SchemaOptionType.LIST
        else -> SchemaOptionType.STRING
    }
    val label = o.optString("label", name)
    val tooltip = o.optString("tooltip", "").takeIf { it.isNotBlank() }
    val default = parseDefault(type, o.opt("default"))
    val choices = if (o.has("choices")) {
        val arr = o.getJSONArray("choices")
        (0 until arr.length()).map { idx ->
            val c = arr.getJSONObject(idx)
            SchemaChoice(c.getString("value"), c.optString("label", c.getString("value")))
        }
    } else emptyList()
    val min = if (o.has("min")) o.getInt("min") else null
    val max = if (o.has("max")) o.getInt("max") else null
    val step = if (o.has("step")) o.getInt("step") else null
    val cosmetic = o.optBoolean("cosmetic", false)
    return SchemaOption(name, type, label, tooltip, default, choices, min, max, step, cosmetic)
}

private fun parseDefault(type: SchemaOptionType, raw: Any?): Any? {
    if (raw == null || raw === JSONObject.NULL) {
        return when (type) {
            SchemaOptionType.BOOL -> false
            SchemaOptionType.INT -> 0
            SchemaOptionType.STRING -> ""
            SchemaOptionType.ENUM -> null
            SchemaOptionType.LIST -> emptyList<String>()
        }
    }
    return when (type) {
        SchemaOptionType.BOOL -> (raw as? Boolean) ?: false
        SchemaOptionType.INT -> (raw as? Number)?.toInt() ?: 0
        SchemaOptionType.STRING -> raw.toString()
        SchemaOptionType.ENUM -> raw.toString()
        SchemaOptionType.LIST -> if (raw is JSONArray) {
            (0 until raw.length()).map { raw.getString(it) }
        } else emptyList<String>()
    }
}
