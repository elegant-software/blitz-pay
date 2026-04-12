package com.elegant.software.blitzpay.invoice.internal

/**
 * Payment lifecycle states for a stored invoice.
 *
 * `PAID` means the payment action completed on the payer side.
 * `RECEIVED` is reserved for downstream confirmation that the funds were received.
 */
enum class PaymentStatus {
    PENDING,
    PAID,
    RECEIVED
}
