package es.kim.story

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StockBuyQuantityTest {
    @Test
    fun calculatesPercentageBuyQuantitiesFromAvailableMoney() {
        assertEquals(10, stockBuyQuantity(100_000L, 1_000L, 10))
        assertEquals(50, stockBuyQuantity(100_000L, 1_000L, 50))
        assertEquals(100, stockBuyQuantity(100_000L, 1_000L, 100))
    }

    @Test
    fun roundsDownAndDisablesUnaffordableBuys() {
        assertEquals(0, stockBuyQuantity(9_999L, 1_000L, 10))
        assertEquals(4, stockBuyQuantity(9_999L, 1_000L, 50))
        assertEquals(0, stockBuyQuantity(100_000L, 0L, 100))
    }

    @Test
    fun includesNewStocksWithUniqueIds() {
        assertEquals(15, virtualStocks.size)
        assertEquals(virtualStocks.size, virtualStocks.map { it.id }.distinct().size)
        assertTrue(
            virtualStocks.map { it.name }.containsAll(
                listOf("구마네 말랑이", "불새 철강", "누피 장례식", "환영 랜선", "네모 호두"),
            ),
        )
    }
}
