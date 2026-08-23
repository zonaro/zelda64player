package br.com.redclaw.zelda64player.settings

import org.json.JSONArray

/**
 * Pure, Android-free persistence of the user's custom catalog URLs.
 *
 * The list is stored as a JSON array string under a single key so it round-trips
 * cleanly and stays order-stable. A KeyValueStore abstraction is used so the
 * logic is unit-testable on the JVM with an in-memory backing (see
 * CatalogUrlStoreTest); on Android it is backed by android.content.SharedPreferences
 * via SharedPreferencesStore.
 *
 * Validation accepts only http or https URLs with a non-blank host, which is
 * enough to keep the store from fetching arbitrary (e.g. file) schemes.
 */
class CatalogUrlStore(private val store: KeyValueStore, private val key: String) {

    fun getUrls(): List<String> {
        val raw = store.getString(key, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optString(i).takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setUrls(urls: List<String>) {
        val arr = JSONArray()
        urls.forEach { arr.put(it) }
        store.putString(key, arr.toString())
    }

    /** Add url after validation + de-duplication. Returns false if invalid or duplicate. */
    fun addUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (!isValidUrl(trimmed)) return false
        val current = getUrls().toMutableList()
        if (current.any { it.equals(trimmed, ignoreCase = true) }) return false
        current.add(trimmed)
        setUrls(current)
        return true
    }

    fun removeUrl(url: String): Boolean {
        val current = getUrls().toMutableList()
        val removed = current.removeAll { it.equals(url, ignoreCase = true) }
        if (removed) setUrls(current)
        return removed
    }

    companion object {
        const val PREFS_NAME = "zelda64_settings"
        const val KEY = "catalog_urls"

        /** Accept only http/https URLs with a non-blank host. */
        fun isValidUrl(url: String): Boolean {
            val trimmed = url.trim()
            if (trimmed.isBlank()) return false
            val lower = trimmed.lowercase()
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
            val host = runCatching { java.net.URI(trimmed).host }.getOrNull()
            return !host.isNullOrBlank()
        }
    }
}

/** Minimal key/value abstraction so CatalogUrlStore is testable without Android. */
interface KeyValueStore {
    fun getString(key: String, default: String?): String?
    fun putString(key: String, value: String)
}
