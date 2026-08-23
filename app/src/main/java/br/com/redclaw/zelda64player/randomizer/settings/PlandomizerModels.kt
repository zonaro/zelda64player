package br.com.redclaw.zelda64player.randomizer.settings

import org.json.JSONArray
import org.json.JSONObject

/**
 * Known dungeon names accepted by the OoTR Plandomizer `dungeons` section.
 *
 * Best-effort list derived from the OoTR source (`Plandomizer.py`). The builder
 * also allows free-text names, so this list only seeds the dropdown and is not
 * used for hard validation (unknown names produce a warning, not an error).
 */
val PLANDOMIZER_DUNGEON_NAMES: List<String> = listOf(
    "Deku Tree",
    "Dodongos Cavern",
    "Jabu Jabus Belly",
    "Forest Temple",
    "Fire Temple",
    "Water Temple",
    "Spirit Temple",
    "Shadow Temple",
    "Bottom of the Well",
    "Ice Cavern",
    "Gerudo Training Ground",
    "Ganons Castle"
)

/**
 * Known trial names accepted by the OoTR Plandomizer `trials` section.
 */
val PLANDOMIZER_TRIAL_NAMES: List<String> = listOf(
    "Forest Trial",
    "Fire Trial",
    "Water Trial",
    "Shadow Trial",
    "Spirit Trial",
    "Light Trial"
)

/** Allowed values for the `dungeons` section. */
val PLANDOMIZER_DUNGEON_VALUES: List<String> = listOf("vanilla", "mq", "random")

/** Allowed values for the `trials` section. */
val PLANDOMIZER_TRIAL_VALUES: List<String> = listOf("active", "inactive", "random")

/** Allowed item-pool operation types. */
val PLANDOMIZER_ITEM_POOL_TYPES: List<String> = listOf("add", "remove", "set")

/**
 * A single `item_pool` entry.
 *
 * A plain count maps to the OoTR "set" semantics (the item count is set to
 * [count]). The `add` / `remove` operations serialize as the object form
 * `{"type": ..., "count": ...}`.
 *
 * @property type One of [PLANDOMIZER_ITEM_POOL_TYPES].
 * @property count Target count for the operation.
 */
data class ItemPoolEntry(val type: String, val count: Int) {
    /** Serialize to the JSON value expected by the OoTR API. */
    fun toJsonValue(): Any = if (type == "set") {
        count
    } else {
        JSONObject().apply {
            put("type", type)
            put("count", count)
        }
    }

    companion object {
        /** Parse an `item_pool` value (number or `{"type","count"}` object). */
        fun fromJsonValue(value: Any?): ItemPoolEntry = when (value) {
            is Number -> ItemPoolEntry("set", value.toInt())
            is JSONObject -> ItemPoolEntry(
                value.optString("type", "set"),
                value.optInt("count", 0)
            )
            else -> ItemPoolEntry("set", 0)
        }
    }
}

/**
 * A parsed Plandomizer placement file (the OoTR distribution file).
 *
 * Every section is optional. The [raw] object preserves the entire original
 * JSON (including `:`-prefixed spoiler keys) so the file can be re-serialized
 * losslessly when needed. The typed fields are convenience accessors produced
 * by [fromJson] and re-emitted by [toJson].
 *
 * @property settings GUI setting overrides (name -> value).
 * @property randomizedSettings Settings left to the randomizer.
 * @property startingItems Item name -> count.
 * @property itemPool Item name -> typed operation.
 * @property dungeons Dungeon name -> vanilla/mq/random.
 * @property trials Trial name -> active/inactive/random.
 * @property entrances Entrance name -> target region/location (string or dict).
 * @property locations Location name -> item(s) (string, list or dict).
 * @property gossipStones Stone name -> text or dict.
 * @property customGroups Group name -> list of location/item names.
 * @property fileHash Up to 5 icon names (or nulls).
 * @property raw Full original JSON, including unknown and `:`-prefixed keys.
 */
