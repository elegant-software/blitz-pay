package com.elegant.software.blitzpay.betterprice.search.domain

import com.fasterxml.jackson.annotation.JsonValue

enum class ExtractionSourceType(private val wireValue: String) {
    JSON_LD("json_ld"),
    MICRODATA("microdata"),
    META_TAGS("meta_tags"),
    FALLBACK_TEXT("fallback_text");

    @JsonValue
    fun wireValue(): String = wireValue
}

enum class MarketSearchStage(private val wireValue: String) {
    SEARCH_DISCOVERY("search_discovery"),
    OFFER_EXTRACTION("offer_extraction");

    @JsonValue
    fun wireValue(): String = wireValue
}
