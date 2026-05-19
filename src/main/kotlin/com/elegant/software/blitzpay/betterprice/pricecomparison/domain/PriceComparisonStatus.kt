package com.elegant.software.blitzpay.betterprice.pricecomparison.domain

import com.fasterxml.jackson.annotation.JsonValue

enum class PriceComparisonStatus(private val wireValue: String) {
    BETTER_PRICE_FOUND("better_price_found"),
    NO_BETTER_PRICE_FOUND("no_better_price_found"),
    COMPARISON_UNAVAILABLE("comparison_unavailable");

    @JsonValue
    fun wireValue(): String = wireValue
}

enum class MatchConfidence(private val wireValue: String) {
    HIGH("high"),
    MEDIUM("medium"),
    LOW("low");

    @JsonValue
    fun wireValue(): String = wireValue
}

enum class ExplanationCode(private val wireValue: String) {
    BETTER_PRICE_AVAILABLE("better_price_available"),
    NO_CHEAPER_QUALIFYING_OFFER("no_cheaper_qualifying_offer"),
    NO_COMPARABLE_OFFERS("no_comparable_offers"),
    LOW_MATCH_CONFIDENCE("low_match_confidence"),
    PROVIDER_LOOKUP_FAILED("provider_lookup_failed"),
    AGENT_EXECUTION_FAILED("agent_execution_failed"),
    INVALID_REQUEST("invalid_request");

    @JsonValue
    fun wireValue(): String = wireValue
}

enum class OfferQualificationStatus(private val wireValue: String) {
    QUALIFIED("qualified"),
    NOT_LOWER_PRICE("not_lower_price"),
    UNAVAILABLE("unavailable"),
    WEAK_MATCH("weak_match");

    @JsonValue
    fun wireValue(): String = wireValue
}

enum class MonitoringStage(private val wireValue: String) {
    RECEIVED("received"),
    VALIDATION("validation"),
    SEARCH_DISCOVERY("search_discovery"),
    OFFER_EXTRACTION("offer_extraction"),
    OFFER_LOOKUP("offer_lookup"),
    MATCHING("matching"),
    COMPARISON("comparison"),
    COMPLETED("completed"),
    FAILED("failed");

    @JsonValue
    fun wireValue(): String = wireValue
}

enum class MonitoringWarningCode(private val wireValue: String) {
    PARTIAL_RESULTS("partial_results"),
    LOW_MATCH_CONFIDENCE("low_match_confidence"),
    UNAVAILABLE_OFFERS("unavailable_offers"),
    INPUT_NORMALIZED("input_normalized");

    @JsonValue
    fun wireValue(): String = wireValue
}

enum class MonitoringFailureCode(private val wireValue: String) {
    INVALID_REQUEST("invalid_request"),
    PROVIDER_LOOKUP_FAILED("provider_lookup_failed"),
    AGENT_EXECUTION_FAILED("agent_execution_failed"),
    UNEXPECTED_ERROR("unexpected_error");

    @JsonValue
    fun wireValue(): String = wireValue
}
