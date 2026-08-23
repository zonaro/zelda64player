package br.com.redclaw.zelda64player.randomizer.settings

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds the JSON settings map submitted to the OoTR `POST /api/v2/seed/create`
 * endpoint from the current form values.
 *
 * Every non-cosmetic option is emitted explicitly (the server validates the
 * full map); options flagged `cosmetic: true` are stripped unless cosmetics are
 * explicitly enabled via [stripCosmetics]. Lists are emitted as JSON arrays and
 * ints/bools as their native JSON types.
 *
 * Pure (no Android dependencies) so it can be unit-tested on the JVM.
 */
object SettingsStateBuilder {
    /**
     * When true (the default), cosmetic options are omitted from the generated
     * settings map. Exposed as a constant so the ViewModel can pass a user
     * toggle through unchanged.
     */
    const val STRIP_COSMETICS_DEFAULT = true

    /**
     * @param schema The parsed settings schema (defines which options exist and
     *   which are cosmetic).
     * @param values Current form values keyed by option name. Missing entries
     *   fall back to the option's [SchemaOption.default].
     * @param stripCosmetics When true, omit options flagged `cosmetic: true`.
     * @return A [JSONObject] ready to be sent as the request body.
     */
    fun build(
        schema: RandomizerSettingsSchema,
        values: Map<String, Any?>,
        stripCosmetics: Boolean = STRIP_COSMETICS_DEFAULT
    ): JSONObject {
        val root = JSONObject()
        for (category in schema.categories) {
            for (option in category.options) {
                if (stripCosmetics && option.cosmetic) continue
                val value = values[option.name] ?: option.default
                putValue(root, option, value)
            }
        }
        return root
    }

    private fun putValue(root: JSONObject, option: SchemaOption, value: Any?) {
        when (option.type) {
            SchemaOptionType.BOOL -> root.put(option.name, value as? Boolean ?: false)
            SchemaOptionType.INT -> root.put(option.name, (value as? Number)?.toInt() ?: 0)
            SchemaOptionType.STRING -> root.put(option.name, value as? String ?: "")
            SchemaOptionType.ENUM -> root.put(option.name, value as? String ?: "")
            SchemaOptionType.LIST -> {
                val arr = JSONArray()
                @Suppress("UNCHECKED_CAST")
                val list = (value as? List<Any?>) ?: emptyList<Any?>()
                list.forEach { arr.put(it?.toString() ?: "") }
                root.put(option.name, arr)
            }
        }
    }
}
