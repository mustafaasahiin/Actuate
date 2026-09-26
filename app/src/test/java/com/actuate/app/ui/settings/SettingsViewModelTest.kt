package com.actuate.app.ui.settings

import com.actuate.domain.entitlement.EntitlementProvider
import com.actuate.domain.model.ServerConfig
import com.actuate.domain.repository.AppSettingsRepository
import com.actuate.domain.repository.HistoryRepository
import com.actuate.domain.repository.ServerRepository
import com.actuate.domain.repository.ServerSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class SettingsViewModelTest {

    private val settingsRepository: AppSettingsRepository = mockk(relaxed = true)
    private val historyRepository: HistoryRepository = mockk(relaxed = true)
    private val entitlementProvider: EntitlementProvider = mockk(relaxed = true)
    private val serverRepository: ServerRepository = mockk(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()
    private val isProFlow = MutableStateFlow(false)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { entitlementProvider.observeIsPro() } returns isProFlow
        every { entitlementProvider.displayName } returns "Free"
        coEvery { entitlementProvider.isPro() } returns false
        coEvery { settingsRepository.readServerConfig() } returns ServerConfig(
            baseUrl = "http://localhost:8787",
            token = "test-token",
            userId = "user-1",
        )
        coEvery { serverRepository.me() } returns Result.success(
            ServerSession(
                userId = "user-1",
                token = "test-token",
                isPro = false,
                quotaRemaining = 3,
            ),
        )
        coEvery { serverRepository.integrationsStatus() } returns Result.success(
            com.actuate.domain.model.IntegrationsStatus(),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SettingsViewModel(
        settingsRepository = settingsRepository,
        historyRepository = historyRepository,
        entitlementProvider = entitlementProvider,
        serverRepository = serverRepository,
    )

    @Test
    fun initialStateReflectsFreePlan() = runTest {
        val viewModel = createViewModel()
        assertFalse(viewModel.state.value.isPro)
        assertEquals("Free", viewModel.state.value.planName)
        assertEquals("Connected · 3 left this week", viewModel.state.value.serverStatus)
    }

    @Test
    fun stateUpdatesToProWhenEntitlementEmitsTrue() = runTest {
        val viewModel = createViewModel()
        assertFalse(viewModel.state.value.isPro)

        isProFlow.value = true
        assertTrue(viewModel.state.value.isPro)
        assertEquals("Actuate Pro · Unlimited", viewModel.state.value.planName)
    }

    @Test
    fun upgradeToProCallsEntitlementSetProTrue() = runTest {
        val viewModel = createViewModel()
        viewModel.upgradeToPro()
        coVerify { entitlementProvider.setPro(true) }
    }

    @Test
    fun setProUpdatesEntitlement() = runTest {
        val viewModel = createViewModel()
        viewModel.setPro(true)
        coVerify { entitlementProvider.setPro(true) }

        viewModel.setPro(false)
        coVerify { entitlementProvider.setPro(false) }
    }

    @Test
    fun clearHistoryCallsHistoryRepository() = runTest {
        val viewModel = createViewModel()
        viewModel.clearHistory()
        coVerify { historyRepository.clear() }
    }
}
