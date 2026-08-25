package br.com.redclaw.zelda64player.utils

import android.content.Context
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import br.com.redclaw.zelda64player.R
import java.util.Locale

/**
 * Single source of truth for the app-wide display language. Persists the user
 * choice in the shared [PREFS_NAME] and applies it via AppCompatDelegate so the
 * selection propagates to every AppCompatActivity without touching each
 * activity's attachBaseContext.
 *
 * Fallback strategy: every explicit choice is paired with English as a fallback
 * locale (LocaleListCompat([chosen, ENGLISH])). For "Auto" we follow the real
 * device locale (Resources.getSystem, which is unaffected by app overrides) and
 * still keep English as a fallback before the default (pt-BR) resources.
 */
object LanguageManager {
    private const val PREFS_NAME = "ludere_prefs"
    private const val KEY_LANGUAGE = "pref_language"

    const val AUTO = "auto"
    const val PT = "pt"
    const val EN = "en"
    const val ES = "es"

    /** Ordered selectable codes; Auto is first and is the default. */
    val CODES = listOf(AUTO, PT, EN, ES)

    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, AUTO) ?: AUTO
    }

    /** Persists the choice and applies it (triggers activity recreation). */
    fun setLanguage(context: Context, code: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANGUAGE, code).apply()
        applyLanguage(code)
    }

    /**
     * Builds a LocaleList with English as the fallback locale and applies it via
     * AppCompatDelegate so the choice propagates to every AppCompatActivity.
     * For Auto we follow the real device locale (Resources.getSystem, which is
     * unaffected by app overrides) and still keep English as a fallback.
     */
    fun applyLanguage(code: String) {
        val locales = when (code) {
            PT -> LocaleListCompat.create(Locale("pt", "BR"), Locale.ENGLISH)
            EN -> LocaleListCompat.create(Locale.ENGLISH)
            ES -> LocaleListCompat.create(Locale("es"), Locale.ENGLISH)
            else -> {
                val system = Resources.getSystem().configuration.locales.get(0) ?: Locale.getDefault()
                LocaleListCompat.create(system, Locale.ENGLISH)
            }
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /** Re-applies the persisted choice at application startup. */
    fun applyAtStartup(context: Context) {
        applyLanguage(getLanguage(context))
    }

    /** Localized label for a code (used to display the current selection). */
    fun labelFor(context: Context, code: String): String {
        val resId = when (code) {
            PT -> R.string.language_portuguese
            EN -> R.string.language_english
            ES -> R.string.language_spanish
            else -> R.string.language_auto
        }
        return context.getString(resId)
    }
}
