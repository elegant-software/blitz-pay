@file:ApplicationModule(displayName = "outbound")

package com.elegant.software.blitzpay.payments.qrpay

import TlWebhookEnvelope
import com.elegant.software.blitzpay.payments.support.PaymentUpdateBus
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.modulith.ApplicationModule
import org.springframework.stereotype.Component
import java.util.*

@Component
class PaymentInitRequestListener(
    private val paymentUpdateBus: PaymentUpdateBus
) {
    companion object {
        private val LOG = LoggerFactory.getLogger(PaymentInitRequestListener::class.java)
    }

    @EventListener
    fun on(e: TlWebhookEnvelope) {
        LOG.info("Webhook Informed TrueLayer module {}", e)
        val paymentRequestId = e.metadata?.get("paymentRequestId")
        if (paymentRequestId is String) {
            val uuid = UUID.fromString(paymentRequestId)
            paymentUpdateBus.complete(uuid)
        }
    }
}
