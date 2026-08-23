package br.com.redclaw.zelda64player.data.model

/**
 * An N64 base ROM imported by the user (e.g. a legally-obtained Ocarina of Time
 * or Majora's Mask ROM). The [crc32] is computed over the normalized big-endian
 * `.z64` bytes and is the key used to match a hack's required base ROM.
 *
 * @property id stable identifier (the normalized CRC32 hex, which is unique per ROM)
 * @property displayName user-facing name, derived from the ROM header title or filename
 * @property path absolute path to the normalized `.z64` copy stored in the app cache
 * @property gameCode four-character N64 game code (e.g. `CZLE`, `NSME`)
 * @property versionByte header version byte (0 = v1.0)
 * @property sizeBytes size of the normalized ROM in bytes
 * @property crc32 required CRC32 (lowercase hex) of the normalized ROM
 * @property md5 optional MD5 (lowercase hex) for extra validation
 * @property sha1 optional SHA-1 (lowercase hex) for extra validation
 */
data class BaseRom(
    val id: String,
    val displayName: String,
    val path: String,
    val gameCode: String,
    val versionByte: Int,
    val sizeBytes: Long,
    val crc32: String,
    val md5: String?,
    val sha1: String?
) {
    fun toJson(): org.json.JSONObject = org.json.JSONObject().apply {
        put("id", id)
        put("displayName", displayName)
        put("path", path)
        put("gameCode", gameCode)
        put("versionByte", versionByte)
        put("sizeBytes", sizeBytes)
        put("crc32", crc32)
        put("md5", md5)
        put("sha1", sha1)
    }

    companion object {
        fun fromJson(o: org.json.JSONObject): BaseRom = BaseRom(
            id = o.getString("id"),
            displayName = o.getString("displayName"),
            path = o.getString("path"),
            gameCode = o.getString("gameCode"),
            versionByte = o.getInt("versionByte"),
            sizeBytes = o.getLong("sizeBytes"),
            crc32 = o.getString("crc32"),
            md5 = if (o.isNull("md5")) null else o.getString("md5"),
            sha1 = if (o.isNull("sha1")) null else o.getString("sha1")
        )
    }
}
