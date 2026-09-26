package com.actuate.app.ui.home

import android.content.Context
import com.actuate.app.R
import com.actuate.app.session.SessionBootstrapper
import com.actuate.app.session.SessionResult
import com.actuate.app.util.PermissionState
import com.actuate.core.components.BubbleState
import com.actuate.domain.entitlement.EntitlementProvider
import com.actuate.domain.model.Destination
import com.actuate.domain.model.ExecutionResult
import com.actuate.domain.model.VoiceRunResult
import com.actuate.domain.repository.AppSettingsRepository
import com.actuate.domain.repository.HistoryRepository
import com.actuate.domain.repository.QuotaRepository
import com.actuate.domain.repository.ServerSession
import com.actuate.domain.speech.SpeechTranscriber
import com.actuate.domain.usecase.ExecuteVoiceCommandUseCase
import com.actuate.domain.usecase.QuotaExceededException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val transcriber: SpeechTranscriber = mockk(relaxed = true)
    private val executeCommand: ExecuteVoiceCommandUseCase = mockk()
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
    fun submitTextCommandExecutesViaUseCaseAndUpdatesState() = runTest {
        val command = "Add meeting with Priya tomorrow at 3 PM"
        val expectedResult = VoiceRunResult(
            transcript = command,
            executed = listOf(
                ExecutionResult(
                    actionId = "act-1",
                    destination = Destination.CALENDAR,
                    success = true,
                    message = "Added meeting with Priya",
                ),
            ),
            remainingQuota = 2,
        )
        coEvery { executeCommand.execute(command, any()) } returns expectedResult

        val viewModel = createViewModel()
        assertEquals(BubbleState.IDLE, viewModel.bubbleState.value)

        viewModel.submitTextCommand(command)

        coVerify(exactly = 1) { executeCommand.execute(command, any()) }
        assertEquals("1 executed", viewModel.statusText.value)
        assertEquals(BubbleState.IDLE, viewModel.bubbleState.value)
        assertEquals(1, viewModel.executionTick.value)
    }

    @Test
    fun submitTextCommandIgnoresBlankText() = runTest {
        val viewModel = createViewModel()

        viewModel.submitTextCommand("   ")

        coVerify(exactly = 0) { executeCommand.execute(any(), any()) }
        assertEquals(BubbleState.IDLE, viewModel.bubbleState.value)
        assertEquals(0, viewModel.executionTick.value)
    }

    @Test
    fun submitTextCommandUpdatesStatusOnFailure() = runTest {
        val command = "Put milk and eggs on shopping list"
        val failedResult = VoiceRunResult(
            transcript = command,
            executed = listOf(
                ExecutionResult(
                    actionId = "act-2",
                    destination = Destination.NOTION,
                    success = false,
                    message = "Failed to connect to Notion",
                ),
            ),
            remainingQuota = 3,
        )
        coEvery { executeCommand.execute(command, any()) } returns failedResult

        val viewModel = createViewModel()
        viewModel.submitTextCommand(command)

        coVerify(exactly = 1) { executeCommand.execute(command, any()) }
        assertEquals("0 done · 1 failed", viewModel.statusText.value)
        assertEquals(BubbleState.IDLE, viewModel.bubbleState.value)
        assertEquals(1, viewModel.executionTick.value)
    }

    @Test
    fun submitTextCommandHandlesExceptionGracefully() = runTest {
        val command = "Remind me to stretch at 5 PM"
        coEvery { executeCommand.execute(command, any()) } throws RuntimeException("Network timeout")

        val viewModel = createViewModel()
        viewModel.submitTextCommand(command)

        coVerify(exactly = 1) { executeCommand.execute(command, any()) }
        assertEquals(BubbleState.IDLE, viewModel.bubbleState.value)
    }

    @Test
    fun submitTextCommandEmitsQuotaExhaustedWhenQuotaExceededExceptionThrown() = runTest {
        val command = "Remind me to stretch at 5 PM"
        coEvery { executeCommand.execute(command, any()) } throws QuotaExceededException()

        val viewModel = createViewModel()
        val events = mutableListOf<UiEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.submitTextCommand(command)

        coVerify(exactly = 1) { executeCommand.execute(command, any()) }
        assertEquals(BubbleState.IDLE, viewModel.bubbleState.value)
        assertTrue("Expected QuotaExhausted event", events.any { it is UiEvent.QuotaExhausted })
        assertTrue("Expected Message event", events.any { it is UiEvent.Message && it.text.contains("20 actions per week") })
        job.cancel()
    }

    @Test
    fun initialShowOnboardingIsTrueWhenNotSeen() = runTest {
        every { settingsRepository.hasSeenOnboarding } returns flowOf(false)

        val viewModel = createViewModel()

        assertEquals(true, viewModel.showOnboarding.value)
    }

    @Test
    fun initialShowOnboardingIsFalseWhenAlreadySeen() = runTest {
        every { settingsRepository.hasSeenOnboarding } returns flowOf(true)

        val viewModel = createViewModel()

        assertEquals(false, viewModel.showOnboarding.value)
    }

    @Test
    fun dismissOnboardingPersistsFlagAndUpdatesState() = runTest {
        every { settingsRepository.hasSeenOnboarding } returns flowOf(false)

        val viewModel = createViewModel()
        viewModel.dismissOnboarding(requestPermissions = false)

        assertEquals(false, viewModel.showOnboarding.value)
        coVerify(exactly = 1) { settingsRepository.setHasSeenOnboarding(true) }
    }

    @Test
    fun dismissOnboardingWithRequestPermissionsEmitsPermissionEvents() = runTest {
        every { settingsRepository.hasSeenOnboarding } returns flowOf(false)
        every { permissionState.hasMicrophone() } returns false
        every { permissionState.hasNotifications() } returns false

        val viewModel = createViewModel()
        viewModel.dismissOnboarding(requestPermissions = true)

        assertEquals(false, viewModel.showOnboarding.value)
        coVerify(exactly = 1) { settingsRepository.setHasSeenOnboarding(true) }
    }

    @Test
    fun showOnboardingSetsShowOnboardingToTrue() = runTest {
        every { settingsRepository.hasSeenOnboarding } returns flowOf(true)

        val viewModel = createViewModel()
        assertEquals(false, viewModel.showOnboarding.value)

        viewModel.showOnboarding()
        assertEquals(true, viewModel.showOnboarding.value)
    }

    @Test
    fun signUpSuccessEmitsMessageRes() = runTest {
        val session = ServerSession("user-1", "token-1", false, 3)
        coEvery { sessionBootstrapper.signUp(any(), any(), any(), any()) } returns Result.success(session)

        val viewModel = createViewModel()
        val events = mutableListOf<UiEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.signUp("test@example.com", "Password123!")

        assertTrue(viewModel.authState.value.succeeded)
        assertTrue(viewModel.authState.value.isNewRegistration)
        assertEquals("Connected", viewModel.sessionStatus.value)
        assertTrue(events.any { it is UiEvent.MessageRes && it.resId == R.string.account_created_welcome })
        job.cancel()
    }

    @Test
    fun logInSuccessEmitsMessageRes() = runTest {
        val session = ServerSession("user-1", "token-1", false, 3)
        coEvery { sessionBootstrapper.logIn(any(), any(), any()) } returns Result.success(session)

        val viewModel = createViewModel()
        val events = mutableListOf<UiEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.logIn("test@example.com", "Password123!")

        assertTrue(viewModel.authState.value.succeeded)
        assertFalse(viewModel.authState.value.isNewRegistration)
        assertEquals("Connected", viewModel.sessionStatus.value)
        assertTrue(events.any { it is UiEvent.MessageRes && it.resId == R.string.welcome_back })
        job.cancel()
    }

    @Test
    fun friendlyAuthErrorMapsLocalizedStringsWhenContextProvided() = runTest {
        val mockContext: Context = mockk()
        every { mockContext.getString(R.string.auth_error_email_exists) } returns "Email already registered"
        every { mockContext.getString(R.string.auth_error_invalid_credentials) } returns "Invalid credentials"
        every { mockContext.getString(R.string.email_invalid_error) } returns "Invalid email format"
        every { mockContext.getString(R.string.password_criteria_error) } returns "Password too short"
        every { mockContext.getString(R.string.something_went_wrong) } returns "Generic error"

        coEvery { sessionBootstrapper.signUp(any(), any(), any(), any()) } returns Result.failure(Exception("An account with this email already exists"))

        val viewModel = createViewModel(mockContext)
        viewModel.signUp("test@example.com", "Password123!")

        assertEquals("Email already registered", viewModel.authState.value.error)
    }

    @Test
    fun onVoiceTapWhenSpeechRecognitionUnavailableEmitsMessageRes() = runTest {
        every { permissionState.hasMicrophone() } returns true
        every { permissionState.hasNotifications() } returns true
        every { transcriber.startListening(any(), any(), any()) } returns false

        val viewModel = createViewModel()
        val events = mutableListOf<UiEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.onVoiceTap()

        assertTrue(events.any { it is UiEvent.MessageRes && it.resId == R.string.speech_not_available })
        job.cancel()
    }

    @Test
    fun voiceAndCommandStatusUsesContextLocalizedStringsWhenProvided() = runTest {
        val mockContext: Context = mockk()
        every { mockContext.getString(R.string.listening_status) } returns "Escuchando…"
        every { mockContext.getString(R.string.parsing_status) } returns "Analizando…"
        every { mockContext.getString(R.string.transcribing_status) } returns "Transcribiendo…"
        every { mockContext.getString(R.string.nothing_recognized) } returns "Nada reconocido"
        every { mockContext.getString(R.string.something_went_wrong) } returns "Algo salió mal"

        every { permissionState.hasMicrophone() } returns true
        every { permissionState.hasNotifications() } returns true
        every { transcriber.startListening(any(), any(), any()) } answers {
            val onPartial = firstArg<(String) -> Unit>()
            onPartial("")
            true
        }

        val emptyResult = VoiceRunResult(
            transcript = "test",
            executed = emptyList(),
            remainingQuota = 3,
        )
        coEvery { executeCommand.execute("test", any()) } returns emptyResult

        val viewModel = createViewModel(mockContext)

        viewModel.onVoiceTap()
        assertEquals("Escuchando…", viewModel.statusText.value)
        assertEquals(BubbleState.LISTENING, viewModel.bubbleState.value)

        viewModel.submitTextCommand("test")
        assertEquals(BubbleState.LISTENING, viewModel.bubbleState.value)
        coVerify(exactly = 0) { executeCommand.execute("test", any()) }

        val commandViewModel = createViewModel(mockContext)
        commandViewModel.submitTextCommand("test")
        assertEquals("Nada reconocido", commandViewModel.statusText.value)

        coEvery { executeCommand.execute("fail", any()) } throws RuntimeException()
        val events = mutableListOf<UiEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            commandViewModel.events.collect { events.add(it) }
        }
        commandViewModel.submitTextCommand("fail")
        assertTrue(events.any { it is UiEvent.Message && it.text == "Algo salió mal" })
        job.cancel()
    }

    @Test
    fun onVoiceTapRequestsMicrophoneOnlyWhenNotGrantedAndStartsWhenGranted() = runTest {
        every { permissionState.hasMicrophone() } returns false
        val viewModel = createViewModel()
        val events = mutableListOf<UiEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.onVoiceTap()
        assertTrue(events.any { it is UiEvent.PermissionNeeded && it.kind == PermissionKind.MICROPHONE })

        every { permissionState.hasMicrophone() } returns true
        every { transcriber.startListening(any(), any(), any()) } returns true
        viewModel.onPermissionResult(PermissionKind.MICROPHONE, granted = true)

        assertEquals(BubbleState.LISTENING, viewModel.bubbleState.value)
        job.cancel()
    }

    @Test
    fun confirmActionsRequestsNotificationPermissionOnlyWhenReminderPresentAndNotGranted() = runTest {
        every { permissionState.hasNotifications() } returns false
        val reminderAction = com.actuate.domain.model.ParsedAction.Reminder(
            id = "rem-1",
            title = "Take medicine",
            dueAt = java.time.Instant.now().plusSeconds(3600),
        )
        val parsed = com.actuate.domain.model.ParsedActions(
            rawTranscript = "Remind me to take medicine in 1 hour",
            actions = listOf(reminderAction),
            source = com.actuate.domain.model.ParserSource.RULES,
            confidence = 1.0f,
        )

        val viewModel = createViewModel()
        viewModel.reviewText("Remind me to take medicine in 1 hour")
        val preparedField = HomeViewModel::class.java.getDeclaredField("_prepared")
        preparedField.isAccessible = true
        (preparedField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<com.actuate.domain.model.ParsedActions?>).value = parsed

        val events = mutableListOf<UiEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.confirmActions()
        assertTrue(events.any { it is UiEvent.PermissionNeeded && it.kind == PermissionKind.NOTIFICATIONS })
        job.cancel()
    }
}
