package com.actuate.app.ui.home

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.actuate.app.R
import com.actuate.app.session.SessionBootstrapper
import com.actuate.app.session.SessionResult
import com.actuate.app.util.PermissionState
import com.actuate.core.audio.AudioWaveformBuffer
import com.actuate.core.components.BubbleState
import com.actuate.domain.entitlement.EntitlementProvider
import com.actuate.domain.model.ActionRecord
import com.actuate.domain.model.Destination
import com.actuate.domain.model.ServerConfig
import com.actuate.domain.model.VoiceRunResult
import com.actuate.domain.repository.AppSettingsRepository
import com.actuate.domain.repository.HistoryRepository
import com.actuate.domain.repository.QuotaRepository
import com.actuate.domain.repository.ServerRepository
import com.actuate.domain.speech.SpeechTranscriber
import com.actuate.domain.usecase.ExecuteVoiceCommandUseCase
import com.actuate.domain.usecase.QuotaExceededException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class PermissionKind { MICROPHONE, NOTIFICATIONS }

sealed interface UiEvent {
    data class PermissionNeeded(val kind: PermissionKind) : UiEvent
    data class Message(val text: String) : UiEvent
    data class MessageRes(@StringRes val resId: Int, val formatArgs: List<Any> = emptyList()) : UiEvent
    data object QuotaExhausted : UiEvent
}

/** Lifecycle of an explicit signup/login attempt driven by the auth screens. */
data class AuthUiState(
    val submitting: Boolean = false,
    val succeeded: Boolean = false,
    val error: String? = null,
    val isNewRegistration: Boolean = false,
)

