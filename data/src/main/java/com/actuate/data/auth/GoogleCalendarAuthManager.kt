package com.actuate.data.auth

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.actuate.domain.repository.SecretStore
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class GoogleCalendarAuthManager(
    private val context: Context,
    private val secretStore: SecretStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    val CALENDAR_SCOPE: String get() = Companion.CALENDAR_SCOPE

    fun getSignInIntent(): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(CALENDAR_SCOPE))
            .build()
        return GoogleSignIn.getClient(context, gso).signInIntent
    }

    fun handleSignInResult(intent: Intent?): Result<String> {
        if (intent == null) {
            return Result.failure(IllegalArgumentException("Sign-in intent is null"))
        }
        return runCatching {
            val task = GoogleSignIn.getSignedInAccountFromIntent(intent)
            val account = task.getResult(ApiException::class.java)
                ?: throw IllegalStateException("GoogleSignInAccount is null")
            val email = account.email
                ?: throw IllegalStateException("Google account has no email")

            secretStore.save(KEY_GOOGLE_CALENDAR_EMAIL, email)
            secretStore.save(KEY_GOOGLE_CALENDAR_CONNECTED, "true")
            email
        }
    }

    suspend fun getAccessToken(forceRefresh: Boolean = false): Result<String> = withContext(ioDispatcher) {
        val email = getConnectedEmail()
            ?: return@withContext Result.failure(IllegalStateException("No Google account connected"))

        if (!forceRefresh) {
            val cached = secretStore.read(KEY_GOOGLE_CALENDAR_TOKEN)
            if (!cached.isNullOrBlank()) {
                return@withContext Result.success(cached)
            }
        } else {
            val oldToken = secretStore.read(KEY_GOOGLE_CALENDAR_TOKEN)
            if (!oldToken.isNullOrBlank()) {
                runCatching { GoogleAuthUtil.clearToken(context, oldToken) }
                secretStore.delete(KEY_GOOGLE_CALENDAR_TOKEN)
            }
        }

        val account = runCatching { GoogleSignIn.getLastSignedInAccount(context)?.account }.getOrNull()
            ?: runCatching { Account(email, "com.google") }.getOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Unable to resolve Google account for $email"))

        try {
            val token = GoogleAuthUtil.getToken(context, account, "oauth2:$CALENDAR_SCOPE")
            secretStore.save(KEY_GOOGLE_CALENDAR_TOKEN, token)
            Result.success(token)
        } catch (e: UserRecoverableAuthException) {
            Result.failure(e)
        } catch (e: GoogleAuthException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getAccessTokenBlocking(forceRefresh: Boolean = false): Result<String> =
        runBlocking(ioDispatcher) { getAccessToken(forceRefresh) }

    fun isConnected(): Boolean {
        val storedEmail = secretStore.read(KEY_GOOGLE_CALENDAR_EMAIL)
        if (!storedEmail.isNullOrBlank()) {
            return true
        }
        val lastAccount = runCatching { GoogleSignIn.getLastSignedInAccount(context) }.getOrNull() ?: return false
        return runCatching { GoogleSignIn.hasPermissions(lastAccount, Scope(CALENDAR_SCOPE)) }.getOrDefault(false)
    }

    fun getConnectedEmail(): String? {
        return secretStore.read(KEY_GOOGLE_CALENDAR_EMAIL)
            ?: runCatching { GoogleSignIn.getLastSignedInAccount(context)?.email }.getOrNull()
    }

    fun disconnect() {
        val token = secretStore.read(KEY_GOOGLE_CALENDAR_TOKEN)
        secretStore.delete(KEY_GOOGLE_CALENDAR_EMAIL)
        secretStore.delete(KEY_GOOGLE_CALENDAR_TOKEN)
        secretStore.delete(KEY_GOOGLE_CALENDAR_CONNECTED)

        runCatching {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(CALENDAR_SCOPE))
                .build()
            GoogleSignIn.getClient(context, gso).signOut()
        }

        if (!token.isNullOrBlank()) {
            CoroutineScope(ioDispatcher).launch {
                runCatching { GoogleAuthUtil.clearToken(context, token) }
            }
        }
    }

    companion object {
        const val CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar.events"
        const val KEY_GOOGLE_CALENDAR_EMAIL = "google_calendar_account_email"
        const val KEY_GOOGLE_CALENDAR_TOKEN = "google_calendar_access_token"
        const val KEY_GOOGLE_CALENDAR_CONNECTED = "google_calendar_connected"
    }
}
