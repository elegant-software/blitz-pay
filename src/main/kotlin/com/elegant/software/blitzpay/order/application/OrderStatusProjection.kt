package com.elegant.software.blitzpay.order.application

import com.elegant.software.blitzpay.config.LogContext
import com.elegant.software.blitzpay.order.domain.OrderStatus
import com.elegant.software.blitzpay.order.repository.OrderRepository
import com.elegant.software.blitzpay.order.repository.PaymentAttemptRepository
import com.elegant.software.blitzpay.payments.push.api.PaymentStatusChanged
import com.elegant.software.blitzpay.payments.push.api.PaymentStatusCode
import org.slf4j.LoggerFactory
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderStatusProjection(
    private val orderRepository: OrderRepository,
    private val paymentAttemptRepository: PaymentAttemptRepository,
) {
    private val log = LoggerFactory.getLogger(OrderStatusProjection::class.java)

    @ApplicationModuleListener
    fun on(event: PaymentStatusChanged) {
        LogContext.with(
            LogContext.PAYMENT_REQUEST_ID to event.paymentRequestId,
            LogContext.EVENT_ID to event.sourceEventId,
        ) {
            val attempt = paymentAttemptRepository.findByPaymentRequestId(event.paymentRequestId)
            if (attempt == null) {
                log.warn("order projection skipped because payment attempt was not found")
                return
            }
            val order = orderRepository.findById(attempt.orderIdFk).orElse(null)
            if (order == null) {
                log.warn("order projection skipped because order was not found orderIdFk={}", attempt.orderIdFk)
                return
            }
            LogContext.with(
                LogContext.ORDER_ID to order.orderId,
                LogContext.PROVIDER to attempt.provider,
            ) {
                attempt.update(status = event.newStatus.name, at = event.occurredAt)
                paymentAttemptRepository.save(attempt)
                val nextStatus = when (event.newStatus) {
                    PaymentStatusCode.PENDING -> OrderStatus.PAYMENT_INITIATED
                    PaymentStatusCode.EXECUTED -> OrderStatus.PAYMENT_INITIATED
                    PaymentStatusCode.SETTLED -> OrderStatus.PAID
                    PaymentStatusCode.FAILED, PaymentStatusCode.EXPIRED -> OrderStatus.FAILED
                }
                log.info(
                    "project payment status currentOrderStatus={} paymentStatus={} nextOrderStatus={}",
                    order.status, event.newStatus, nextStatus,
                )
                if (order.applyPaymentUpdate(nextStatus, event.paymentRequestId, attempt.provider, event.occurredAt)) {
                    orderRepository.save(order)
                    log.info("order status updated status={} paidAt={}", order.status, order.paidAt)
                } else {
                    log.info("order status unchanged status={}", order.status)
                }
            }
        }
    }
}