class HomeViewModel(
    private val transcriber: SpeechTranscriber,
    private val executeCommand: ExecuteVoiceCommandUseCase,
    private val settingsRepository: AppSettingsRepository,
    private val quotaRepository: QuotaRepository,
    private val entitlementProvider: EntitlementProvider,
    private val historyRepository: HistoryRepository,
    private val sessionBootstrapper: SessionBootstrapper,
    private val permissionState: PermissionState,
    private val context: Context? = null,
    private val serverRepository: ServerRepository? = null,
) : ViewModel() {

    private val _bubbleState = MutableStateFlow(BubbleState.IDLE)
    val bubbleState: StateFlow<BubbleState> = _bubbleState

    private val _statusText = MutableStateFlow<String?>(null)
    val statusText: StateFlow<String?> = _statusText

    private val draftPrefs = runCatching { context?.getSharedPreferences("capture_draft", Context.MODE_PRIVATE) }.getOrNull()
    private val _draft = MutableStateFlow(draftPrefs?.getString("text", "").orEmpty())
    val draft: StateFlow<String> = _draft
    private val _captureResult = MutableStateFlow<VoiceRunResult?>(null)
    val captureResult: StateFlow<VoiceRunResult?> = _captureResult
    private val _audioLevel = MutableStateFlow<Float?>(null)

    /** Raw RMS level in dBFS, or `null` while not recording. */
    val audioLevel: StateFlow<Float?> = _audioLevel

    private val waveformBuffer = AudioWaveformBuffer()
    private val _audioWaveform = MutableStateFlow(waveformBuffer.snapshot())

    /**
     * Rolling history of the last [AudioWaveformBuffer.BAND_COUNT] normalized amplitude
     * bands, oldest first. Driven only by real microphone RMS — when nothing is being
     * recorded the buffer is flat, never a decorative idle animation.
     */
    val audioWaveform: StateFlow<List<Float>> = _audioWaveform
    private val _prepared = MutableStateFlow<com.actuate.domain.model.ParsedActions?>(null)
    val prepared: StateFlow<com.actuate.domain.model.ParsedActions?> = _prepared

    /** Wall-clock ms the last parse took, surfaced as the staging deck's latency badge. */
    private val _prepareLatencyMs = MutableStateFlow<Long?>(null)
    val prepareLatencyMs: StateFlow<Long?> = _prepareLatencyMs
    private var awaitingReminderPermission = false
    private var reminderPermissionAsked = false

    /**
     * Applies an edit from the staging deck (destination switch, time step, rename).
     *
     * Replaces by id so sibling actions and the raw transcript are untouched, and the
     * sheet is never dismissed or rebuilt from scratch.
     */
    fun editAction(action: com.actuate.domain.model.ParsedAction) {
        _prepared.value = _prepared.value?.let { current ->
            com.actuate.app.ui.sections.ActionStaging.applyTo(current, action)
        }
    }

    fun reviewText(text: String) {
        if (_bubbleState.value != BubbleState.IDLE || text.isBlank()) return
        updateDraft(text)
        _captureResult.value = null
        _prepared.value = null
        _bubbleState.value = BubbleState.BUSY
        _statusText.value = "Understanding your thought…"
        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            val prepResult = withTimeoutOrNull(PREPARE_TIMEOUT_MS) {
                runCatching { executeCommand.prepare(text.trim()) }
            }
            _prepareLatencyMs.value = System.currentTimeMillis() - startedAt
            if (prepResult != null && prepResult.isSuccess) {
                _prepared.value = prepResult.getOrNull()
                _statusText.value = null
            } else {
                val errorMsg = prepResult?.exceptionOrNull()?.message ?: "Command understanding timed out"
                _statusText.value = "Couldn’t understand this thought. Your text is saved. You can edit it and try again."
                emit(UiEvent.Message("Couldn’t understand thought: $errorMsg"))
            }
            _bubbleState.value = BubbleState.IDLE
        }
    }

    fun confirmActions() {
        if (_bubbleState.value != BubbleState.IDLE) return
        val rawParsed = _prepared.value ?: return
        val parsed = if (rawParsed.actions.any { it is com.actuate.domain.model.ParsedAction.Reminder && it.dueAt == null }) {
            rawParsed.copy(
                actions = rawParsed.actions.map { action ->
                    if (action is com.actuate.domain.model.ParsedAction.Reminder && action.dueAt == null) {
                        action.copy(dueAt = java.time.Instant.now().plus(java.time.Duration.ofHours(1)))
                    } else {
                        action
                    }
                }
            )
        } else {
            rawParsed
        }
        if (parsed.actions.any { it is com.actuate.domain.model.ParsedAction.Reminder } && !permissionState.hasNotifications() && !reminderPermissionAsked) {
            awaitingReminderPermission = true
            reminderPermissionAsked = true
            emit(UiEvent.PermissionNeeded(PermissionKind.NOTIFICATIONS))
            return
        }
        _bubbleState.value = BubbleState.BUSY
        _statusText.value = "Saving your actions…"
        viewModelScope.launch {
            var allSucceeded = false
            runCatching { executeCommand.executePrepared(parsed) }
                .onSuccess {
                    _captureResult.value = it
                    _prepared.value = null
                    _executionTick.value += 1
                    _statusText.value = null
                    allSucceeded = it.executed.isNotEmpty() && it.executed.all { result -> result.success }
                }
                .onFailure {
                    _statusText.value = "Couldn’t save these actions. Your text and corrections are still here."
                    if (it is QuotaExceededException) emit(UiEvent.QuotaExhausted)
                }
            // Hold the emerald burst just long enough to register, then return to idle.
            if (allSucceeded) {
                _bubbleState.value = BubbleState.SUCCESS
                delay(SUCCESS_FLASH_MS)
            }
            _bubbleState.value = BubbleState.IDLE
        }
    }

    fun updateDraft(text: String) {
        _draft.value = text
        draftPrefs?.edit()?.putString("text", text)?.apply()
    }

    fun newThought() {
        if (_bubbleState.value != BubbleState.IDLE) return
        _captureResult.value = null
        _prepared.value = null
        _statusText.value = null
        updateDraft("")
    }

    fun retryFailed() {
        if (_bubbleState.value != BubbleState.IDLE) return
        val previous = _captureResult.value ?: return
        _bubbleState.value = BubbleState.BUSY
        _statusText.value = "Retrying failed actions…"
        viewModelScope.launch {
            runCatching { executeCommand.retryFailed(previous) }
                .onSuccess { _captureResult.value = it; _executionTick.value += 1; _statusText.value = null }
                .onFailure { _statusText.value = "Couldn’t retry. Your actions are still here." }
            _bubbleState.value = BubbleState.IDLE
        }
    }

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events

    private val _quotaText = MutableStateFlow<String?>(null)
    val quotaText: StateFlow<String?> = _quotaText

    /** "Connected" / "Offline" / null while the first session check runs. */
    private val _sessionStatus = MutableStateFlow<String?>(null)
    val sessionStatus: StateFlow<String?> = _sessionStatus

    private val _authState = MutableStateFlow(AuthUiState())
    val authState: StateFlow<AuthUiState> = _authState

    val recentHistory: StateFlow<List<ActionRecord>> = historyRepository.observeHistory()
        .map { it.takeLast(5).reversed() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Bumped after each voice run so sections (Today, Lists) can reload. */
    private val _executionTick = MutableStateFlow(0)
    val executionTick: StateFlow<Int> = _executionTick

    private val _showOnboarding = MutableStateFlow(false)
    val showOnboarding: StateFlow<Boolean> = _showOnboarding

    private var pendingVoiceTap = false

    init {
        transcriber.setAudioLevelListener { rmsDb ->
            _audioLevel.value = rmsDb
            waveformBuffer.push(AudioWaveformBuffer.normalizeRmsDb(rmsDb))
            _audioWaveform.value = waveformBuffer.snapshot()
        }
        transcriber.setTranscribingListener {
            _bubbleState.value = BubbleState.BUSY
            _statusText.value = "Transcribing your recording…"
        }
        viewModelScope.launch {
            settingsRepository.hasSeenOnboarding.collect { hasSeen ->
                if (!hasSeen) {
                    _showOnboarding.value = true
                }
            }
        }
        viewModelScope.launch {
            combine(
                entitlementProvider.observeIsPro(),
                quotaRepository.observeRemaining(),
            ) { isPro, remaining ->
                if (isPro) "Pro · unlimited" else "Free · $remaining left this week"
            }.collect { _quotaText.value = it }
        }
        viewModelScope.launch {
            when (val result = sessionBootstrapper.ensureSession(permissionState.deviceId())) {
                is SessionResult.Connected -> _sessionStatus.value = "Connected"
                is SessionResult.Offline -> _sessionStatus.value = "Offline"
                is SessionResult.NeedsSignup -> _sessionStatus.value = "Login"
                is SessionResult.NeedsLogin -> _sessionStatus.value = "Login"
            }
        }
    }

    fun dismissOnboarding(requestPermissions: Boolean = false) {
        _showOnboarding.value = false
        viewModelScope.launch {
            settingsRepository.setHasSeenOnboarding(true)
        }
        if (requestPermissions) {
            if (!permissionState.hasMicrophone()) {
                emit(UiEvent.PermissionNeeded(PermissionKind.MICROPHONE))
            }
            if (!permissionState.hasNotifications()) {
                emit(UiEvent.PermissionNeeded(PermissionKind.NOTIFICATIONS))
            }
        }
    }

    fun showOnboarding() {
        _showOnboarding.value = true
    }

    fun upgradeToPro(promoCode: String = "") {
        viewModelScope.launch {
            entitlementProvider.setPro(true)
            runCatching {
                serverRepository?.syncEntitlement(isPro = true, promoCode = promoCode)
            }
        }
    }

    /**
     * Clears the local session and re-registers the device (same device id,
     * same server-side user — a fresh token). Local history is kept.
     */
    fun resetSession() {
        viewModelScope.launch {
            settingsRepository.saveServerConfig(ServerConfig())
            _sessionStatus.value = null
            _executionTick.value += 1  // Bump tick so sections reload
            when (val result = sessionBootstrapper.ensureSession(permissionState.deviceId())) {
                is SessionResult.Connected -> _sessionStatus.value = "Connected"
                is SessionResult.Offline -> _sessionStatus.value = "Offline"
                is SessionResult.NeedsSignup -> _sessionStatus.value = "Signup"
                is SessionResult.NeedsLogin -> _sessionStatus.value = "Login"
            }
        }
    }

    // region Auth screens

    fun signUp(email: String, password: String, name: String = "") {
        if (_authState.value.submitting) return
        _authState.value = AuthUiState(submitting = true)
        viewModelScope.launch {
            sessionBootstrapper
                .signUp(email = email, password = password, name = name, deviceId = permissionState.deviceId())
                .onSuccess {
                    _authState.value = AuthUiState(succeeded = true, isNewRegistration = true)
                    _sessionStatus.value = "Connected"
                    emit(UiEvent.MessageRes(R.string.account_created_welcome))
                }
                .onFailure { error ->
                    _authState.value = AuthUiState(error = friendlyAuthError(error))
                }
        }
    }

    fun logIn(email: String, password: String) {
        if (_authState.value.submitting) return
        _authState.value = AuthUiState(submitting = true)
        viewModelScope.launch {
            sessionBootstrapper
                .logIn(email = email, password = password, deviceId = permissionState.deviceId())
                .onSuccess {
                    _authState.value = AuthUiState(succeeded = true, isNewRegistration = false)
                    _sessionStatus.value = "Connected"
                    emit(UiEvent.MessageRes(R.string.welcome_back))
                }
                .onFailure { error ->
                    _authState.value = AuthUiState(error = friendlyAuthError(error))
                }
        }
    }

    fun logOut() {
        viewModelScope.launch {
            sessionBootstrapper.logOut()
            _sessionStatus.value = "Login"
            _executionTick.value += 1
        }
    }

    /** Auth screens call this after consuming a success (navigation). */
    fun consumeAuthSuccess() {
        if (_authState.value.succeeded) _authState.value = AuthUiState()
    }

    private fun friendlyAuthError(error: Throwable): String {
        val msg = error.message.orEmpty()
        val ctx = context
        return when {
            msg.contains("already exists", ignoreCase = true) ->
                ctx?.getString(R.string.auth_error_email_exists) ?: "An account with this email already exists"
            msg.contains("Invalid email or password", ignoreCase = true) ->
                ctx?.getString(R.string.auth_error_invalid_credentials) ?: "Invalid email or password"
            msg.contains("valid email", ignoreCase = true) ->
                ctx?.getString(R.string.email_invalid_error) ?: "Enter a valid email address"
            msg.contains("at least 8", ignoreCase = true) ->
                ctx?.getString(R.string.password_criteria_error) ?: "Must be at least 8 characters with letters and numbers/symbols"
            else ->
                ctx?.getString(R.string.something_went_wrong) ?: (error.message?.takeIf { it.isNotBlank() } ?: "Something went wrong — try again")
        }
    }

    // endregion

    // region Voice

    fun onVoiceTap() {
        if (!permissionState.hasMicrophone()) {
            pendingVoiceTap = true
            emit(UiEvent.PermissionNeeded(PermissionKind.MICROPHONE))
            return
        }
        when (_bubbleState.value) {
            BubbleState.IDLE -> startListening()
            BubbleState.LISTENING -> transcriber.stopListening()
            // BUSY and SUCCESS are transient states the engine owns; a tap during
            // either is swallowed so the user can't enqueue a second capture.
            BubbleState.BUSY -> Unit
            BubbleState.SUCCESS -> Unit
        }
    }

    fun onPermissionResult(kind: PermissionKind, granted: Boolean) {
        if (kind == PermissionKind.NOTIFICATIONS && awaitingReminderPermission) {
            awaitingReminderPermission = false
            confirmActions()
            return
        }
        // Only the microphone result can cancel or continue a pending voice flow.
        // A notification-permission result (or a permission that was not part of
        // the request) must never disturb an in-flight listening session.
        if (kind != PermissionKind.MICROPHONE) return
        if (!granted) {
            if (pendingVoiceTap) {
                pendingVoiceTap = false
                _bubbleState.value = BubbleState.IDLE
                _statusText.value = null
            }
            return
        }
        if (pendingVoiceTap) {
            pendingVoiceTap = false
            startListening()
        }
    }

    private fun startListening() {
        _audioLevel.value = null
        waveformBuffer.clear()
        _audioWaveform.value = waveformBuffer.snapshot()
        val listening = context?.getString(R.string.listening_status) ?: "Listening…"
        val started = transcriber.startListening(
            onPartial = { partial ->
                if (partial.isNotBlank()) updateDraft(partial)
                _statusText.value = partial.ifBlank { listening }
            },
            onFinal = { text -> onFinalTranscript(text) },
            onError = { message -> onRecognitionError(message) },
        )
        if (!started) {
            emit(UiEvent.MessageRes(R.string.speech_not_available))
            return
        }
        _bubbleState.value = BubbleState.LISTENING
        _statusText.value = listening
    }

    fun submitTextCommand(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (_bubbleState.value == BubbleState.BUSY) return
        if (_bubbleState.value == BubbleState.LISTENING) return
        updateDraft(trimmed)
        _captureResult.value = null
        _bubbleState.value = BubbleState.BUSY
        _statusText.value = context?.getString(R.string.parsing_status) ?: "Parsing…"
        viewModelScope.launch {
            executeCommandInternal(trimmed)
        }
    }

    private fun onFinalTranscript(text: String) {
        updateDraft(text)
        _captureResult.value = null
        _bubbleState.value = BubbleState.IDLE
        reviewText(text)
    }

    fun resetVoiceState() {
        if (_bubbleState.value != BubbleState.IDLE) {
            _bubbleState.value = BubbleState.IDLE
            _statusText.value = null
        }
    }

    private fun onRecognitionError(message: String) {
        _bubbleState.value = BubbleState.IDLE
        _statusText.value = message
        emit(UiEvent.Message(message))
    }

    private suspend fun executeCommandInternal(text: String) {
        _statusText.value = context?.getString(R.string.parsing_status) ?: "Parsing…"
        val result = runCatching { executeCommand.execute(text) }
            .getOrElse { error ->
                if (error is QuotaExceededException || error.cause is QuotaExceededException) {
                    _statusText.value = context?.getString(R.string.quota_exhausted_error)
                        ?: (error.message ?: "Free tier: 3 actions per week. Upgrade for unlimited.")
                    emit(UiEvent.QuotaExhausted)
                    emit(UiEvent.Message(error.message ?: "Free tier: 3 actions per week. Upgrade for unlimited."))
                } else {
                    _statusText.value = "Couldn’t process this thought. Your text is saved. Try again when you’re connected."
                    emit(UiEvent.Message(error.message ?: (context?.getString(R.string.something_went_wrong) ?: "Something went wrong")))
                }
                null
            }
        if (result != null) {
            _captureResult.value = result
            val done = result.executed.count { it.success }
            val failed = result.executed.count { !it.success }
            _statusText.value = when {
                result.executed.isEmpty() -> context?.getString(R.string.nothing_recognized) ?: "Nothing recognized"
                failed == 0 -> "$done executed"
                else -> "$done done · $failed failed"
            }
            _executionTick.value += 1
        }
        _bubbleState.value = BubbleState.IDLE
    }

    private fun summaryMessage(result: VoiceRunResult): String {
        if (result.executed.isEmpty()) return "I couldn't find any actions in that."
        val lines = result.executed.map { item ->
            when {
                item.success -> "✓ ${item.message}"
                item.destination == Destination.REMINDERS -> "— ${item.message}"
                else -> "✗ ${item.message}"
            }
        }
        return lines.joinToString("\n")
    }

    private fun emit(event: UiEvent) {
        _events.tryEmit(event)
    }

    override fun onCleared() {
        transcriber.destroy()
        super.onCleared()
    }

    companion object {
        const val PREPARE_TIMEOUT_MS = 15_000L

        /** How long the emerald success burst holds before the bubble returns to idle. */
        const val SUCCESS_FLASH_MS = 900L
    }
}
