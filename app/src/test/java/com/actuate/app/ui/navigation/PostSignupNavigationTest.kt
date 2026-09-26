package com.actuate.app.ui.navigation

import android.content.Context
import android.content.SharedPreferences
import com.actuate.app.session.SessionBootstrapper
import com.actuate.app.session.SessionResult
import com.actuate.app.ui.home.HomeViewModel
import com.actuate.app.util.LocaleManager
import com.actuate.app.util.PermissionState
import com.actuate.app.util.SupportedLanguage
import com.actuate.domain.entitlement.EntitlementProvider
import com.actuate.domain.repository.AppSettingsRepository
import com.actuate.domain.repository.HistoryRepository
import com.actuate.domain.repository.QuotaRepository
import com.actuate.domain.repository.ServerSession
import com.actuate.domain.speech.SpeechTranscriber
import com.actuate.domain.usecase.ExecuteVoiceCommandUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PostSignupNavigationTest {

    private val transcriber: SpeechTranscriber = mockk(relaxed = true)
    private val executeCommand: ExecuteVoiceCommandUseCase = mockk(relaxed = true)
    private val settingsRepository: AppSettingsRepository = mockk(relaxed = true)
    private val quotaRepository: QuotaRepository = mockk(relaxed = true)
    private val entitlementProvider: EntitlementProvider = mockk(relaxed = true)
    private val historyRepository: HistoryRepository = mockk(relaxed = true)
    private val sessionBootstrapper: SessionBootstrapper = mockk(relaxed = true)
    private val permissionState: PermissionState = mockk(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()

    private fun createViewModel(context: Context? = null): HomeViewModel = HomeViewModel(
        transcriber = transcriber,
        executeCommand = executeCommand,
        settingsRepository = settingsRepository,
        quotaRepository = quotaRepository,
        entitlementProvider = entitlementProvider,
        historyRepository = historyRepository,
        sessionBootstrapper = sessionBootstrapper,
        permissionState = permissionState,
        context = context,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { entitlementProvider.observeIsPro() } returns flowOf(false)
        every { quotaRepository.observeRemaining() } returns flowOf(3)
        every { historyRepository.observeHistory() } returns flowOf(emptyList())
        every { permissionState.deviceId() } returns "device-123"
        coEvery { sessionBootstrapper.ensureSession(any()) } returns SessionResult.Connected
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun signUpSuccessSetsIsNewRegistrationTrueInAuthState() = runTest {
        val session = ServerSession("user-new", "token-new", false, 3)
        coEvery { sessionBootstrapper.signUp(any(), any(), any(), any()) } returns Result.success(session)

        val viewModel = createViewModel()
        assertFalse(viewModel.authState.value.succeeded)
        assertFalse(viewModel.authState.value.isNewRegistration)

        viewModel.signUp("newuser@example.com", "Password123!", "New User")

        assertTrue("Expected authState.succeeded to be true", viewModel.authState.value.succeeded)
        assertTrue("Expected authState.isNewRegistration to be true for new user signup", viewModel.authState.value.isNewRegistration)
        assertFalse("Expected authState.submitting to be false after completion", viewModel.authState.value.submitting)
        assertNull("Expected authState.error to be null on success", viewModel.authState.value.error)
    }

    @Test
    fun logInSuccessSetsIsNewRegistrationFalseInAuthState() = runTest {
        val session = ServerSession("user-existing", "token-existing", false, 3)
        coEvery { sessionBootstrapper.logIn(any(), any(), any()) } returns Result.success(session)

        val viewModel = createViewModel()
        assertFalse(viewModel.authState.value.succeeded)
        assertFalse(viewModel.authState.value.isNewRegistration)

        viewModel.logIn("existinguser@example.com", "Password123!")

        assertTrue("Expected authState.succeeded to be true", viewModel.authState.value.succeeded)
        assertFalse("Expected authState.isNewRegistration to be false for existing user login", viewModel.authState.value.isNewRegistration)
        assertFalse("Expected authState.submitting to be false after completion", viewModel.authState.value.submitting)
        assertNull("Expected authState.error to be null on success", viewModel.authState.value.error)
    }

    @Test
    fun consumeAuthSuccessResetsAuthState() = runTest {
        val session = ServerSession("user-new", "token-new", false, 3)
        coEvery { sessionBootstrapper.signUp(any(), any(), any(), any()) } returns Result.success(session)

        val viewModel = createViewModel()
        viewModel.signUp("newuser@example.com", "Password123!")

        assertTrue(viewModel.authState.value.succeeded)
        assertTrue(viewModel.authState.value.isNewRegistration)

        viewModel.consumeAuthSuccess()

        assertFalse("Expected authState.succeeded to be reset to false", viewModel.authState.value.succeeded)
        assertFalse("Expected authState.isNewRegistration to be reset to false", viewModel.authState.value.isNewRegistration)
        assertFalse("Expected authState.submitting to be reset to false", viewModel.authState.value.submitting)
        assertNull("Expected authState.error to be reset to null", viewModel.authState.value.error)
    }

    @Test
    fun localeManagerSetLanguageUpdatesCurrentLanguageAndPreferences() {
        val prefs = FakeSharedPreferences()
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns prefs

        val manager = LocaleManager(context)

        manager.setLanguage(SupportedLanguage.ENGLISH)
        assertEquals(SupportedLanguage.ENGLISH, manager.currentLanguage.value)
        assertEquals("en", prefs.getString(LocaleManager.KEY_LANGUAGE, null))
        assertFalse(manager.showLanguagePicker.value)
    }

    @Test
    fun routesDefinesLanguageSelectionRoute() {
        assertEquals("language_selection", Routes.LANGUAGE_SELECTION)
        assertEquals("home", Routes.HOME)
        assertEquals("signup", Routes.SIGNUP)
        assertEquals("login", Routes.LOGIN)
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun edit(): SharedPreferences.Editor = FakeEditor(data)

        private class FakeEditor(private val data: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) temp.remove(key)
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                temp.clear()
                return this
            }
            override fun commit(): Boolean {
                data.putAll(temp)
                return true
            }
            override fun apply() {
                data.putAll(temp)
            }
        }
    }
}
