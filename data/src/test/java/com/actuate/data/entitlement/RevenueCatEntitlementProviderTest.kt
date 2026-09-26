package com.actuate.data.entitlement

import android.app.Activity
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.actuate.domain.executor.ActionExecutor
import com.actuate.domain.model.Destination
import com.actuate.domain.model.ExecutionResult
import com.actuate.domain.model.ParsedAction
import com.actuate.domain.model.ParsedActions
import com.actuate.domain.model.ParserSource
import com.actuate.domain.parser.ActionParser
import com.actuate.domain.repository.HistoryRepository
import com.actuate.domain.repository.QuotaRepository
import com.actuate.domain.usecase.ExecuteVoiceCommandUseCase
import com.actuate.domain.usecase.QuotaExceededException
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.EntitlementInfo
import com.revenuecat.purchases.EntitlementInfos
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.models.StoreTransaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class RevenueCatEntitlementProviderTest {

    private lateinit var context: Context
    private lateinit var dataStore: InMemoryPreferencesDataStore
    private lateinit var quotaRepository: QuotaRepository
    private lateinit var fallbackProvider: LocalQuotaEntitlementProvider

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        dataStore = InMemoryPreferencesDataStore()
        quotaRepository = mockk(relaxed = true)
        fallbackProvider = LocalQuotaEntitlementProvider(dataStore, quotaRepository)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun fallbackWhenUnconfiguredDefaultsToFalse() = runTest {
        mockkObject(Purchases)
        every { Purchases.isConfigured } returns false

        val provider = RevenueCatEntitlementProvider(
            appContext = context,
            fallback = fallbackProvider,
            apiKeyProvider = { "" },
            appUserIdProvider = { "" },
        )

        assertFalse(provider.isReady())
        assertEquals("Free", provider.displayName)
        assertFalse(provider.isPro())
        assertFalse(provider.observeIsPro().first())
    }

    @Test
    fun displayNameReturnsFallbackWhenUnconfigured() {
        mockkObject(Purchases)
        every { Purchases.isConfigured } returns false

        val provider = RevenueCatEntitlementProvider(
            appContext = context,
            fallback = fallbackProvider,
            apiKeyProvider = { "" },
        )

        assertEquals("Free", provider.displayName)
    }

    @Test
    fun setProPropagatesToFallbackAndUpdatesObserveIsProWhenUnconfigured() = runTest {
        mockkObject(Purchases)
        every { Purchases.isConfigured } returns false

        val provider = RevenueCatEntitlementProvider(
            appContext = context,
            fallback = fallbackProvider,
            apiKeyProvider = { "" },
            appUserIdProvider = { "" },
        )

        assertFalse(provider.isPro())
        assertFalse(provider.observeIsPro().first())

        provider.setPro(true)

        assertTrue(provider.isPro())
        assertTrue(provider.observeIsPro().first())
        assertTrue(fallbackProvider.isPro())
        assertTrue(fallbackProvider.observeIsPro().first())

        provider.setPro(false)

        assertFalse(provider.isPro())
        assertFalse(provider.observeIsPro().first())
        assertFalse(fallbackProvider.isPro())
        assertFalse(fallbackProvider.observeIsPro().first())
    }

    @Test
    fun observeIsProEmitsTrueWhenProEntitlementIsActiveOnInit() = runTest {
        mockkObject(Purchases)
        val mockPurchases = mockk<Purchases>(relaxed = true)
        every { Purchases.isConfigured } returns true
        every { Purchases.sharedInstance } returns mockPurchases

        val customerInfo = createMockCustomerInfo(proActive = true)
        val callbackSlot = slot<ReceiveCustomerInfoCallback>()
        every { mockPurchases.getCustomerInfo(capture(callbackSlot)) } answers {
            callbackSlot.captured.onReceived(customerInfo)
        }

        val provider = RevenueCatEntitlementProvider(
            appContext = context,
            fallback = fallbackProvider,
            apiKeyProvider = { "test_api_key" },
            appUserIdProvider = { "test_user_id" },
        )

        assertTrue(provider.isReady())
        assertEquals("RevenueCat", provider.displayName)
        assertTrue(provider.observeIsPro().first())
        assertTrue(provider.isPro())
    }

    @Test
    fun observeIsProUpdatesWhenUpdatedCustomerInfoListenerFires() = runTest {
        mockkObject(Purchases)
        val mockPurchases = mockk<Purchases>(relaxed = true)
        every { Purchases.isConfigured } returns true
        every { Purchases.sharedInstance } returns mockPurchases

        var currentCustomerInfo = createMockCustomerInfo(proActive = false)
        val callbackSlot = slot<ReceiveCustomerInfoCallback>()
        every { mockPurchases.getCustomerInfo(capture(callbackSlot)) } answers {
            callbackSlot.captured.onReceived(currentCustomerInfo)
        }

        val provider = RevenueCatEntitlementProvider(
            appContext = context,
            fallback = fallbackProvider,
            apiKeyProvider = { "test_api_key" },
            appUserIdProvider = { "test_user_id" },
        )

        val listenerSlot = slot<UpdatedCustomerInfoListener>()
        verify { mockPurchases.updatedCustomerInfoListener = capture(listenerSlot) }

        assertFalse(provider.observeIsPro().first())
        assertFalse(provider.isPro())

        val updatedCustomerInfo = createMockCustomerInfo(proActive = true)
        currentCustomerInfo = updatedCustomerInfo
        listenerSlot.captured.onReceived(updatedCustomerInfo)

        assertTrue(provider.observeIsPro().first())
        assertTrue(provider.isPro())
    }

    @Test
    fun checkProEntitlementRecognizesAlternativeEntitlementKeysAndSubscriptions() = runTest {
        mockkObject(Purchases)
        val mockPurchases = mockk<Purchases>(relaxed = true)
        every { Purchases.isConfigured } returns true
        every { Purchases.sharedInstance } returns mockPurchases

        val customerInfoWithSubs = mockk<CustomerInfo>()
        val entitlementsMock = mockk<EntitlementInfos>()
        every { entitlementsMock[any()] } returns null
        every { entitlementsMock.all } returns emptyMap()
        every { customerInfoWithSubs.entitlements } returns entitlementsMock
        every { customerInfoWithSubs.activeSubscriptions } returns setOf("monthly_sub_id")

        val callbackSlot = slot<ReceiveCustomerInfoCallback>()
        every { mockPurchases.getCustomerInfo(capture(callbackSlot)) } answers {
            callbackSlot.captured.onReceived(customerInfoWithSubs)
        }

        val provider = RevenueCatEntitlementProvider(
            appContext = context,
            fallback = fallbackProvider,
            apiKeyProvider = { "key" },
        )

        assertTrue(provider.observeIsPro().first())
        assertTrue(provider.isPro())
    }

    @Test
    fun isProReturnsCurrentStateWhenGetCustomerInfoErrors() = runTest {
        mockkObject(Purchases)
        val mockPurchases = mockk<Purchases>(relaxed = true)
        every { Purchases.isConfigured } returns true
        every { Purchases.sharedInstance } returns mockPurchases

        val callbackSlot = slot<ReceiveCustomerInfoCallback>()
        every { mockPurchases.getCustomerInfo(capture(callbackSlot)) } answers {
            callbackSlot.captured.onError(
                PurchasesError(PurchasesErrorCode.NetworkError, "Offline"),
            )
        }

        val provider = RevenueCatEntitlementProvider(
            appContext = context,
            fallback = fallbackProvider,
            apiKeyProvider = { "key" },
        )

        assertFalse(provider.isPro())
    }

    @Test
    fun purchasePackageWhenUnconfiguredInvokesOnError() {
        mockkObject(Purchases)
        every { Purchases.isConfigured } returns false

        val provider = RevenueCatEntitlementProvider(
            appContext = context,
            fallback = fallbackProvider,
            apiKeyProvider = { "" },
        )

        val activity = mockk<Activity>()
        val pkg = mockk<Package>()
        var errorReceived: PurchasesError? = null

        provider.purchasePackage(
            activity = activity,
            packageToPurchase = pkg,
            onSuccess = {},
            onError = { err, _ -> errorReceived = err },
        )

        assertNotNull(errorReceived)
        assertEquals(PurchasesErrorCode.StoreProblemError, errorReceived?.code)
        assertEquals("RevenueCat is not configured", errorReceived?.underlyingErrorMessage)
    }

    @Test
    fun purchasePackageWhenConfiguredSuccessUpdatesProState() = runTest {
        mockkObject(Purchases)
        val mockPurchases = mockk<Purchases>(relaxed = true)
        every { Purchases.isConfigured } returns true
        every { Purchases.sharedInstance } returns mockPurchases

        val initialCustomerInfo = createMockCustomerInfo(proActive = false)
        val callbackSlot = slot<ReceiveCustomerInfoCallback>()
        every { mockPurchases.getCustomerInfo(capture(callbackSlot)) } answers {
            callbackSlot.captured.onReceived(initialCustomerInfo)
        }

        val provider = RevenueCatEntitlementProvider(
            appContext = context,
            fallback = fallbackProvider,
            apiKeyProvider = { "key" },
        )

        val activity = mockk<Activity>(relaxed = true)
        val pkg = mockk<Package>(relaxed = true)
        val purchaseCallbackSlot = slot<PurchaseCallback>()
        every { mockPurchases.purchase(any<PurchaseParams>(), capture(purchaseCallbackSlot)) } answers {
            val purchasedInfo = createMockCustomerInfo(proActive = true)
            val storeTx = mockk<StoreTransaction>(relaxed = true)
            purchaseCallbackSlot.captured.onCompleted(storeTx, purchasedInfo)
        }

        var successInfo: CustomerInfo? = null
        provider.purchasePackage(
            activity = activity,
            packageToPurchase = pkg,
            onSuccess = { info -> successInfo = info },
            onError = { _, _ -> },
        )

        assertNotNull(successInfo)
        assertTrue(provider.observeIsPro().first())
    }

    @Test
    fun quotaExceededExceptionIsThrownWhenQuotaIsExhaustedAndCaughtAppropriately() = runTest {
        mockkObject(Purchases)
        every { Purchases.isConfigured } returns false

        val provider = RevenueCatEntitlementProvider(
            appContext = context,
            fallback = fallbackProvider,
            apiKeyProvider = { "" },
        )

        coEvery { quotaRepository.tryConsume(any()) } returns false
        coEvery { quotaRepository.remaining() } returns 0

        val parser = mockk<ActionParser>()
        val executor = mockk<ActionExecutor>()
        val historyRepository = mockk<HistoryRepository>(relaxed = true)

        val calendarAction = ParsedAction.Calendar(
            title = "Team Standup",
            start = Instant.parse("2026-09-12T10:00:00Z"),
            end = Instant.parse("2026-09-12T10:30:00Z"),
        )
        coEvery { parser.parse(any(), any()) } returns ParsedActions(
            rawTranscript = "Team standup tomorrow at 10 AM",
            actions = listOf(calendarAction),
            source = ParserSource.RULES,
            confidence = 1.0f,
        )

        val useCase = ExecuteVoiceCommandUseCase(
            parser = parser,
            executor = executor,
            quotaRepository = quotaRepository,
            historyRepository = historyRepository,
            entitlementProvider = provider,
        )

        val exception = assertThrows(QuotaExceededException::class.java) {
            kotlinx.coroutines.runBlocking {
                useCase.execute("Team standup tomorrow at 10 AM")
            }
        }
        assertEquals("Free tier: 20 actions per week. Upgrade for unlimited.", exception.message)

        val runResult = runCatching {
            useCase.execute("Team standup tomorrow at 10 AM")
        }
        assertTrue(runResult.isFailure)
        assertTrue(runResult.exceptionOrNull() is QuotaExceededException)
        assertEquals(
            "Free tier: 20 actions per week. Upgrade for unlimited.",
            runResult.exceptionOrNull()?.message,
        )

        coVerify(exactly = 0) { executor.execute(any()) }
    }

    @Test
    fun judgePassSetsProStatusAndBypassesQuotaLimit() = runTest {
        mockkObject(Purchases)
        every { Purchases.isConfigured } returns false

        val provider = RevenueCatEntitlementProvider(
            appContext = context,
            fallback = fallbackProvider,
            apiKeyProvider = { "" },
        )

        coEvery { quotaRepository.tryConsume(any()) } returns false
        coEvery { quotaRepository.remaining() } returns 0

        val parser = mockk<ActionParser>()
        val executor = mockk<ActionExecutor>()
        val historyRepository = mockk<HistoryRepository>(relaxed = true)

        val calendarAction = ParsedAction.Calendar(
            title = "Product Review with Priya",
            start = Instant.parse("2026-09-12T15:00:00Z"),
            end = Instant.parse("2026-09-12T16:00:00Z"),
        )
        coEvery { parser.parse(any(), any()) } returns ParsedActions(
            rawTranscript = "Product review with Priya",
            actions = listOf(calendarAction),
            source = ParserSource.RULES,
            confidence = 1.0f,
        )
        coEvery { executor.execute(calendarAction) } returns ExecutionResult(
            actionId = "action-judge-pass-1",
            destination = Destination.CALENDAR,
            success = true,
            message = "Calendar event created",
        )

        val useCase = ExecuteVoiceCommandUseCase(
            parser = parser,
            executor = executor,
            quotaRepository = quotaRepository,
            historyRepository = historyRepository,
            entitlementProvider = provider,
        )

        assertFalse(provider.isPro())
        assertThrows(QuotaExceededException::class.java) {
            kotlinx.coroutines.runBlocking {
                useCase.execute("Product review with Priya")
            }
        }

        provider.setPro(true)

        assertTrue(provider.isPro())
        assertTrue(provider.observeIsPro().first())
        assertTrue(fallbackProvider.isPro())

        val voiceRunResult = useCase.execute("Product review with Priya")

        assertEquals(1, voiceRunResult.executed.size)
        assertTrue(voiceRunResult.executed[0].success)
        assertEquals("Calendar event created", voiceRunResult.executed[0].message)
        assertNull(voiceRunResult.remainingQuota)

        coVerify(exactly = 1) { quotaRepository.tryConsume(any()) }
        coVerify(exactly = 1) { executor.execute(calendarAction) }
    }

    private fun createMockCustomerInfo(proActive: Boolean): CustomerInfo {
        val info = mockk<CustomerInfo>()
        val proEntitlement = mockk<EntitlementInfo>()
        every { proEntitlement.isActive } returns proActive

        val entitlementsMock = mockk<EntitlementInfos>()
        every { entitlementsMock["pro"] } returns if (proActive) proEntitlement else null
        every { entitlementsMock["pro_access"] } returns null
        every { entitlementsMock["pro_monthly"] } returns null
        every { entitlementsMock["pro_yearly"] } returns null
        every { entitlementsMock["pro_annual"] } returns null
        every { entitlementsMock["premium"] } returns null
        every { entitlementsMock.all } returns if (proActive) mapOf("pro" to proEntitlement) else emptyMap()

        every { info.entitlements } returns entitlementsMock
        every { info.activeSubscriptions } returns emptySet()
        return info
    }

    private class InMemoryPreferencesDataStore(
        initial: Preferences = emptyPreferences(),
    ) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
