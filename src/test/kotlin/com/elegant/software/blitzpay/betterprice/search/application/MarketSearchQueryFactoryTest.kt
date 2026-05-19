package com.elegant.software.blitzpay.betterprice.search.application

import com.elegant.software.blitzpay.betterprice.search.config.MarketSearchProperties
import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchInput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MarketSearchQueryFactoryTest {

    private val factory = MarketSearchQueryFactory(
        MarketSearchProperties(enabled = true, maxHits = 5)
    )

    @Test
    fun `create builds query from strongest identifiers first`() {
        val query = factory.create(
            MarketSearchInput(
                productTitle = "Sony WH-1000XM5 Wireless Noise Canceling Headphones",
                brandName = "Sony",
                modelName = "WH-1000XM5",
                sku = "SONY-WH1000XM5-BLK",
                currency = "USD"
            )
        )

        assertEquals("Sony WH-1000XM5 Sony WH-1000XM5 Wireless Noise Canceling Headphones SONY-WH1000XM5-BLK", query.queryText)
        assertEquals(5, query.maxHits)
    }

    @Test
    fun `create rejects blank provider-neutral query input`() {
        assertThrows(IllegalArgumentException::class.java) {
            factory.create(MarketSearchInput(currency = "USD"))
        }
    }

    @Test
    fun `createQueries builds shopping intent variants`() {
        val queries = factory.createQueries(
            MarketSearchInput(
                productTitle = "Frontline Spot On Dog S Solution",
                brandName = "FRONTLINE",
                currency = "EUR"
            )
        )

        assertEquals(5, queries.size)
        assertEquals("FRONTLINE Frontline Spot On Dog S Solution", queries[0].queryText)
        assertEquals("FRONTLINE Frontline Spot On Dog S Solution price EUR", queries[1].queryText)
        assertEquals("buy FRONTLINE Frontline Spot On Dog S Solution", queries[2].queryText)
    }
}