data class PlandomizerFile(
    val settings: Map<String, Any?> = emptyMap(),
    val randomizedSettings: Map<String, Any?> = emptyMap(),
    val startingItems: Map<String, Int> = emptyMap(),
    val itemPool: Map<String, ItemPoolEntry> = emptyMap(),
    val dungeons: Map<String, String> = emptyMap(),
    val trials: Map<String, String> = emptyMap(),
    val entrances: Map<String, Any?> = emptyMap(),
    val locations: Map<String, Any?> = emptyMap(),
    val gossipStones: Map<String, Any?> = emptyMap(),
    val customGroups: Map<String, List<String>> = emptyMap(),
    val fileHash: List<String?> = emptyList(),
    val raw: JSONObject? = null
) {
    /** Serialize the typed sections to a [JSONObject]. */
    fun toJson(): JSONObject {
        val obj = JSONObject()
        if (settings.isNotEmpty()) obj.put("settings", mapToJson(settings))
        if (randomizedSettings.isNotEmpty()) obj.put("randomized_settings", mapToJson(randomizedSettings))
        if (startingItems.isNotEmpty()) obj.put("starting_items", mapToJson(startingItems.mapValues { it.value }))
        if (itemPool.isNotEmpty()) {
            val jo = JSONObject()
            itemPool.forEach { (k, v) -> jo.put(k, v.toJsonValue()) }
            obj.put("item_pool", jo)
        }
        if (dungeons.isNotEmpty()) obj.put("dungeons", mapToJson(dungeons))
        if (trials.isNotEmpty()) obj.put("trials", mapToJson(trials))
        if (entrances.isNotEmpty()) obj.put("entrances", mapToJson(entrances))
        if (locations.isNotEmpty()) obj.put("locations", mapToJson(locations))
        if (gossipStones.isNotEmpty()) obj.put("gossip_stones", mapToJson(gossipStones))
        if (customGroups.isNotEmpty()) {
            val jo = JSONObject()
            customGroups.forEach { (k, v) -> jo.put(k, listToJsonArray(v)) }
            obj.put("custom_groups", jo)
        }
        if (fileHash.isNotEmpty()) {
            val arr = JSONArray()
            fileHash.forEach { arr.put(it ?: JSONObject.NULL) }
            obj.put("file_hash", arr)
        }
        return obj
    }

    companion object {
        /** Parse a [PlandomizerFile] from a placement [JSONObject]. */
        fun fromJson(json: JSONObject): PlandomizerFile {
            fun objSection(name: String): JSONObject? =
                json.opt(name)?.takeIf { it is JSONObject } as? JSONObject

            fun mapSection(name: String): Map<String, Any?> {
                val sec = objSection(name) ?: return emptyMap()
                val out = LinkedHashMap<String, Any?>()
                sec.keys().asSequence().forEach { out[it] = sec.get(it) }
                return out
            }

            val startingItems = LinkedHashMap<String, Int>()
            objSection("starting_items")?.keys()?.asSequence()?.forEach { k ->
                val v = objSection("starting_items")!!.get(k)
                startingItems[k] = (v as? Number)?.toInt() ?: 0
            }

            val itemPool = LinkedHashMap<String, ItemPoolEntry>()
            objSection("item_pool")?.keys()?.asSequence()?.forEach { k ->
                itemPool[k] = ItemPoolEntry.fromJsonValue(objSection("item_pool")!!.get(k))
            }

            val dungeons = LinkedHashMap<String, String>()
            objSection("dungeons")?.keys()?.asSequence()?.forEach { k ->
                dungeons[k] = objSection("dungeons")!!.optString(k, "")
            }

            val trials = LinkedHashMap<String, String>()
            objSection("trials")?.keys()?.asSequence()?.forEach { k ->
                trials[k] = objSection("trials")!!.optString(k, "")
            }

            val customGroups = LinkedHashMap<String, List<String>>()
            objSection("custom_groups")?.keys()?.asSequence()?.forEach { k ->
                val v = objSection("custom_groups")!!.get(k)
                customGroups[k] = if (v is JSONArray) {
                    (0 until v.length()).map { v.getString(it) }
                } else emptyList()
            }

            val fileHash = mutableListOf<String?>()
            json.opt("file_hash")?.takeIf { it is JSONArray }?.let { arr ->
                arr as JSONArray
                for (i in 0 until arr.length()) {
                    val e = arr.get(i)
                    fileHash.add(if (e === JSONObject.NULL) null else e.toString())
                }
            }

            return PlandomizerFile(
                settings = mapSection("settings"),
                randomizedSettings = mapSection("randomized_settings"),
                startingItems = startingItems,
                itemPool = itemPool,
                dungeons = dungeons,
                trials = trials,
                entrances = mapSection("entrances"),
                locations = mapSection("locations"),
                gossipStones = mapSection("gossip_stones"),
                customGroups = customGroups,
                fileHash = fileHash,
                raw = json
            )
        }
    }
}

/**
 * Form model backing the visual Plandomizer builder. Each section is a list of
 * simple rows so it can be rendered with `RecyclerView` adapters. The model is
 * the single editable representation inside the builder; the text editor tab
 * remains the source of truth for the final JSON string.
 */
