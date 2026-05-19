package com.elegant.software.blitzpay.betterprice.search.parser

import com.elegant.software.blitzpay.betterprice.search.domain.ExtractedMarketOffer
import com.elegant.software.blitzpay.betterprice.search.domain.ExtractionEvidence
import com.elegant.software.blitzpay.betterprice.search.domain.ExtractionSourceType
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class RetailerStructuredDataParser(
    private val objectMapper: ObjectMapper
) {

    fun parse(html: String, pageUrl: String): ExtractedMarketOffer? {
        val document = Jsoup.parse(html, pageUrl)

        document.select("script[type=application/ld+json]").forEach { script ->
            val parsed = runCatching { objectMapper.readTree(script.data()) }.getOrNull() ?: return@forEach
            flatten(parsed).firstNotNullOfOrNull(::extractOffer)?.let { return it.copy(productUrl = pageUrl) }
        }

        return fallbackMetaExtraction(document, pageUrl)
            ?: fallbackVisibleTextExtraction(document, pageUrl)
    }

    fun diagnostics(html: String, pageUrl: String): String {
        val document = Jsoup.parse(html, pageUrl)
        val jsonLdScripts = document.select("script[type=application/ld+json]").size
        val hasMetaPrice = document.select("meta[property=product:price:amount],meta[itemprop=price]").isNotEmpty()
        val hasMetaCurrency = document.select("meta[property=product:price:currency],meta[itemprop=priceCurrency]").isNotEmpty()
        val title = document.title().ifBlank { "<blank>" }
        val excerpt = document.body().text().replace(Regex("\\s+"), " ").trim().let {
            if (it.length <= 300) it else it.take(300) + "...[truncated]"
        }
        return "title='$title', jsonLdScripts=$jsonLdScripts, hasMetaPrice=$hasMetaPrice, hasMetaCurrency=$hasMetaCurrency, bodyExcerpt='$excerpt'"
    }

    fun extractRetailerLinks(html: String, pageUrl: String): List<String> {
        val document = Jsoup.parse(html, pageUrl)
        val sourceHost = runCatching { URI(pageUrl).host.orEmpty().removePrefix("www.") }.getOrDefault("")

        return document.select("a[href]")
            .mapNotNull { anchor ->
                val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val text = listOf(
                    anchor.text(),
                    anchor.attr("aria-label"),
                    anchor.attr("title"),
                    anchor.className(),
                    anchor.id()
                ).joinToString(" ").lowercase()

                val targetHost = runCatching { URI(href).host.orEmpty().removePrefix("www.") }.getOrDefault("")
                val looksLikeRetailerAction = text.contains("buy") ||
                    text.contains("shop") ||
                    text.contains("retailer") ||
                    text.contains("where to buy") ||
                    text.contains("order") ||
                    text.contains("add to cart")

                if (!looksLikeRetailerAction || href.startsWith("http").not()) {
                    return@mapNotNull null
                }

                if (targetHost.isBlank() || targetHost == sourceHost) {
                    return@mapNotNull null
                }

                href
            }
            .distinct()
    }

    private fun fallbackMetaExtraction(document: Document, pageUrl: String): ExtractedMarketOffer? {
        val price = document.select("meta[property=product:price:amount],meta[itemprop=price]").firstOrNull()?.attr("content")
            ?: return null
        val currency = document.select("meta[property=product:price:currency],meta[itemprop=priceCurrency]").firstOrNull()?.attr("content")
            ?: return null
        val title = document.title().ifBlank { return null }

        return ExtractedMarketOffer(
            sellerName = document.location().substringAfter("://").substringBefore("/").ifBlank { "Unknown seller" },
            offerPrice = price.toBigDecimalOrNull() ?: return null,
            currency = currency,
            availability = true,
            productUrl = pageUrl,
            normalizedTitle = title,
            extractionEvidence = ExtractionEvidence(
                sourceType = ExtractionSourceType.META_TAGS,
                observedFields = listOf("price", "priceCurrency", "title"),
                pageTitle = title
            )
        )
    }

    private fun flatten(node: JsonNode): List<JsonNode> = when {
        node.isArray -> node.flatMap(::flatten)
        node.has("@graph") -> flatten(node.get("@graph"))
        else -> listOf(node)
    }

    private fun extractOffer(node: JsonNode): ExtractedMarketOffer? {
        if (!node.matchesType("Product")) {
            return if (node.matchesType("Offer")) extractDirectOffer(node) else null
        }

        val offers = node.get("offers") ?: return null
        val productName = node.path("name").asText(null) ?: return null
        val brandName = node.path("brand").textValueOrName()
        val modelName = node.path("model").asText(null)
        val sku = node.path("sku").asText(null)

        val offerNode = flatten(offers).firstOrNull { it.matchesType("Offer") || it.has("price") } ?: return null
        val price = offerNode.path("price").decimalValueOrNull() ?: return null
        val currency = offerNode.path("priceCurrency").asText(null) ?: return null

        return ExtractedMarketOffer(
            sellerName = offerNode.path("seller").textValueOrName()
                ?: offerNode.path("merchant").textValueOrName()
                ?: "Unknown seller",
            offerPrice = price,
            currency = currency,
            availability = !offerNode.path("availability").asText("").contains("OutOfStock", ignoreCase = true),
            productUrl = "",
            normalizedTitle = productName,
            brandName = brandName,
            modelName = modelName,
            sku = sku,
            extractionEvidence = ExtractionEvidence(
                sourceType = ExtractionSourceType.JSON_LD,
                observedFields = buildList {
                    add("name")
                    if (brandName != null) add("brand")
                    if (modelName != null) add("model")
                    if (sku != null) add("sku")
                    add("price")
                    add("priceCurrency")
                },
                pageTitle = productName,
                sellerHint = offerNode.path("seller").textValueOrName()
            )
        )
    }

    private fun extractDirectOffer(node: JsonNode): ExtractedMarketOffer? {
        val price = node.path("price").decimalValueOrNull() ?: return null
        val currency = node.path("priceCurrency").asText(null) ?: return null
        val title = node.path("name").asText("Offer")

        return ExtractedMarketOffer(
            sellerName = node.path("seller").textValueOrName() ?: "Unknown seller",
            offerPrice = price,
            currency = currency,
            availability = !node.path("availability").asText("").contains("OutOfStock", ignoreCase = true),
            productUrl = "",
            normalizedTitle = title,
            extractionEvidence = ExtractionEvidence(
                sourceType = ExtractionSourceType.JSON_LD,
                observedFields = listOf("price", "priceCurrency"),
                pageTitle = title,
                sellerHint = node.path("seller").textValueOrName()
            )
        )
    }

    private fun fallbackVisibleTextExtraction(document: Document, pageUrl: String): ExtractedMarketOffer? {
        val currency = detectCurrency(document) ?: return null
        val priceText = visiblePriceCandidates(document)
            .firstNotNullOfOrNull { candidate -> candidate.normalizedPriceOrNull() }
            ?: return null
        val title = candidateTitle(document) ?: return null

        return ExtractedMarketOffer(
            sellerName = sellerName(document),
            offerPrice = priceText,
            currency = currency,
            availability = !document.body().text().contains("out of stock", ignoreCase = true),
            productUrl = pageUrl,
            normalizedTitle = title,
            extractionEvidence = ExtractionEvidence(
                sourceType = ExtractionSourceType.FALLBACK_TEXT,
                observedFields = listOf("visible_title", "visible_price", "currency_hint"),
                pageTitle = document.title().ifBlank { title },
                sellerHint = sellerName(document)
            )
        )
    }

    private fun detectCurrency(document: Document): String? {
        val text = buildString {
            append(document.title())
            append(' ')
            append(document.body().text())
        }

        return when {
            "EUR" in text.uppercase() || "€" in text -> "EUR"
            "GBP" in text.uppercase() || "£" in text -> "GBP"
            "USD" in text.uppercase() || "$" in text -> "USD"
            else -> null
        }
    }

    private fun visiblePriceCandidates(document: Document): List<String> = buildList {
        addAll(
            document.select(
                "[data-price],[data-product-price],[class*=price],[id*=price],[itemprop=price],span,div,p"
            ).mapNotNull { it.attr("data-price").ifBlank { it.text() }.takeIf(String::isNotBlank) }
        )
        add(document.body().text())
    }

    private fun candidateTitle(document: Document): String? = sequenceOf(
        document.select("meta[property=og:title]").firstOrNull()?.attr("content"),
        document.select("meta[name=twitter:title]").firstOrNull()?.attr("content"),
        document.select("h1").firstOrNull()?.text(),
        document.title().ifBlank { null }
    ).firstOrNull { !it.isNullOrBlank() }?.trim()

    private fun sellerName(document: Document): String =
        document.location().substringAfter("://").substringBefore("/").removePrefix("www.").ifBlank { "Unknown seller" }
}

private fun JsonNode.matchesType(type: String): Boolean = when {
    !has("@type") -> false
    path("@type").isArray -> path("@type").any { it.asText("").equals(type, ignoreCase = true) }
    else -> path("@type").asText("").equals(type, ignoreCase = true)
}

private fun JsonNode.textValueOrName(): String? = when {
    isTextual -> asText()
    has("name") -> path("name").asText(null)
    else -> null
}

private fun JsonNode.decimalValueOrNull(): BigDecimal? = when {
    isTextual -> asText(null)?.toBigDecimalOrNull()
    isNumber -> decimalValue()
    else -> asText(null)?.toBigDecimalOrNull()
}

private fun String.normalizedPriceOrNull(): BigDecimal? {
    val match = Regex("""(?:EUR|GBP|USD)?\s*([€£$]?\s*\d{1,4}(?:[.,]\d{2})?)""", RegexOption.IGNORE_CASE)
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?: return null

    val normalized = match
        .replace("€", "")
        .replace("£", "")
        .replace("$", "")
        .replace(",", ".")
        .replace(" ", "")

    return normalized.toBigDecimalOrNull()
}
