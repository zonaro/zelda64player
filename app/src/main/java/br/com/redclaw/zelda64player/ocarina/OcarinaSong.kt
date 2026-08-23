package br.com.redclaw.zelda64player.ocarina

import android.content.Context
import androidx.annotation.StringRes
import org.json.JSONArray
import org.json.JSONObject

/**
 * A playable Ocarina song: an ordered list of [OcarinaNote]s.
 *
 * Built-in songs carry a localized [nameRes]; catalog-provided songs carry a raw
 * [rawName] taken verbatim from JSON (per AGENTS.md i18n exception: catalog song
 * names are displayed as-is). [displayName] resolves the correct label at runtime.
 */
data class OcarinaSong(
    val id: String,
    @StringRes val nameRes: Int = 0,
    val rawName: String? = null,
    val notes: List<OcarinaNote>
) {
    /** Resolve the user-visible name (localized for built-ins, raw for catalog). */
    fun displayName(context: Context): String =
        if (nameRes != 0) context.getString(nameRes) else (rawName ?: id)

    /** Serialize to the catalog JSON shape: {"id","name","notes":[...]}. */
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", rawName ?: id)
        put("notes", JSONArray(notes.map { it.name }))
    }

    companion object {
        /**
         * Parse a catalog song object tolerantly. Returns null when the entry is
         * malformed (missing id/name/notes, or any note code unrecognized) so the
         * caller can skip it without breaking the rest of the list (runCatching
         * style, matching [br.com.redclaw.zelda64player.data.model.HackCatalog]).
         */
        fun fromCatalogJson(obj: JSONObject): OcarinaSong? = runCatching {
            val id = obj.getString("id")
            val name = obj.getString("name")
            val notesArr = obj.getJSONArray("notes")
            val notes = mutableListOf<OcarinaNote>()
            for (i in 0 until notesArr.length()) {
                // An unrecognized note code invalidates the whole song.
                val note = OcarinaNote.fromCode(notesArr.getString(i)) ?: return null
                notes.add(note)
            }
            if (notes.isEmpty()) return null
            OcarinaSong(id = id, rawName = name, notes = notes)
        }.getOrNull()
    }
}
