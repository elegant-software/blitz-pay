package com.elegant.software.blitzpay.merchant

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.merchant.domain.BusinessProfile
import com.elegant.software.blitzpay.merchant.domain.MerchantApplication
import com.elegant.software.blitzpay.merchant.domain.MerchantOnboardingStatus
import com.elegant.software.blitzpay.merchant.domain.MerchantProduct
import com.elegant.software.blitzpay.merchant.domain.PrimaryContact
import jakarta.persistence.Query
import org.hibernate.Filter
import org.hibernate.Session
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import java.util.Optional
import java.util.UUID
import java.math.BigDecimal

class MerchantContractTest : ContractVerifierBase() {
    private val session = mock<Session>()
    private val hibernateFilter = mock<Filter>()
    private val nativeQuery = mock<Query>()

    @BeforeEach
    fun setupMerchantContracts() {
        whenever(entityManager.unwrap(Session::class.java)).thenReturn(session)
        whenever(session.enableFilter(any())).thenReturn(hibernateFilter)
        whenever(hibernateFilter.setParameter(any<String>(), any())).thenReturn(hibernateFilter)
        whenever(entityManager.createNativeQuery(any<String>())).thenReturn(nativeQuery)
        whenever(nativeQuery.setParameter(any<String>(), any())).thenReturn(nativeQuery)
        whenever(nativeQuery.singleResult).thenReturn("contract")
        whenever(storageService.presignDownload(any(), any())).thenAnswer { "https://signed.example/${it.arguments[0]}" }
    }

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
                primaryBusinessAddress = "Teststrasse 1, Berlin",
                logoStorageKey = "merchants/logo/test.webp"
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
            .jsonPath("$.legalBusinessName").isEqualTo("Test GmbH")
            .jsonPath("$.contactEmail").isEqualTo("test@test.de")
            .jsonPath("$.logoUrl").isEqualTo("https://signed.example/merchants/logo/test.webp")
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

