package com.elegant.software.quickpay.truelayer.api

import com.fasterxml.jackson.annotation.JsonProperty

data class TrueLayerPaymentRequest(
    @JsonProperty("expires_at")
    val expiresAt: String,

    val reference: String,

    @JsonProperty("return_uri")
    val returnUri: String,

    @JsonProperty("payment_configuration")
    val paymentConfiguration: PaymentConfiguration,

    @JsonProperty("product_items")
    val productItems: List<ProductItem>,

    val type: String = "single_payment"
) {
    data class PaymentConfiguration(
        @JsonProperty("amount_in_minor")
        val amountInMinor: Long,

        val currency: String,

        @JsonProperty("payment_method")
        val paymentMethod: PaymentMethod,

        val user: PaymentUser
    )

    data class PaymentMethod(
        val type: String = "bank_transfer",

        @JsonProperty("provider_selection")
        val providerSelection: ProviderSelection = ProviderSelection(),

        val beneficiary: Beneficiary
    )

    data class ProviderSelection(
        val type: String = "user_selected"
    )

    data class Beneficiary(
        val type: String = "merchant_account",

        @JsonProperty("merchant_account_id")
        val merchantAccountId: String
    )

    data class PaymentUser(
        val id: String,
        val name: String,
        val email: String? = null,
        val phone: String? = null,

        @JsonProperty("date_of_birth")
        val dateOfBirth: String? = null,

        val address: Address? = null
    )

    data class Address(
        @JsonProperty("address_line1")
        val addressLine1: String,

        val city: String,
        val state: String,
        val zip: String,

        @JsonProperty("country_code")
        val countryCode: String
    )

    data class ProductItem(
        val name: String,

        @JsonProperty("price_in_minor")
        val priceInMinor: Long,

        val quantity: Int,

        val url: String? = null
    )
}

data class TrueLayerPaymentResponse(
    val id: String,
    val uri: String
)