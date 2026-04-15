package com.elegant.software.blitzpay.payments.push.internal

import com.elegant.software.blitzpay.payments.push.api.DeviceRegistrationRequest
import com.elegant.software.blitzpay.payments.push.api.DeviceRegistrationResponse
import com.elegant.software.blitzpay.payments.push.persistence.DeviceRegistrationEntity
import com.elegant.software.blitzpay.payments.push.persistence.DeviceRegistrationRepository
import com.elegant.software.blitzpay.payments.push.persistence.PaymentStatusRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class RegistrationOutcome(val response: DeviceRegistrationResponse, val created: Boolean)

@Service
class DeviceRegistrationService(
    private val deviceRepository: DeviceRegistrationRepository,
    private val paymentStatusRepository: PaymentStatusRepository,
) {
    @Transactional
    fun register(request: DeviceRegistrationRequest): RegistrationOutcome {
        val paymentRequestId = requireNotNull(request.paymentRequestId)
        val token = requireNotNull(request.expoPushToken)

        if (!paymentStatusRepository.existsById(paymentRequestId)) {
            throw PaymentRequestNotFoundException(paymentRequestId)
        }

        val existing = deviceRepository.findByExpoPushToken(token)
        return if (existing == null) {
            val entity = DeviceRegistrationEntity(
                id = UUID.randomUUID(),
                paymentRequestId = paymentRequestId,
                expoPushToken = token,
                platform = request.platform,
            )
            val saved = deviceRepository.save(entity)
            RegistrationOutcome(saved.toResponse(), created = true)
        } else {
            existing.paymentRequestId = paymentRequestId
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
        paymentRequestId = paymentRequestId,
        expoPushToken = expoPushToken,
        platform = platform,
    )
}

class PaymentRequestNotFoundException(val paymentRequestId: UUID) :
    RuntimeException("payment request $paymentRequestId not found")