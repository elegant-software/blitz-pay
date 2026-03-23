package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.domain.BusinessProfile
import com.elegant.software.blitzpay.merchant.domain.MerchantApplication
import com.elegant.software.blitzpay.merchant.domain.Person
import com.elegant.software.blitzpay.merchant.domain.PersonRole
import com.elegant.software.blitzpay.merchant.domain.PrimaryContact
import com.elegant.software.blitzpay.merchant.domain.SupportingMaterial
import com.elegant.software.blitzpay.merchant.domain.SupportingMaterialType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MerchantRedactionServiceTest {

    private val redactionService = MerchantRedactionService()

    @Test
    fun `redacts contact and registration details`() {
        val redacted = redactionService.redact(
            MerchantApplication(
                applicationReference = "MO-REDACT-1",
                businessProfile = BusinessProfile(
                    legalBusinessName = "Acme GmbH",
                    businessType = "LIMITED_COMPANY",
                    registrationNumber = "HRB123456",
                    operatingCountry = "DE",
                    primaryBusinessAddress = "Berlin"
                ),
                primaryContact = PrimaryContact(
                    fullName = "Mina Example",
                    email = "mina@example.com",
                    phoneNumber = "+49123456789"
                )
            ).apply {
                addPerson(
                    Person(
                        fullName = "Mina Example",
                        role = PersonRole.BENEFICIAL_OWNER,
                        countryOfResidence = "DE",
                        ownershipPercentage = 100
                    )
                )
                addSupportingMaterial(
                    SupportingMaterial(
                        type = SupportingMaterialType.BUSINESS_REGISTRATION,
                        fileName = "registration.pdf",
                        storageKey = "merchant/MO-REDACT-1/registration.pdf"
                    )
                )
            }
        )

        assertEquals("HRB***", redacted.registrationNumber)
        assertEquals("m***@example.com", redacted.primaryContactEmail)
        assertTrue(redacted.primaryContactPhone.endsWith("6789"))
        assertEquals(1, redacted.peopleCount)
        assertEquals(1, redacted.supportingMaterialCount)
    }
}
