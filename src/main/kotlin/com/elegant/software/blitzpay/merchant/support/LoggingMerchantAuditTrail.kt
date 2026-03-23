package com.elegant.software.blitzpay.merchant.support

import com.elegant.software.blitzpay.merchant.application.MerchantAuditEvent
import com.elegant.software.blitzpay.merchant.application.MerchantAuditTrail
import mu.KotlinLogging
import org.springframework.stereotype.Component

@Component
class LoggingMerchantAuditTrail : MerchantAuditTrail {
    private val logger = KotlinLogging.logger {}

    override fun record(event: MerchantAuditEvent) {
        logger.info {
            "merchant_audit action=${event.action} actorId=${event.actorId} applicationId=${event.applicationId} " +
                "applicationReference=${event.applicationReference} status=${event.status} details=${event.details}"
        }
    }
}
