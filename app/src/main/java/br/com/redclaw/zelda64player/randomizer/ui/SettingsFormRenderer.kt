package br.com.redclaw.zelda64player.randomizer.ui

import br.com.redclaw.zelda64player.randomizer.settings.RandomizerSettingsSchema
import br.com.redclaw.zelda64player.randomizer.settings.SchemaCategory
import br.com.redclaw.zelda64player.randomizer.settings.SchemaOption

/** Alias to keep the row type self-documenting. */
typealias SchemaCategoryOption = SchemaOption

/**
 * Stateless helper that flattens a [RandomizerSettingsSchema] into the row list
 * consumed by [SettingsOptionAdapter] and computes the starting position of
 * each category (used to scroll the list when a tab is selected).
 *
 * The renderer itself holds no state; the form values live in the ViewModel and
 * are passed to the adapter separately.
 */
object SettingsFormRenderer {

    /** A single row in the flattened settings list. */
    sealed class SettingsRow {
        /** A non-interactive category title. */
        data class Header(val categoryId: String, val title: String) : SettingsRow()

        /** An interactive option row. */
        data class OptionRow(val option: SchemaCategoryOption) : SettingsRow()
    }

    /**
     * Build the flat row list: one [SettingsRow.Header] per category followed by
     * its [SettingsRow.OptionRow]s.
     *
     * @param schema The parsed schema.
     * @param titleFor Maps a category id to a display title (the caller resolves
     *   localized strings, falling back to the id).
     */
    fun buildRows(
        schema: RandomizerSettingsSchema,
        titleFor: (String) -> String
    ): List<SettingsRow> {
        val rows = mutableListOf<SettingsRow>()
        for (category in schema.categories) {
            rows += SettingsRow.Header(category.id, titleFor(category.id))
            for (option in category.options) {
                rows += SettingsRow.OptionRow(option)
            }
        }
        return rows
    }

    /**
     * Map of category id -> flat-list position of its header, for tab scrolling.
     */
    fun categoryPositions(schema: RandomizerSettingsSchema): Map<String, Int> {
        val positions = mutableMapOf<String, Int>()
        var index = 0
        for (category in schema.categories) {
            positions[category.id] = index
            index += 1 + category.options.size
        }
        return positions
    }
}
