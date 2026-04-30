package com.elegant.software.blitzpay.payments.push.internal

import com.elegant.software.blitzpay.order.repository.OrderRepository
import com.elegant.software.blitzpay.payments.push.api.DeviceRegistrationRequest
import com.elegant.software.blitzpay.payments.push.api.DeviceRegistrationResponse
import com.elegant.software.blitzpay.payments.push.persistence.DeviceRegistrationEntity
import com.elegant.software.blitzpay.payments.push.persistence.DeviceRegistrationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class RegistrationOutcome(val response: DeviceRegistrationResponse, val created: Boolean)

@Service
class DeviceRegistrationService(
    private val deviceRepository: DeviceRegistrationRepository,
    private val orderRepository: OrderRepository,
) {
    @Transactional
    fun register(request: DeviceRegistrationRequest): RegistrationOutcome {
        val orderId = requireNotNull(request.orderId).trim().takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("orderId must not be blank")
        val token = requireNotNull(request.expoPushToken)

        val order = orderRepository.findByOrderId(orderId)
            ?: throw OrderNotFoundException(orderId)

        val paymentRequestId = order.lastPaymentRequestId
        val existing = deviceRepository.findByExpoPushToken(token)
        return if (existing == null) {
            val entity = DeviceRegistrationEntity(
                id = UUID.randomUUID(),
                paymentRequestId = paymentRequestId,
                orderId = order.orderId,
                expoPushToken = token,
                platform = request.platform,
            )
            val saved = deviceRepository.save(entity)
            RegistrationOutcome(saved.toResponse(), created = true)
        } else {
            existing.paymentRequestId = paymentRequestId
            existing.orderId = order.orderId
            existing.platform = request.platform ?: existing.platform
            existing.lastSeenAt = Instant.now()
            existing.invalid = false
            val saved = deviceRepository.save(existing)
            RegistrationOutcome(saved.toResponse(), created = false)
        }
    }

    @Transactional
    fun unregister(expoPushToken: String) {
        deviceRepository.deleteByExpoPushToken(expoPushToken)
    }

    @Transactional
    fun markInvalid(expoPushToken: String) {
        deviceRepository.findByExpoPushToken(expoPushToken)?.let {
            it.invalid = true
            deviceRepository.save(it)
        }
    }

    private fun DeviceRegistrationEntity.toResponse() = DeviceRegistrationResponse(
        id = id,
        orderId = orderId,
        expoPushToken = expoPushToken,
        platform = platform,
    )
}

class OrderNotFoundException(val orderId: String) :
    RuntimeException("order $orderId not found")
