package com.actuate.app.di

import com.actuate.app.BuildConfig
import com.actuate.app.session.SessionBootstrapper
import com.actuate.data.entitlement.LocalQuotaEntitlementProvider
import com.actuate.data.network.ActuateServerApi
import com.actuate.data.executor.ServerActionExecutor
import com.actuate.data.parser.ServerActionParser
import com.actuate.data.quota.HistoryRepositoryImpl
import com.actuate.data.quota.QuotaRepositoryImpl
import com.actuate.data.reminders.LocalReminderScheduler
import com.actuate.data.security.KeystoreSecretStore
import com.actuate.data.settings.AppSettingsRepositoryImpl
import com.actuate.data.speech.AndroidSpeechTranscriber
import com.actuate.domain.entitlement.EntitlementProvider
import com.actuate.domain.executor.ActionExecutor
import com.actuate.domain.parser.ActionParser
import com.actuate.domain.parser.RuleBasedActionParser
import com.actuate.domain.repository.AppSettingsRepository
import com.actuate.domain.repository.HistoryRepository
import com.actuate.domain.repository.QuotaRepository
import com.actuate.domain.repository.SecretStore
import com.actuate.domain.repository.ServerRepository
import com.actuate.domain.speech.SpeechTranscriber
import com.actuate.domain.usecase.ExecuteVoiceCommandUseCase
import com.actuate.app.ui.home.HomeViewModel
import com.actuate.app.ui.home.PermissionState
import com.actuate.app.ui.history.HistoryViewModel
import com.actuate.app.ui.sections.SectionsViewModel
import com.actuate.app.ui.settings.SettingsViewModel
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val appModule = module {

    single { createHttpClient() }

    singleOf(::KeystoreSecretStore) bind SecretStore::class
    singleOf(::AppSettingsRepositoryImpl) bind AppSettingsRepository::class
    singleOf(::QuotaRepositoryImpl) bind QuotaRepository::class
    singleOf(::HistoryRepositoryImpl) bind HistoryRepository::class

    // Actuate server — the endpoint is compiled in per build type; the app
    // only ever stores an encrypted session token.
    single {
        ActuateServerApi(
            client = get(),
            baseUrl = BuildConfig.SERVER_BASE_URL,
            configProvider = { runBlocking { get<AppSettingsRepository>().readServerConfig() } },
        )
    } bind ServerRepository::class
    singleOf(::LocalReminderScheduler)

    // Parser chain: server LLM -> offline rules (server does the LLM call).
    single {
        ServerActionParser(get(), RuleBasedActionParser())
    } bind ActionParser::class
    single(named("rules")) { RuleBasedActionParser() } bind ActionParser::class

    // Executor: server-first (Google Calendar API / Notion API), reminders
    // always schedule locally; no on-device destination credentials.
    single {
        ServerActionExecutor(
            serverRepository = get<ServerRepository>(),
            reminderScheduler = get<LocalReminderScheduler>(),
        )
    } bind ActionExecutor::class

    singleOf(::ExecuteVoiceCommandUseCase)
    singleOf(::AndroidSpeechTranscriber) bind SpeechTranscriber::class
    singleOf(::LocalQuotaEntitlementProvider) bind EntitlementProvider::class
    singleOf(::SessionBootstrapper)

    single { PermissionState(androidContext()) }

    viewModelOf(::HomeViewModel)
    viewModelOf(::HistoryViewModel)
    viewModelOf(::SectionsViewModel)
    viewModelOf(::SettingsViewModel)
}

private fun createHttpClient(): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()