package com.elegant.software.quickpay.payments.truelayer.support

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.RemoteJWKSet
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import java.net.URL
import org.springframework.stereotype.Component

@Component
class TlSignatureVerifier(
    private val trueLayerProperties: TrueLayerProperties
) {
    fun verify(tlSignatureJws: String, rawBody: String) {
        try {
            val jws = SignedJWT.parse(tlSignatureJws)
            val jwkSource = RemoteJWKSet<SecurityContext>(URL(trueLayerProperties.webhookJku))

            val processor = DefaultJWTProcessor<SecurityContext>()
            processor.jwsKeySelector = JWSVerificationKeySelector(JWSAlgorithm.RS256, jwkSource)
            processor.process(jws, null)

            val actualPayload = jws.payload.toString()
            if (actualPayload != rawBody) {
                error("TrueLayer webhook payload mismatch")
            }
        } catch (e: Exception) {
            throw IllegalStateException("TrueLayer signature verification failed", e)
        }
    }
}
