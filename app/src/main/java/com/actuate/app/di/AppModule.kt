package com.actuate.app.di

import com.actuate.app.BuildConfig
import com.actuate.app.session.SessionBootstrapper
import com.actuate.data.auth.GoogleCalendarAuthManager
import com.actuate.data.calendar.CalendarOfflineSyncManager
import com.actuate.data.calendar.GoogleCalendarSyncService
import com.actuate.data.entitlement.LocalQuotaEntitlementProvider
import com.actuate.data.entitlement.RevenueCatEntitlementProvider
import com.actuate.data.network.ActuateServerApi
import com.actuate.data.executor.ServerActionExecutor
import com.actuate.data.parser.ServerActionParser
import com.actuate.data.quota.HistoryRepositoryImpl
import com.actuate.data.quota.QuotaRepositoryImpl
import com.actuate.data.reminders.LocalReminderScheduler
import com.actuate.data.security.KeystoreSecretStore
import com.actuate.data.settings.AppSettingsRepositoryImpl
import com.actuate.data.speech.AndroidSpeechTranscriber
import com.actuate.core.audio.FeedbackPreferences
import com.actuate.core.audio.TactileSoundEngine
import com.actuate.data.speech.WhisperSpeechTranscriber
import com.actuate.data.storage.LocalActionStore as LocalActionStoreImpl
import com.actuate.domain.entitlement.EntitlementProvider
import com.actuate.domain.executor.ActionExecutor
import com.actuate.domain.parser.ActionParser
import com.actuate.domain.parser.RuleBasedActionParser
import com.actuate.domain.repository.AppSettingsRepository
import com.actuate.domain.repository.HistoryRepository
import com.actuate.domain.repository.LocalActionStore
import com.actuate.domain.repository.QuotaRepository
import com.actuate.domain.repository.SecretStore
import com.actuate.domain.repository.ServerRepository
import com.actuate.domain.speech.SpeechTranscriber
import com.actuate.domain.usecase.ExecuteVoiceCommandUseCase
import com.actuate.app.ui.calendar.CalendarViewModel
import com.actuate.app.ui.home.HomeViewModel
import com.actuate.app.util.PermissionState
import com.actuate.app.ui.history.HistoryViewModel
import com.actuate.app.ui.sections.SectionsViewModel
import com.actuate.app.ui.settings.ConnectionsViewModel
import com.actuate.app.ui.settings.SettingsViewModel
import com.actuate.app.util.LocaleManager
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val appModule = module {

    single { createHttpClient() }

    single { KeystoreSecretStore(androidContext()) } bind SecretStore::class
    single { GoogleCalendarAuthManager(androidContext(), get<SecretStore>()) }
    single { GoogleCalendarSyncService(get<OkHttpClient>(), get<GoogleCalendarAuthManager>()) }
    single {
        CalendarOfflineSyncManager(
            localActionStore = get(),
            googleCalendarSyncService = get(),
            googleCalendarAuthManager = get(),
            context = androidContext(),
        )
    }
    single { AppSettingsRepositoryImpl(androidContext(), get()) } bind AppSettingsRepository::class
    single { QuotaRepositoryImpl(androidContext()) } bind QuotaRepository::class
    single { HistoryRepositoryImpl(androidContext()) } bind HistoryRepository::class
    single { LocalActionStoreImpl(androidContext()) } bind LocalActionStore::class

    // Actuate server — the endpoint is compiled in per build type; the app
    // only ever stores an encrypted session token.
    single {
        ActuateServerApi(
            client = get(),
            baseUrl = BuildConfig.SERVER_BASE_URL,
            configProvider = { get<AppSettingsRepository>().readServerConfig() },
        )
    } bind ServerRepository::class
    single { LocalReminderScheduler(androidContext()) }

    // Parser chain: server LLM -> offline rules (server does the LLM call).
    single {
        ServerActionParser(get(), RuleBasedActionParser())
    } bind ActionParser::class
    single(named("rules")) { RuleBasedActionParser() } bind ActionParser::class

    // Executor: server-first (file storage), reminders
    // always schedule locally; no on-device destination credentials.
    single {
        ServerActionExecutor(
            serverRepository = get<ServerRepository>(),
            reminderScheduler = get<LocalReminderScheduler>(),
            localActionStore = get<LocalActionStore>(),
            googleCalendarSyncService = get<GoogleCalendarSyncService>(),
            googleCalendarAuthManager = get<GoogleCalendarAuthManager>(),
            entitlementProvider = getOrNull(),
        )
    } bind ActionExecutor::class

    single {
        ExecuteVoiceCommandUseCase(
            parser = get(),
            executor = get(),
            quotaRepository = get(),
            historyRepository = get(),
            entitlementProvider = getOrNull(),
        )
    }
    single { WhisperSpeechTranscriber(androidContext()) } bind SpeechTranscriber::class
    single {
        val fallback = LocalQuotaEntitlementProvider(androidContext(), get())
        val permissionState: PermissionState = get()
        val resKey = runCatching {
            androidContext().getString(com.actuate.app.R.string.revenuecat_public_key)
        }.getOrElse {
            val id = androidContext().resources.getIdentifier("revenuecat_public_key", "string", androidContext().packageName)
            if (id != 0) androidContext().getString(id) else "goog_OQkpQXWyGyBKfajmLnZaOYGfzCj"
        }.ifBlank { "goog_OQkpQXWyGyBKfajmLnZaOYGfzCj" }
        RevenueCatEntitlementProvider(
            appContext = androidContext(),
            fallback = fallback,
            apiKeyProvider = { resKey },
            appUserIdProvider = { permissionState.deviceId() },
        )
    } bind EntitlementProvider::class
    single { SessionBootstrapper(get(), get()) }

    single { PermissionState(androidContext()) }
    single { LocaleManager(androidContext()) }

    // Procedural sound design. Synthesized in memory on demand, never an asset,
    // and gated on the user's own Sound & Haptics preference.
    single {
        TactileSoundEngine(
            context = androidContext(),
            enabledProvider = { FeedbackPreferences.isSoundEnabled(androidContext()) },
        )
    }

    viewModel {
        HomeViewModel(
            transcriber = get(),
            executeCommand = get(),
            settingsRepository = get(),
            quotaRepository = get(),
            entitlementProvider = get(),
            historyRepository = get(),
            sessionBootstrapper = get(),
            permissionState = get(),
            context = androidContext(),
            serverRepository = getOrNull(),
        )
    }
    viewModel { ConnectionsViewModel(get()) }
    viewModel { HistoryViewModel(get()) }
    viewModel { CalendarViewModel(get(), get(), get()) }
    viewModel { SectionsViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get()) }
}

private fun createHttpClient(): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .hostnameVerifier { hostname, session ->
            if ((hostname == "35.232.148.87" || hostname == "35.232.148.87.sslip.io") &&
                runCatching { session.peerPrincipal.name.contains("35.232.148.87.sslip.io") }.getOrDefault(false)) {
                true
            } else {
                javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
            }
        }
        .build()