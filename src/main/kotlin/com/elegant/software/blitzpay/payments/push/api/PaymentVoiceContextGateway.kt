package com.elegant.software.blitzpay.payments.push.api

interface PaymentVoiceContextGateway {
    fun findRecentPaymentsBySubject(subject: String, limit: Int = 5): List<RecentPaymentSummary>
}
