/*
 * Zelda 64 Player - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package br.com.redclaw.zelda64player.drive

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.common.AccountPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * OAuth2 helper for the Google Drive backup feature.
 *
 * Uses the Drive [SCOPE] (`drive.file`) so the app can only access the files it
 * created in the user's Drive — the least-privilege scope for a backup tool. The
 * account is chosen through the system [AccountPicker]; the access token is
 * fetched lazily via [GoogleAuthUtil.getToken] (which transparently caches and
 * refreshes it). When the token is rejected (HTTP 401) callers should
 * [invalidateToken] and retry once.
 *
 * The Drive REST API itself is called elsewhere (see [GoogleDriveBackupService])
 * over plain HTTPS; this object only handles account selection and tokens.
 */
object GoogleDriveAuth {
    /** OAuth2 scope for the Drive REST API (file-only access). */
    const val SCOPE = "oauth2:https://www.googleapis.com/auth/drive.file"

    /**
     * Build the system account-chooser intent, or null when no Google accounts
     * are available on the device (the picker cannot show an empty list).
     */
    fun accountPickerIntent(): Intent? = runCatching {
        AccountPicker.newChooseAccountIntent(
            AccountPicker.AccountChooserOptions.Builder()
                .setAllowableAccountsTypes(listOf("com.google"))
                .build()
        )
    }.getOrNull()

    /** Resolve a stored account name to an [Account], or null if it vanished. */
    fun getAccount(context: Context, name: String): Account? =
        AccountManager.get(context).getAccountsByType("com.google")
            .firstOrNull { it.name == name }

    /**
     * Fetch a fresh access token for [account]. Suspends while the (blocking)
     * [GoogleAuthUtil.getToken] network call runs on the IO dispatcher.
     *
     * @throws UserRecoverableAuthException when the user must grant consent in a
     *   separate activity; the caller should launch [UserRecoverableAuthException.intent].
     * @throws GoogleAuthException on other auth failures (no network, etc.).
     */
    suspend fun getToken(context: Context, account: Account): String =
        withContext(Dispatchers.IO) {
            val token: String? = GoogleAuthUtil.getToken(context, account, SCOPE)
            token ?: throw GoogleAuthException("Empty Drive token")
        }

    /** Invalidate a cached token so the next [getToken] returns a fresh one. */
    @Suppress("DEPRECATION")
    fun invalidateToken(context: Context, token: String) {
        runCatching { GoogleAuthUtil.invalidateToken(context, token) }
    }
}
