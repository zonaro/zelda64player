package br.com.redclaw.zelda64player.randomizer.settings

import android.content.Context
import java.io.IOException

/**
 * Loads and parses the bundled OoTR settings schema asset
 * ([RANDOMIZER_SCHEMA_ASSET]) exactly once and caches the parsed result.
 *
 * The schema is static per build (a few hundred entries, ~50KB), so a single
 * in-memory cache is sufficient. The loader is constructed with the application
 * context and is safe to share across the Randomizer feature.
 *
 * @param context Application context used to open the asset stream.
 */
class SchemaLoader(private val context: Context) {
    private var cached: RandomizerSettingsSchema? = null

    /**
     * Returns the parsed schema, loading and caching it on first call.
     *
     * @throws IOException If the asset cannot be opened or is malformed.
     */
    @Throws(IOException::class)
    fun load(): RandomizerSettingsSchema {
        cached?.let { return it }
        val json = context.assets.open(RANDOMIZER_SCHEMA_ASSET).bufferedReader().use { it.readText() }
        val schema = parseRandomizerSchema(json)
        cached = schema
        return schema
    }

    /** Drops the cache so the next [load] re-reads the asset. */
    fun invalidate() {
        cached = null
    }
}