data class PlandomizerBuilderState(
    val locations: List<PlandomizerRow> = emptyList(),
    val entrances: List<PlandomizerEntranceRow> = emptyList(),
    val dungeons: List<PlandomizerDungeonRow> = emptyList(),
    val trials: List<PlandomizerTrialRow> = emptyList(),
    val startingItems: List<PlandomizerRow> = emptyList(),
    val itemPool: List<PlandomizerItemPoolRow> = emptyList(),
    val settings: List<PlandomizerRow> = emptyList(),
    val fileHash: List<String> = List(5) { "" }
) {
    fun isEmpty(): Boolean =
        locations.isEmpty() && entrances.isEmpty() && dungeons.isEmpty() &&
            trials.isEmpty() && startingItems.isEmpty() && itemPool.isEmpty() &&
            settings.isEmpty() && fileHash.all { it.isBlank() }

    /** Convert the form model into a typed [PlandomizerFile]. */
    fun toPlandomizerFile(): PlandomizerFile = PlandomizerFile(
        settings = rowsToMap(settings),
        startingItems = startingItems.mapNotNull { (k, v) ->
            v.toIntOrNull()?.let { k to it }
        }.toMap(),
        itemPool = itemPool.map { (item, type, count) ->
            item to ItemPoolEntry(type, count.toIntOrNull() ?: 0)
        }.toMap(),
        dungeons = dungeons.map { (name, mode) -> name to mode }.toMap(),
        trials = trials.map { (name, mode) -> name to mode }.toMap(),
        entrances = rowsToMap(entrances.map { PlandomizerRow(it.from, it.to) }),
        locations = rowsToMap(locations),
        fileHash = fileHash.map { it.takeIf { s -> s.isNotBlank() } }
    )

    /** Serialize the builder form directly to a placement [JSONObject]. */
    fun toJson(): JSONObject = toPlandomizerFile().toJson()

    companion object {
        /** Parse a builder form model from a placement [JSONObject]. */
        fun fromJson(json: JSONObject): PlandomizerBuilderState {
            fun objSection(name: String): JSONObject? =
                json.opt(name)?.takeIf { it is JSONObject } as? JSONObject

            fun rowsFrom(name: String): List<PlandomizerRow> {
                val sec = objSection(name) ?: return emptyList()
                return sec.keys().asSequence().map { PlandomizerRow(it, sec.opt(it)?.toString() ?: "") }.toList()
            }

            val dungeons = objSection("dungeons")?.keys()?.asSequence()?.map {
                PlandomizerDungeonRow(it, objSection("dungeons")!!.optString(it, "vanilla"))
            }?.toList() ?: emptyList()

            val trials = objSection("trials")?.keys()?.asSequence()?.map {
                PlandomizerTrialRow(it, objSection("trials")!!.optString(it, "active"))
            }?.toList() ?: emptyList()

            val itemPool = objSection("item_pool")?.keys()?.asSequence()?.map { k ->
                val v = objSection("item_pool")!!.get(k)
                val entry = ItemPoolEntry.fromJsonValue(v)
                PlandomizerItemPoolRow(k, entry.type, entry.count.toString())
            }?.toList() ?: emptyList()

            val fileHash = mutableListOf<String>("", "", "", "", "")
            json.opt("file_hash")?.takeIf { it is JSONArray }?.let { arr ->
                arr as JSONArray
                for (i in 0 until minOf(arr.length(), 5)) {
                    val e = arr.get(i)
                    fileHash[i] = if (e === JSONObject.NULL) "" else e.toString()
                }
            }

            return PlandomizerBuilderState(
                locations = rowsFrom("locations"),
                entrances = objSection("entrances")?.keys()?.asSequence()?.map {
                    PlandomizerEntranceRow(it, objSection("entrances")!!.optString(it, ""))
                }?.toList() ?: emptyList(),
                dungeons = dungeons,
                trials = trials,
                startingItems = rowsFrom("starting_items"),
                itemPool = itemPool,
                settings = rowsFrom("settings"),
                fileHash = fileHash
            )
        }
    }
}

/** A simple key/value row used by several builder sections. */
data class PlandomizerRow(val key: String, val value: String)

/** An entrance row: from-region -> to-region. */
data class PlandomizerEntranceRow(val from: String, val to: String)

/** A dungeon row: name + mode (vanilla/mq/random). */
data class PlandomizerDungeonRow(val name: String, val mode: String)

/** A trial row: name + mode (active/inactive/random). */
data class PlandomizerTrialRow(val name: String, val mode: String)

/** An item-pool row: item + operation type + count. */
data class PlandomizerItemPoolRow(val item: String, val type: String, val count: String)

/** Convert a list of [PlandomizerRow] into a key->value map. */
private fun rowsToMap(rows: List<PlandomizerRow>): Map<String, Any?> =
    rows.filter { it.key.isNotBlank() }.associate { it.key to it.value }

/** Convert an arbitrary Kotlin value into a JSON-compatible value. */
internal fun jsonValueOf(value: Any?): Any? = when (value) {
    null -> JSONObject.NULL
    is JSONObject, is JSONArray, is String, is Number, is Boolean -> value
    is Map<*, *> -> JSONObject().also { o ->
        value.forEach { (k, v) -> o.put(k.toString(), jsonValueOf(v)) }
    }
    is List<*> -> JSONArray().also { a -> value.forEach { a.put(jsonValueOf(it)) } }
    else -> value.toString()
}

/** Build a [JSONObject] from a string-keyed map. */
internal fun mapToJson(map: Map<String, Any?>): JSONObject =
    JSONObject().also { o -> map.forEach { (k, v) -> o.put(k, jsonValueOf(v)) } }

/** Build a [JSONArray] from a list of strings. */
internal fun listToJsonArray(list: List<String>): JSONArray =
    JSONArray().also { a -> list.forEach { a.put(it) } }
