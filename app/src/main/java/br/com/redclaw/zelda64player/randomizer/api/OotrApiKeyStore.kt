package br.com.redclaw.zelda64player.randomizer.api

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.GeneralSecurityException

/**
 * Secure, encrypted storage for the user's OoTR API key.
 *
 * The key is persisted via [EncryptedSharedPreferences] (AES256 master key held
 * in the Android Keystore) so it never lands on disk in plaintext. The key
 * value is **never** returned in logs; callers must treat [getKey] results as
 * secret and avoid printing them.
 *
 * @param context Application context used to open the encrypted preferences.
 */
class OotrApiKeyStore(private val context: Context) {

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
            throw OotrApiKeyStoreException("Unable to initialize secure storage", e)
        } catch (e: Exception) {
            throw OotrApiKeyStoreException("Unable to initialize secure storage", e)
        }
    }

    /** Returns the stored API key, or `null` if none is configured. */
    fun getKey(): String? = prefs.getString(KEY, null)

    /** Persists the API key. */
    fun setKey(value: String) {
        prefs.edit().putString(KEY, value).apply()
    }

    /** True when a non-blank API key is stored. */
    fun hasKey(): Boolean = !getKey().isNullOrBlank()

    /** Removes the stored API key. */
    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    companion object {
        const val PREFS_FILE = "ootr_secure_prefs"
        const val KEY = "pref_ootr_api_key"
    }
}

/**
 * Raised when the encrypted preferences cannot be initialized (e.g. Keystore
 * unavailable). Surfaces a safe message that never includes the API key.
 */
class OotrApiKeyStoreException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
