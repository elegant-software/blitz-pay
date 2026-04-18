package com.elegant.software.blitzpay.payments.braintree

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals

class BraintreeLocaleFormattingTest {

    @Test
    fun `amount is formatted with dot even in German locale`() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val amount = 89.0
            
            // This is what was failing:
            // val formattedAmount = "%.2f".format(amount) 
            // In GERMANY locale, it would be "89,00"
            
            val formattedAmount = "%.2f".format(Locale.US, amount)
            assertEquals("89.00", formattedAmount)
            
            val bigDecimal = BigDecimal(formattedAmount)
            assertEquals(BigDecimal("89.00"), bigDecimal)
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
