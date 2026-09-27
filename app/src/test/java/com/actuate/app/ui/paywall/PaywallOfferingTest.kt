package com.actuate.app.ui.paywall

import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.models.StoreProduct
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PaywallOfferingTest {

    @Test
    fun resolveOfferingPrioritizesDefaultOffering() {
        val defaultOffering = mockk<Offering>()
        every { defaultOffering.availablePackages } returns listOf(mockk())

        val currentOffering = mockk<Offering>()
        every { currentOffering.availablePackages } returns listOf(mockk())

        val offerings = mockk<Offerings>()
        every { offerings.getOffering("default") } returns defaultOffering
        every { offerings["default"] } returns defaultOffering
        every { offerings.all } returns mapOf("default" to defaultOffering, "current" to currentOffering)
        every { offerings.current } returns currentOffering

        val resolved = resolveOffering(offerings)
        assertEquals(defaultOffering, resolved)
    }

    @Test
    fun resolveOfferingFindsCaseInsensitiveDefaultOffering() {
        val defaultOffering = mockk<Offering>()
        every { defaultOffering.availablePackages } returns listOf(mockk())

        val offerings = mockk<Offerings>()
        every { offerings.getOffering("default") } returns null
        every { offerings["default"] } returns null
        every { offerings.all } returns mapOf("Default" to defaultOffering)
        every { offerings.current } returns null
        every { offerings.getOffering("pro") } returns null
        every { offerings.getOffering("active") } returns null

        val resolved = resolveOffering(offerings)
        assertEquals(defaultOffering, resolved)
    }

    @Test
    fun resolveOfferingFallsBackToCurrentWhenDefaultMissing() {
        val currentOffering = mockk<Offering>()
        every { currentOffering.availablePackages } returns listOf(mockk())

        val offerings = mockk<Offerings>()
        every { offerings.getOffering("default") } returns null
        every { offerings["default"] } returns null
        every { offerings.all } returns mapOf("other" to currentOffering)
        every { offerings.current } returns currentOffering

        val resolved = resolveOffering(offerings)
        assertEquals(currentOffering, resolved)
    }

    @Test
    fun resolvePackageForPlanMatchesAnnualAndMonthlyPackages() {
        val monthlyProduct = mockk<StoreProduct>()
        every { monthlyProduct.id } returns "actuate_pro_monthly"

        val monthlyPackage = mockk<Package>()
        every { monthlyPackage.identifier } returns "\$rc_monthly"
        every { monthlyPackage.product } returns monthlyProduct

        val annualProduct = mockk<StoreProduct>()
        every { annualProduct.id } returns "actuate_pro_annual"

        val annualPackage = mockk<Package>()
        every { annualPackage.identifier } returns "\$rc_annual"
        every { annualPackage.product } returns annualProduct

        val offering = mockk<Offering>()
        every { offering.monthly } returns monthlyPackage
        every { offering.annual } returns annualPackage
        every { offering.lifetime } returns null
        every { offering.availablePackages } returns listOf(monthlyPackage, annualPackage)

        val resolvedAnnual = resolvePackageForPlan(offering, PaywallPlan.ANNUAL)
        val resolvedMonthly = resolvePackageForPlan(offering, PaywallPlan.MONTHLY)

        assertEquals(annualPackage, resolvedAnnual)
        assertEquals(monthlyPackage, resolvedMonthly)
    }

    @Test
    fun resolvePackageForPlanMatchesCustomIdentifierOrProductId() {
        val annualProduct = mockk<StoreProduct>()
        every { annualProduct.id } returns "actuate_pro_annual_2026"

        val annualPackage = mockk<Package>()
        every { annualPackage.identifier } returns "custom_pro_yearly"
        every { annualPackage.product } returns annualProduct

        val offering = mockk<Offering>()
        every { offering.monthly } returns null
        every { offering.annual } returns null
        every { offering.lifetime } returns null
        every { offering.availablePackages } returns listOf(annualPackage)

        val resolved = resolvePackageForPlan(offering, PaywallPlan.ANNUAL)
        assertEquals(annualPackage, resolved)
    }

    @Test
    fun resolveOfferingFallsBackToOfferingWithPackagesIfDefaultIsEmpty() {
        val defaultOffering = mockk<Offering>()
        every { defaultOffering.availablePackages } returns emptyList()

        val testOffering = mockk<Offering>()
        every { testOffering.availablePackages } returns listOf(mockk())

        val offerings = mockk<Offerings>()
        every { offerings.getOffering("default") } returns defaultOffering
        every { offerings["default"] } returns defaultOffering
        every { offerings.all } returns mapOf("default" to defaultOffering, "test" to testOffering)
        every { offerings.current } returns null
        every { offerings.getOffering("test") } returns testOffering
        every { offerings.getOffering("pro") } returns null
        every { offerings.getOffering("active") } returns null

        val resolved = resolveOffering(offerings)
        assertEquals(testOffering, resolved)
    }

    @Test
    fun resolveOfferingReturnsDefaultEvenIfEmptyWhenNoOtherOfferingHasPackages() {
        val defaultOffering = mockk<Offering>()
        every { defaultOffering.availablePackages } returns emptyList()

        val offerings = mockk<Offerings>()
        every { offerings.getOffering("default") } returns defaultOffering
        every { offerings["default"] } returns defaultOffering
        every { offerings.all } returns mapOf("default" to defaultOffering)
        every { offerings.current } returns null
        every { offerings.getOffering("test") } returns null
        every { offerings.getOffering("pro") } returns null
        every { offerings.getOffering("active") } returns null

        val resolved = resolveOffering(offerings)
        assertEquals(defaultOffering, resolved)
    }

    @Test
    fun resolveOfferingReturnsNullForNullInput() {
        assertNull(resolveOffering(null))
    }
}
