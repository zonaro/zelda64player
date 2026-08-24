package br.com.redclaw.zelda64player.retroachievements.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.GeneralSecurityException

/**
 * Secure, encrypted storage for RetroAchievements credentials.
 *
 * Stores the username and the API token issued by a successful login. The
 * token is functionally a password: it is persisted via
 * [EncryptedSharedPreferences] (AES256 master key in the Android Keystore),
 * never logged, never included in backups, and sanitized as `***` anywhere it
 * could surface. Mirrors the encrypted-key pattern previously used by the
 * OoT Randomizer API key store (now removed in favor of the WebView generator).
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

    /**
     * Returns the stored login token, or `null`. Treat as secret:
     * never log or display.
     */
    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    /** Persists credentials after a successful login. */
    fun setCredentials(username: String, token: String) {
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_TOKEN, token)
            .apply()
    }

    /** True when both username and token are stored. */
    fun hasCredentials(): Boolean =
        !getUsername().isNullOrBlank() && !getToken().isNullOrBlank()

    /** Removes stored credentials (logout). */
    fun clear() {
        prefs.edit().remove(KEY_USERNAME).remove(KEY_TOKEN).apply()
    }

    companion object {
        const val PREFS_FILE = "ra_secure_prefs"
        const val KEY_USERNAME = "pref_ra_username"
        const val KEY_TOKEN = "pref_ra_token"

        /** Placeholder used whenever credentials must appear in logs. */
        const val MASK = "***"
    }
}

/**
 * Raised when the encrypted preferences cannot be initialized (e.g. Keystore
 * unavailable). Never includes credential material in the message.
 */
class RaCredentialStoreException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
