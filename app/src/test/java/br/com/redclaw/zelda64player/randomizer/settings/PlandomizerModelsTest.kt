package br.com.redclaw.zelda64player.randomizer.settings

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [PlandomizerBuilderState] and [PlandomizerFile] serialization:
 * builder form <-> JSON round-trips and typed-model <-> JSON round-trips.
 */
class PlandomizerModelsTest {

    private fun sampleState(): PlandomizerBuilderState = PlandomizerBuilderState(
        locations = listOf(PlandomizerRow("Loc A", "Item X")),
        entrances = listOf(PlandomizerEntranceRow("Link's House", "Kokiri Forest")),
        dungeons = listOf(PlandomizerDungeonRow("Deku Tree", "mq")),
        trials = listOf(PlandomizerTrialRow("Fire Trial", "inactive")),
        startingItems = listOf(PlandomizerRow("Recovery Heart", "5")),
        itemPool = listOf(PlandomizerItemPoolRow("Rupee", "add", "10")),
        settings = listOf(PlandomizerRow("logic_rules", "standard")),
        fileHash = listOf("a", "b", "", "", "")
    )

    @Test
    fun builderStateRoundTrips() {
        val state = sampleState()
        val json = state.toJson()
        val parsed = PlandomizerBuilderState.fromJson(json)
        assertEquals(state, parsed)
    }

    @Test
    fun builderStateEmptyRoundTrips() {
        val state = PlandomizerBuilderState()
        val json = state.toJson()
        val parsed = PlandomizerBuilderState.fromJson(json)
        assertEquals(state, parsed)
    }

    @Test
    fun itemPoolSetSerializesAsPlainNumber() {
        val state = PlandomizerBuilderState(itemPool = listOf(PlandomizerItemPoolRow("Rupee", "set", "3")))
        val json = state.toJson()
        val pool = json.getJSONObject("item_pool")
        // "set" mode serializes as a plain integer, not an object.
        assertEquals(3, pool.getInt("Rupee"))
    }

    @Test
    fun itemPoolAddSerializesAsObject() {
        val state = PlandomizerBuilderState(itemPool = listOf(PlandomizerItemPoolRow("Rupee", "add", "10")))
        val json = state.toJson()
        val pool = json.getJSONObject("item_pool")
        val entry = pool.getJSONObject("Rupee")
        assertEquals("add", entry.getString("type"))
        assertEquals(10, entry.getInt("count"))
    }

    @Test
    fun typedFileRoundTrips() {
        val file = sampleState().toPlandomizerFile()
        val json = file.toJson()
        val parsed = PlandomizerFile.fromJson(json)
        assertEquals(file.startingItems, parsed.startingItems)
        assertEquals(file.itemPool, parsed.itemPool)
        assertEquals(file.dungeons, parsed.dungeons)
        assertEquals(file.trials, parsed.trials)
        assertEquals(file.locations, parsed.locations)
        assertEquals(file.entrances, parsed.entrances)
        assertEquals(file.settings, parsed.settings)
        assertEquals(file.fileHash, parsed.fileHash)
    }

    @Test
    fun fileHashBlanksBecomeNullsThenEmptyOnParse() {
        val state = PlandomizerBuilderState(fileHash = listOf("a", "b", "", "", ""))
        val json = state.toJson()
        val arr = json.getJSONArray("file_hash")
        assertEquals(JSONObject.NULL, arr.get(2))
        val parsed = PlandomizerBuilderState.fromJson(json)
        assertEquals(listOf("a", "b", "", "", ""), parsed.fileHash)
    }

    @Test
    fun fromJsonPreservesRaw() {
        val json = JSONObject().put("settings", JSONObject().put("x", "y"))
        val file = PlandomizerFile.fromJson(json)
        assertEquals(json.toString(), file.raw?.toString())
    }
}
