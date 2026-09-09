package br.com.redclaw.zelda64player.retroachievements.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.GeneralSecurityException

/**
 * Secure, encrypted storage for RetroAchievements credentials.
 *
 * Stores the username and the API token issued by a successful login. The token is functionally a
 * password: it is persisted via [EncryptedSharedPreferences] (AES256 master key in the Android
 * Keystore), never logged, never included in backups, and sanitized as `***` anywhere it could
 * surface. Mirrors a standard encrypted-credential pattern for sensitive tokens.
 *
 * @param context Application context used to open the encrypted preferences.
 */
class RaCredentialStore(private val context: Context) {

    private val masterKey by lazy {
        MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
    }

    private val prefs by lazy {
        try {
            EncryptedSharedPreferences.create(
                    context.applicationContext,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: GeneralSecurityException) {
            throw RaCredentialStoreException("Unable to initialize RA secure storage", e)
        } catch (e: Exception) {
            throw RaCredentialStoreException("Unable to initialize RA secure storage", e)
        }
    }

    /** Returns the stored username, or `null` if the user never logged in. */
    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)

    /** Returns the stored login token, or `null`. Treat as secret: never log or display. */
    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    /**
     * Returns the stored RetroAchievements Web API key, or `null`.
     *
     * This is the key from https://retroachievements.org/controlpanel.php used as the `y` query
     * parameter of the Web API. It is distinct from the rcheevos login token ([getToken]) and is
     * required by the profile screen. Treat as secret: never log or display.
     */
    fun getApiKey(): String? = prefs.getString(KEY_API_KEY, null)

    /** Persists credentials after a successful login. */
    fun setCredentials(username: String, token: String) {
        prefs.edit().putString(KEY_USERNAME, username).putString(KEY_TOKEN, token).apply()
    }

    /** Persists the Web API key (see [getApiKey]). */
    fun setApiKey(apiKey: String) {
        prefs.edit().putString(KEY_API_KEY, apiKey).apply()
    }

    /** True when both username and token are stored. */
    fun hasCredentials(): Boolean = !getUsername().isNullOrBlank() && !getToken().isNullOrBlank()

    /** True when a Web API key is stored (profile screen can fetch). */
    fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()

    /** Removes stored credentials (logout). */
    fun clear() {
        prefs.edit().remove(KEY_USERNAME).remove(KEY_TOKEN).remove(KEY_API_KEY).apply()
    }

    companion object {
        const val PREFS_FILE = "ra_secure_prefs"
        const val KEY_USERNAME = "pref_ra_username"
        const val KEY_TOKEN = "pref_ra_token"
        const val KEY_API_KEY = "pref_ra_api_key"

        /** Placeholder used whenever credentials must appear in logs. */
        const val MASK = "***"
    }
}

/**
 * Raised when the encrypted preferences cannot be initialized (e.g. Keystore unavailable). Never
 * includes credential material in the message.
 */
class RaCredentialStoreException(message: String, cause: Throwable? = null) :
        Exception(message, cause)
