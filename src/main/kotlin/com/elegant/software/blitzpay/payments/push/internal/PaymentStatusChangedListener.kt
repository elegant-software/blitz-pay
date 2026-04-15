package com.elegant.software.blitzpay.payments.push.internal

import com.elegant.software.blitzpay.payments.push.api.PaymentStatusChanged
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
class PaymentStatusChangedListener(
    private val dispatcher: PushDispatcher,
) {
    @ApplicationModuleListener
    fun on(event: PaymentStatusChanged) {
        dispatcher.dispatch(event)
    }
}
