@file:ApplicationModule(displayName = "outbound")

package com.elegant.software.quickpay.payments.qrpay

import TlWebhookEnvelope
import com.elegant.software.quickpay.payments.support.PaymentUpdateBus
import com.elegant.software.quickpay.payments.truelayer.api.PaymentResult
import com.elegant.software.quickpay.payments.truelayer.outbound.PaymentService
import mu.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.modulith.ApplicationModule
import org.springframework.stereotype.Component
import java.util.*

@Component
class PaymentInitRequestListener(
    private val gateway: PaymentService,
    private val paymentUpdateBus: PaymentUpdateBus
) {
    companion object {
        private val LOG = KotlinLogging.logger {}
    }

    @EventListener
    fun on(e: PaymentResult) {
        LOG.info("Pyment result received {}", e)
        paymentUpdateBus.emit(e.paymentRequestId, e)
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