    @Test
    fun `PUT merchant updates editable fields and status`() {
        val application = MerchantApplication(
            applicationReference = "BLTZ-CONTRACT2",
            businessProfile = BusinessProfile(
                legalBusinessName = "Before GmbH",
                businessType = "LLC",
                registrationNumber = "DE-CONTRACT-002",
                operatingCountry = "DE",
                primaryBusinessAddress = "Old Strasse 1"
            ),
            primaryContact = PrimaryContact(fullName = "Before User", email = "before@test.de", phoneNumber = "+49301230000")
        )
        whenever(merchantApplicationRepository.findById(application.id))
            .thenReturn(Optional.of(application))
        whenever(merchantApplicationRepository.save(any<MerchantApplication>()))
            .thenAnswer { it.arguments[0] }

        webTestClient.put()
            .uri("/v1/merchants/${application.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "legalBusinessName": "After GmbH",
                  "primaryBusinessAddress": "New Strasse 9",
                  "contactFullName": "After User",
                  "contactEmail": "after@test.de",
                  "contactPhoneNumber": "+49309999999",
                  "activePaymentChannels": ["STRIPE", "TRUELAYER"],
                  "status": "ACTIVE"
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.legalBusinessName").isEqualTo("After GmbH")
            .jsonPath("$.primaryBusinessAddress").isEqualTo("New Strasse 9")
            .jsonPath("$.contactEmail").isEqualTo("after@test.de")
            .jsonPath("$.activePaymentChannels.length()").isEqualTo(2)
            .jsonPath("$.status").isEqualTo(MerchantOnboardingStatus.ACTIVE.name)
    }

    @Test
    fun `PUT branch updates branch details and contact`() {
        val merchantId = UUID.randomUUID()
        val branchId = UUID.randomUUID()
        whenever(merchantApplicationRepository.existsById(merchantId)).thenReturn(true)
        whenever(merchantBranchRepository.findById(branchId)).thenReturn(
            Optional.of(
                com.elegant.software.blitzpay.merchant.domain.MerchantBranch(
                    id = branchId,
                    merchantApplicationId = merchantId,
                    name = "Old Branch"
                )
            )
        )
        whenever(merchantBranchRepository.save(any<com.elegant.software.blitzpay.merchant.domain.MerchantBranch>()))
            .thenAnswer { it.arguments[0] }

        webTestClient.put()
            .uri("/v1/merchants/$merchantId/branches/$branchId")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "name": "Updated Branch",
                  "active": true,
                  "addressLine1": "Main Street 1",
                  "city": "Berlin",
                  "postalCode": "10115",
                  "country": "DE",
                  "contactFullName": "Store Manager",
                  "contactEmail": "branch@test.de",
                  "contactPhoneNumber": "+49305555555",
                  "activePaymentChannels": ["PAYPAL", "TRUELAYER"]
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.name").isEqualTo("Updated Branch")
            .jsonPath("$.contactFullName").isEqualTo("Store Manager")
            .jsonPath("$.contactEmail").isEqualTo("branch@test.de")
            .jsonPath("$.activePaymentChannels.length()").isEqualTo(2)
    }

    @Test
    fun `PUT branch image stores storage key and returns signed image url`() {
        val merchantId = UUID.randomUUID()
        val branchId = UUID.randomUUID()
        whenever(merchantApplicationRepository.existsById(merchantId)).thenReturn(true)
        whenever(merchantBranchRepository.findById(branchId)).thenReturn(
            Optional.of(
                com.elegant.software.blitzpay.merchant.domain.MerchantBranch(
                    id = branchId,
                    merchantApplicationId = merchantId,
                    name = "Image Branch"
                )
            )
        )
        whenever(merchantBranchRepository.save(any<com.elegant.software.blitzpay.merchant.domain.MerchantBranch>()))
            .thenAnswer { it.arguments[0] }

        webTestClient.put()
            .uri("/v1/merchants/$merchantId/branches/$branchId/image")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"storageKey":"merchants/$merchantId/branches/$branchId/image.webp"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.imageUrl").isEqualTo("https://signed.example/merchants/$merchantId/branches/$branchId/image.webp")
    }

    @Test
    fun `POST product accepts multipart product fields and image`() {
        val merchantId = UUID.randomUUID()
        val branchId = UUID.randomUUID()
        whenever(merchantApplicationRepository.existsById(merchantId)).thenReturn(true)
        whenever(merchantBranchRepository.existsByMerchantApplicationIdAndIdAndActiveTrue(merchantId, branchId)).thenReturn(true)
        whenever(merchantProductRepository.save(any<MerchantProduct>())).thenAnswer { it.arguments[0] }
        val multipart = MultipartBodyBuilder().apply {
            part("name", "Coffee Blend")
            part("branchId", branchId.toString())
            part("description", "**Medium roast**")
            part("unitPrice", "12.50")
            part("image", byteArrayOf(1, 2, 3))
                .filename("coffee.webp")
                .contentType(MediaType("image", "webp"))
        }

        webTestClient.post()
            .uri("/v1/merchants/$merchantId/products")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .bodyValue(multipart.build())
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.name").isEqualTo("Coffee Blend")
            .jsonPath("$.description").isEqualTo("**Medium roast**")
            .jsonPath("$.imageUrl").exists()
    }

    @Test
    fun `GET products returns signed image url and description`() {
        val merchantId = UUID.randomUUID()
        val branchId = UUID.randomUUID()
        whenever(merchantApplicationRepository.existsById(merchantId)).thenReturn(true)
        whenever(entityManager.unwrap(Session::class.java)).thenReturn(session)
        whenever(session.enableFilter(any())).thenReturn(hibernateFilter)
        whenever(hibernateFilter.setParameter(any<String>(), any())).thenReturn(hibernateFilter)
        whenever(entityManager.createNativeQuery(any<String>())).thenReturn(nativeQuery)
        whenever(nativeQuery.setParameter(any<String>(), any())).thenReturn(nativeQuery)
        whenever(nativeQuery.singleResult).thenReturn(merchantId.toString())
        whenever(merchantProductRepository.findAllByActiveTrueAndMerchantBranchId(branchId)).thenReturn(
            listOf(
                MerchantProduct(
                    merchantApplicationId = merchantId,
                    merchantBranchId = branchId,
                    name = "Coffee Blend",
                    description = "**Medium roast**",
                    unitPrice = BigDecimal("12.50"),
                    imageStorageKey = "merchants/$merchantId/branches/$branchId/products/product/image.webp"
                )
            )
        )

        webTestClient.get()
            .uri("/v1/merchants/$merchantId/products?branchId=$branchId")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].description").isEqualTo("**Medium roast**")
            .jsonPath("$[0].imageUrl").isEqualTo("https://signed.example/merchants/$merchantId/branches/$branchId/products/product/image.webp")
    }

    @Test
    fun `POST product rejects unsupported image type`() {
        val merchantId = UUID.randomUUID()
        val branchId = UUID.randomUUID()
        whenever(merchantApplicationRepository.existsById(merchantId)).thenReturn(true)
        val multipart = MultipartBodyBuilder().apply {
            part("name", "Coffee Blend")
            part("branchId", branchId.toString())
            part("unitPrice", "12.50")
            part("image", "not an image".toByteArray())
                .filename("notes.txt")
                .contentType(MediaType.TEXT_PLAIN)
        }

        webTestClient.post()
            .uri("/v1/merchants/$merchantId/products")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .bodyValue(multipart.build())
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").exists()
    }
}
