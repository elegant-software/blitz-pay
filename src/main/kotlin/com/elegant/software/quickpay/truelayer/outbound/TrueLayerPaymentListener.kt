@file:ApplicationModule(displayName = "outbound")

package com.elegant.software.quickpay.truelayer.outbound

import com.elegant.software.quickpay.truelayer.api.PaymentRequested
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.modulith.ApplicationModule
import org.springframework.stereotype.Component

@Component
class TrueLayerPaymentRequestListener(
    private val gateway: PaymentService,
    private val applicationEventPublisher: ApplicationEventPublisher
) {


    @EventListener
    fun on(e: PaymentRequested) {
        val paymentResult=gateway.startPayment(
            PaymentRequested(paymentRequestId =e.paymentRequestId,  e.orderId, e.amount, e.currency, e.userDisplayName, e.redirectReturnUri)
        )
        applicationEventPublisher.publishEvent(paymentResult)
    }
}