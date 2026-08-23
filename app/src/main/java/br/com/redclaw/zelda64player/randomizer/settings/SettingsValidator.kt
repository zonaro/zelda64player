package br.com.redclaw.zelda64player.randomizer.settings

/**
 * Client-side sanity checks for the randomizer settings form, performed before
 * submitting to the OoTR API so obvious mistakes are caught without a network
 * round-trip.
 *
 * Pure (no Android dependencies) so it can be unit-tested on the JVM.
 */
object SettingsValidator {

    /**
     * Validate the current form values against the schema.
     *
     * Rules:
     *  - ENUM options must be non-empty and equal to one of the declared choice
     *    values (when choices are known).
     *  - INT options must fall within `[min, max]` when those bounds are declared.
     *  - BOOL / STRING / LIST options are not validated client-side.
     *
     * @param schema The parsed settings schema.
     * @param values Current form values keyed by option name. Missing entries
     *   fall back to the option's [SchemaOption.default].
     * @return The list of offending option names (empty when valid).
     */
    fun validate(
        schema: RandomizerSettingsSchema,
        values: Map<String, Any?>
    ): List<String> {
        val offending = mutableListOf<String>()
        for (category in schema.categories) {
            for (option in category.options) {
                val value = values[option.name] ?: option.default
                when (option.type) {
                    SchemaOptionType.ENUM -> validateEnum(option, value, offending)
                    SchemaOptionType.INT -> validateInt(option, value, offending)
                    else -> { /* no client-side check */ }
                }
            }
        }
        return offending
    }

    private fun validateEnum(option: SchemaOption, value: Any?, offending: MutableList<String>) {
        val str = value as? String ?: ""
        if (str.isEmpty()) {
            offending += option.name
            return
        }
        if (option.choices.isNotEmpty() && option.choices.none { it.value == str }) {
            offending += option.name
        }
    }

    private fun validateInt(option: SchemaOption, value: Any?, offending: MutableList<String>) {
        val intVal = (value as? Number)?.toInt() ?: 0
        if (option.min != null && intVal < option.min) offending += option.name
        if (option.max != null && intVal > option.max) offending += option.name
    }
}
