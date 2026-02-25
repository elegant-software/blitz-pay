package com.elegant.software.blitzpay.support

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.elegant.software.blitzpay.invoice.api.InvoiceData

object TestFixtureLoader {

    private val objectMapper: JsonMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .addModule(JavaTimeModule())
        .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build()

    val fixture: InvoiceTestFixture by lazy {
        val resource = requireNotNull(this::class.java.classLoader.getResourceAsStream(FIXTURE_PATH)) {
            "Missing fixture file: $FIXTURE_PATH"
        }
        resource.use { objectMapper.readValue(it) }
    }

    fun invoiceRequestJson(): String = objectMapper.writeValueAsString(fixture.invoiceRequest)

    fun invoiceData(): InvoiceData = fixture.invoiceRequest

    private const val FIXTURE_PATH = "testdata/invoice-test-data.json"
}

data class InvoiceTestFixture(
    val invoiceRequest: InvoiceData,
    val expectations: InvoiceExpectations,
)

data class InvoiceExpectations(
    val xmlRootElement: String,
    val invoiceNumber: String,
    val sellerName: String,
    val buyerName: String,
    val firstLineItemDescription: String,
    val secondLineItemDescription: String,
    val currency: String,
    val bankName: String,
    val iban: String,
    val bic: String,
    val footerText: String,
    val logoBase64: String,
    val singleLineItemDescription: String,
    val singleLineItemQuantity: String,
    val singleLineItemUnitPrice: String,
    val singleLineItemVatPercent: String,
)
