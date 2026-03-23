package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.domain.BusinessProfile
import com.elegant.software.blitzpay.merchant.domain.MerchantApplication
import com.elegant.software.blitzpay.merchant.domain.PrimaryContact
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class MerchantAccessPolicyTest {

    private val policy = MerchantAccessPolicy()

    @Test
    fun `merchant applicant can read own application`() {
        policy.requireReadAccess(
            MerchantActor(
                actorId = "merchant-1",
                role = MerchantActorRole.MERCHANT_APPLICANT,
                merchantApplicationReference = "MO-ACCESS-1"
            ),
            application()
        )
    }

    @Test
    fun `merchant applicant cannot read someone elses application`() {
        assertFailsWith<IllegalArgumentException> {
            policy.requireReadAccess(
                MerchantActor(
                    actorId = "merchant-2",
                    role = MerchantActorRole.MERCHANT_APPLICANT,
                    merchantApplicationReference = "OTHER-REF"
                ),
                application()
            )
        }
    }

    @Test
    fun `review access is limited to reviewer roles`() {
        assertFailsWith<IllegalArgumentException> {
            policy.requireReviewAccess(
                MerchantActor(
                    actorId = "merchant-1",
                    role = MerchantActorRole.MERCHANT_APPLICANT,
                    merchantApplicationReference = "MO-ACCESS-1"
                )
            )
        }
    }

    private fun application() = MerchantApplication(
        applicationReference = "MO-ACCESS-1",
        businessProfile = BusinessProfile(
            legalBusinessName = "Acme GmbH",
            businessType = "LIMITED_COMPANY",
            registrationNumber = "HRB777777",
            operatingCountry = "DE",
            primaryBusinessAddress = "Berlin"
        ),
        primaryContact = PrimaryContact(
            fullName = "Mina Example",
            email = "mina@example.com",
            phoneNumber = "+49123456789"
        )
    )
}
