package br.com.redclaw.zelda64player.settings

import android.content.SharedPreferences

/** Android [SharedPreferences]-backed implementation of [KeyValueStore]. */
class SharedPreferencesStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun getString(key: String, default: String?): String? = prefs.getString(key, default)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}
