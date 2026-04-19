package com.elegant.software.blitzpay.merchant

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.merchant.domain.BusinessProfile
import com.elegant.software.blitzpay.merchant.domain.MerchantApplication
import com.elegant.software.blitzpay.merchant.domain.MerchantOnboardingStatus
import com.elegant.software.blitzpay.merchant.domain.PrimaryContact
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.Optional
import java.util.UUID

class MerchantContractTest : ContractVerifierBase() {

    @Test
    fun `POST merchants returns 201 and ACTIVE status for valid registration`() {
        whenever(merchantApplicationRepository.existsByBusinessProfileRegistrationNumberAndStatusIn(any(), any()))
            .thenReturn(false)
        whenever(merchantApplicationRepository.save(any<MerchantApplication>()))
            .thenAnswer { it.arguments[0] }

        webTestClient.post()
            .uri("/v1/merchants")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "legalBusinessName": "Acme GmbH",
                  "businessType": "LLC",
                  "registrationNumber": "DE123456789",
                  "operatingCountry": "DE",
                  "primaryBusinessAddress": "Hauptstrasse 1, 10115 Berlin",
                  "contactFullName": "Jane Doe",
                  "contactEmail": "jane@acme.de",
                  "contactPhoneNumber": "+4930123456"
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.status").isEqualTo(MerchantOnboardingStatus.ACTIVE.name)
            .jsonPath("$.applicationId").exists()
            .jsonPath("$.applicationReference").value<String> { ref ->
                require(ref.startsWith("BLTZ-")) { "Expected BLTZ- prefix, got $ref" }
            }
    }

    @Test
    fun `POST merchants returns 409 when registration number already active`() {
        whenever(merchantApplicationRepository.existsByBusinessProfileRegistrationNumberAndStatusIn(any(), any()))
            .thenReturn(true)

        webTestClient.post()
            .uri("/v1/merchants")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "legalBusinessName": "Duplicate GmbH",
                  "businessType": "LLC",
                  "registrationNumber": "DE-DUPLICATE",
                  "operatingCountry": "DE",
                  "primaryBusinessAddress": "Somestrasse 1",
                  "contactFullName": "John Smith",
                  "contactEmail": "john@dup.de",
                  "contactPhoneNumber": "+4930000000"
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isEqualTo(409)
    }

    @Test
    fun `GET merchant by id returns 200 when found`() {
        val application = MerchantApplication(
            applicationReference = "BLTZ-CONTRACT1",
            businessProfile = BusinessProfile(
                legalBusinessName = "Test GmbH",
                businessType = "LLC",
                registrationNumber = "DE-CONTRACT-001",
                operatingCountry = "DE",
                primaryBusinessAddress = "Teststrasse 1, Berlin"
            ),
            primaryContact = PrimaryContact(fullName = "Test User", email = "test@test.de", phoneNumber = "+49301234567")
        )
        whenever(merchantApplicationRepository.findById(application.id))
            .thenReturn(Optional.of(application))

        webTestClient.get()
            .uri("/v1/merchants/${application.id}")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.applicationId").isEqualTo(application.id.toString())
    }

    @Test
    fun `GET merchant by id returns 404 when not found`() {
        val unknownId = UUID.randomUUID()
        whenever(merchantApplicationRepository.findById(unknownId))
            .thenReturn(Optional.empty())

        webTestClient.get()
            .uri("/v1/merchants/$unknownId")
            .exchange()
            .expectStatus().isNotFound
    }
}
