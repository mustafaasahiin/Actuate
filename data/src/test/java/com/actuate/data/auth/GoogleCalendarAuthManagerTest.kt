package com.actuate.data.auth

import android.content.Context
import android.text.TextUtils
import com.actuate.domain.repository.SecretStore
import com.google.android.gms.auth.api.signin.GoogleSignIn
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoogleCalendarAuthManagerTest {

    private lateinit var context: Context
    private lateinit var secretStore: InMemorySecretStore
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var authManager: GoogleCalendarAuthManager

    @Before
    fun setUp() {
        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any()) } answers {
            val s = firstArg<CharSequence?>()
            s.isNullOrEmpty()
        }
        mockkStatic(GoogleSignIn::class)
        every { GoogleSignIn.getLastSignedInAccount(any()) } returns null

        context = mockk(relaxed = true)
        secretStore = InMemorySecretStore()
        authManager = GoogleCalendarAuthManager(
            context = context,
            secretStore = secretStore,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun calendarScopeMatchesExpectedEventsUrl() {
        assertEquals("https://www.googleapis.com/auth/calendar.events", authManager.CALENDAR_SCOPE)
        assertEquals("https://www.googleapis.com/auth/calendar.events", GoogleCalendarAuthManager.CALENDAR_SCOPE)
    }

    @Test
    fun handleSignInResultNullReturnsFailure() {
        val result = authManager.handleSignInResult(null)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun getConnectedEmailReturnsStoredEmail() {
        assertNull(authManager.getConnectedEmail())
        secretStore.save(GoogleCalendarAuthManager.KEY_GOOGLE_CALENDAR_EMAIL, "user@example.com")
        assertEquals("user@example.com", authManager.getConnectedEmail())
    }

    @Test
    fun isConnectedReturnsTrueWhenEmailInSecretStore() {
        assertFalse(authManager.isConnected())
        secretStore.save(GoogleCalendarAuthManager.KEY_GOOGLE_CALENDAR_EMAIL, "user@example.com")
        assertTrue(authManager.isConnected())
    }

    @Test
    fun disconnectClearsAllStoredCredentials() {
        secretStore.save(GoogleCalendarAuthManager.KEY_GOOGLE_CALENDAR_EMAIL, "user@example.com")
        secretStore.save(GoogleCalendarAuthManager.KEY_GOOGLE_CALENDAR_TOKEN, "sample_token")
        secretStore.save(GoogleCalendarAuthManager.KEY_GOOGLE_CALENDAR_CONNECTED, "true")

        authManager.disconnect()

        assertNull(secretStore.read(GoogleCalendarAuthManager.KEY_GOOGLE_CALENDAR_EMAIL))
        assertNull(secretStore.read(GoogleCalendarAuthManager.KEY_GOOGLE_CALENDAR_TOKEN))
        assertNull(secretStore.read(GoogleCalendarAuthManager.KEY_GOOGLE_CALENDAR_CONNECTED))
        assertFalse(authManager.isConnected())
    }

    @Test
    fun getAccessTokenReturnsFailureWhenNotConnected() = runTest(testDispatcher) {
        val result = authManager.getAccessToken()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun getAccessTokenReturnsCachedTokenWhenAvailable() = runTest(testDispatcher) {
        secretStore.save(GoogleCalendarAuthManager.KEY_GOOGLE_CALENDAR_EMAIL, "user@example.com")
        secretStore.save(GoogleCalendarAuthManager.KEY_GOOGLE_CALENDAR_TOKEN, "cached_oauth_token")

        val result = authManager.getAccessToken(forceRefresh = false)
        assertTrue(result.isSuccess)
        assertEquals("cached_oauth_token", result.getOrNull())
    }

    private class InMemorySecretStore : SecretStore {
        private val storage = mutableMapOf<String, String>()

        override fun save(key: String, value: String) {
            storage[key] = value
        }

        override fun read(key: String): String? = storage[key]

        override fun delete(key: String) {
            storage.remove(key)
        }
    }
}
