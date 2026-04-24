package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.SetMerchantLocationRequest
import com.elegant.software.blitzpay.merchant.domain.BusinessProfile
import com.elegant.software.blitzpay.merchant.domain.MerchantApplication
import com.elegant.software.blitzpay.merchant.domain.PrimaryContact
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantBranchRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional
import kotlin.test.assertEquals

class MerchantLocationServiceTest {
    private val repository = mock<MerchantApplicationRepository>()
    private val merchantBranchRepository = mock<MerchantBranchRepository>()
    private val service = MerchantLocationService(repository, merchantBranchRepository)

    @Test
    fun `set location stores google place id for asynchronous enrichment`() {
        val merchant = MerchantApplication(
            applicationReference = "BLTZ-LOC",
            businessProfile = BusinessProfile(
                legalBusinessName = "Location GmbH",
                businessType = "LLC",
                registrationNumber = "DE-LOC",
                operatingCountry = "DE",
                primaryBusinessAddress = "Legal Strasse 1"
            ),
            primaryContact = PrimaryContact("Jane Doe", "jane@example.com", "+491234")
        )
        whenever(repository.findById(merchant.id)).thenReturn(Optional.of(merchant))
        whenever(repository.save(any<MerchantApplication>())).thenAnswer { it.arguments[0] }

        val response = service.setLocation(
            merchant.id,
            SetMerchantLocationRequest(
                latitude = 53.0758,
                longitude = 8.8072,
                geofenceRadiusMeters = 250,
                googlePlaceId = "ChIJ-test"
            )
        )

        assertEquals("ChIJ-test", response.googlePlaceId)
        assertEquals("PENDING", response.placeEnrichmentStatus)
    }
}
