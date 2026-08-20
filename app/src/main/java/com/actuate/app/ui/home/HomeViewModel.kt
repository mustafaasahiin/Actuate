package com.actuate.app.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.actuate.app.session.SessionBootstrapper
import com.actuate.app.session.SessionResult
import com.actuate.core.components.BubbleState
import com.actuate.core.components.SidebarState
import com.actuate.domain.entitlement.EntitlementProvider
import com.actuate.domain.model.ActionRecord
import com.actuate.domain.model.Destination
import com.actuate.domain.model.ServerConfig
import com.actuate.domain.model.VoiceRunResult
import com.actuate.domain.repository.AppSettingsRepository
import com.actuate.domain.repository.HistoryRepository
import com.actuate.domain.repository.QuotaRepository
import com.actuate.domain.speech.SpeechTranscriber
import com.actuate.domain.usecase.ExecuteVoiceCommandUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PermissionKind { MICROPHONE, NOTIFICATIONS }

sealed interface UiEvent {
    data class PermissionNeeded(val kind: PermissionKind) : UiEvent
    data class Message(val text: String) : UiEvent
}

/** Thin permission check without leaking Android APIs into the ViewModel. */
class PermissionState(context: Context) {
    private val appContext = context.applicationContext

    fun hasMicrophone(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun hasNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Anonymous, app-scoped install ID: a random UUID generated on first launch
     * and persisted locally. No hardware/device identifiers ever leave the app.
     */
    fun deviceId(): String {
        val prefs = appContext.getSharedPreferences("actuate_device", Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val fresh = java.util.UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, fresh).apply()
        return fresh
    }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
    }
}

class HomeViewModel(
    private val transcriber: SpeechTranscriber,
    private val executeCommand: ExecuteVoiceCommandUseCase,
    private val settingsRepository: AppSettingsRepository,
    private val quotaRepository: QuotaRepository,
    private val entitlementProvider: EntitlementProvider,
    private val historyRepository: HistoryRepository,
    private val sessionBootstrapper: SessionBootstrapper,
    private val permissionState: PermissionState,
) : ViewModel() {

    private val _sidebarState = MutableStateFlow(SidebarState.COLLAPSED)
    val sidebarState: StateFlow<SidebarState> = _sidebarState

    private val _bubbleState = MutableStateFlow(BubbleState.IDLE)
    val bubbleState: StateFlow<BubbleState> = _bubbleState

    private val _statusText = MutableStateFlow<String?>(null)
    val statusText: StateFlow<String?> = _statusText

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events

    private val _quotaText = MutableStateFlow<String?>(null)
    val quotaText: StateFlow<String?> = _quotaText

    /** "Connected" / "Offline" / null while the first session check runs. */
    private val _sessionStatus = MutableStateFlow<String?>(null)
    val sessionStatus: StateFlow<String?> = _sessionStatus

    val recentHistory: StateFlow<List<ActionRecord>> = historyRepository.observeHistory()
        .map { it.takeLast(5).reversed() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Bumped after each voice run so sections (Today, Lists) can reload. */
    private val _executionTick = MutableStateFlow(0)
    val executionTick: StateFlow<Int> = _executionTick

    private var pendingVoiceTap = false

    init {
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
            }
        }
    }

    // region Sidebar

    fun toggleSidebar() {
        _sidebarState.value = when (_sidebarState.value) {
            SidebarState.COLLAPSED -> SidebarState.EXPANDED
            SidebarState.EXPANDED -> SidebarState.COLLAPSED
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
            when (val result = sessionBootstrapper.ensureSession(permissionState.deviceId())) {
                is SessionResult.Connected -> _sessionStatus.value = "Connected"
                is SessionResult.Offline -> _sessionStatus.value = "Offline"
            }
        }
    }

    // region Voice

    fun onVoiceTap() {
        if (!permissionState.hasMicrophone()) {
            pendingVoiceTap = true
            emit(UiEvent.PermissionNeeded(PermissionKind.MICROPHONE))
            return
        }
        if (!permissionState.hasNotifications()) {
            emit(UiEvent.PermissionNeeded(PermissionKind.NOTIFICATIONS))
        }
        when (_bubbleState.value) {
            BubbleState.IDLE -> startListening()
            BubbleState.LISTENING -> transcriber.stopListening()
            BubbleState.BUSY -> Unit
        }
    }

    fun onPermissionResult(kind: PermissionKind, granted: Boolean) {
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
        val started = transcriber.startListening(
            onPartial = { partial ->
                _statusText.value = partial.ifBlank { "Listening…" }
            },
            onFinal = { text -> onFinalTranscript(text) },
            onError = { message -> onRecognitionError(message) },
        )
        if (!started) {
            emit(UiEvent.Message("Speech recognition is not available on this device"))
            return
        }
        _bubbleState.value = BubbleState.LISTENING
        _statusText.value = "Listening…"
    }

    private fun onFinalTranscript(text: String) {
        _bubbleState.value = BubbleState.BUSY
        _statusText.value = "Transcribing…"
        viewModelScope.launch { executeTranscript(text) }
    }

    private fun onRecognitionError(message: String) {
        _bubbleState.value = BubbleState.IDLE
        _statusText.value = message
    }

    private fun executeTranscript(text: String) {
        viewModelScope.launch {
            _statusText.value = "Parsing…"
            val result = runCatching { executeCommand(text) }
                .getOrElse { error ->
                    emit(UiEvent.Message(error.message ?: "Something went wrong"))
                    null
                }
            if (result != null) {
                val done = result.executed.count { it.success }
                val failed = result.executed.count { !it.success }
                _statusText.value = when {
                    result.executed.isEmpty() -> "Nothing recognized"
                    failed == 0 -> "$done executed"
                    else -> "$done done · $failed failed"
                }
                emit(UiEvent.Message(summaryMessage(result)))
                _executionTick.value += 1
            }
            _bubbleState.value = BubbleState.IDLE
        }
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
}