package com.elegant.software.blitzpay.betterprice.search.parser

import com.elegant.software.blitzpay.support.TestFixtureLoader
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class RetailerStructuredDataParserTest {

    private val parser = RetailerStructuredDataParser(jacksonObjectMapper().findAndRegisterModules())

    @Test
    fun `parse extracts product offer from json ld`() {
        val offer = parser.parse(
            TestFixtureLoader.marketSearchFixtureText("retailer-sony-techhub.html"),
            "https://example.test/offers/sony-wh1000xm5-techhub"
        )

        assertNotNull(offer)
        assertEquals("TechHub", offer?.sellerName)
        assertEquals(BigDecimal("279.99"), offer?.offerPrice)
        assertEquals("Sony", offer?.brandName)
        assertEquals("SONY-WH1000XM5-BLK", offer?.sku)
    }

    @Test
    fun `parse returns null when page has no structured offer data`() {
        val offer = parser.parse(
            TestFixtureLoader.marketSearchFixtureText("retailer-empty.html"),
            "https://example.test/offers/empty"
        )

        assertNull(offer)
    }

    @Test
    fun `parse falls back to visible title and price text`() {
        val offer = parser.parse(
            """
            <html>
              <head><title>Frontline Spot On Dog S Solution</title></head>
              <body>
                <h1>Frontline Spot On Dog S Solution</h1>
                <div class="price">EUR 29.99</div>
              </body>
            </html>
            """.trimIndent(),
            "https://example.test/frontline"
        )

        assertNotNull(offer)
        assertEquals("Frontline Spot On Dog S Solution", offer?.normalizedTitle)
        assertEquals(BigDecimal("29.99"), offer?.offerPrice)
        assertEquals("EUR", offer?.currency)
    }

    @Test
    fun `extract retailer links returns outbound buy urls`() {
        val links = parser.extractRetailerLinks(
            """
            <html>
              <body>
                <a href="https://shop.example.test/frontline-plus">Buy now</a>
                <a href="https://brand.example.test/about">About</a>
              </body>
            </html>
            """.trimIndent(),
            "https://brand.example.test/frontline-plus"
        )

        assertEquals(listOf("https://shop.example.test/frontline-plus"), links)
    }
}
