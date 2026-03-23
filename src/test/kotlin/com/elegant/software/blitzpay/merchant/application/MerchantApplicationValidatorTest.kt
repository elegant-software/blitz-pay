package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.domain.BusinessProfile
import com.elegant.software.blitzpay.merchant.domain.MerchantApplication
import com.elegant.software.blitzpay.merchant.domain.Person
import com.elegant.software.blitzpay.merchant.domain.PersonRole
import com.elegant.software.blitzpay.merchant.domain.PrimaryContact
import com.elegant.software.blitzpay.merchant.domain.SupportingMaterial
import com.elegant.software.blitzpay.merchant.domain.SupportingMaterialType
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class MerchantApplicationValidatorTest {

    private val validator = MerchantApplicationValidator()

    @Test
    fun `accepts a submission-ready merchant application`() {
        validator.validateForSubmission(validApplication())
    }

    @Test
    fun `rejects application without beneficial owner`() {
        val application = validApplication().apply {
            people.clear()
        }

        assertFailsWith<IllegalArgumentException> {
            validator.validateForSubmission(application)
        }
    }

    @Test
    fun `rejects application without business registration document`() {
        val application = validApplication().apply {
            supportingMaterials.clear()
        }

        assertFailsWith<IllegalArgumentException> {
            validator.validateForSubmission(application)
        }
    }

    private fun validApplication() = MerchantApplication(
        applicationReference = "MO-VALID-1",
        businessProfile = BusinessProfile(
            legalBusinessName = "Acme GmbH",
            businessType = "LIMITED_COMPANY",
            registrationNumber = "HRB123456",
            operatingCountry = "DE",
            primaryBusinessAddress = "Alexanderplatz 1, Berlin"
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
                storageKey = "merchant/MO-VALID-1/registration.pdf"
            )
        )
    }
}
