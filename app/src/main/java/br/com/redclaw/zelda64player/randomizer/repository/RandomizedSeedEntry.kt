package br.com.redclaw.zelda64player.randomizer.repository

import org.json.JSONObject

/**
 * A generated OoTRandomizer seed that is ready to play, persisted so it survives
 * process death and shows up in the Library "Randomizadores" section.
 *
 * The [id] is stable and unique (`ootr_` + a short random token) and is also used
 * as the hack id when launching the seed through the normal [br.com.redclaw.zelda64player
 * .views.GameActivity] route, so [br.com.redclaw.zelda64player.repositories.Storage]
 * resolves its ROM/SRAM/state files via `rom_<id>` / `sram_<id>` / `state_<id>`.
 *
 * JSON-serializable with `org.json` (matching [br.com.redclaw.zelda64player.data
 * .local.InstalledHacksRepository] style) so the whole index is a single array
 * file under `filesDir/randomizer/seeds.json`.
 */
data class RandomizedSeedEntry(
    /** Stable unique id, also used as the launch hack id. */
    val id: String,
    /** User-facing seed name (required at generation time). */
    val name: String,
    /** Server-assigned OoTR seed id (used for support / reference). */
    val ootrSeedId: String,
    /** OoTR randomizer version that generated this seed. */
    val ootrVersion: String,
    /** Epoch millis when the seed was generated and persisted. */
    val createdAt: Long,
    /** Whether a Plandomizer placement was applied to this seed. */
    val hasPlandomizer: Boolean,
    /** ROM file name inside the repository's ROM directory (e.g. `rom_<id>`). */
    val romFileName: String,
    /** Human-readable label of the base ROM this seed was built from. */
    val baseRomLabel: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("ootrSeedId", ootrSeedId)
        put("ootrVersion", ootrVersion)
        put("createdAt", createdAt)
        put("hasPlandomizer", hasPlandomizer)
        put("romFileName", romFileName)
        put("baseRomLabel", baseRomLabel)
    }

    companion object {
        fun fromJson(o: JSONObject): RandomizedSeedEntry = RandomizedSeedEntry(
            id = o.getString("id"),
            name = o.getString("name"),
            ootrSeedId = o.getString("ootrSeedId"),
            ootrVersion = o.getString("ootrVersion"),
            createdAt = o.getLong("createdAt"),
            hasPlandomizer = o.getBoolean("hasPlandomizer"),
            romFileName = o.getString("romFileName"),
            baseRomLabel = o.getString("baseRomLabel")
        )
    }
}
