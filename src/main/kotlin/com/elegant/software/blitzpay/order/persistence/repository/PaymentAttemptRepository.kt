package com.elegant.software.blitzpay.order.repository

import com.elegant.software.blitzpay.order.domain.PaymentAttempt
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PaymentAttemptRepository : JpaRepository<PaymentAttempt, UUID> {
    fun findByPaymentRequestId(paymentRequestId: UUID): PaymentAttempt?
}
