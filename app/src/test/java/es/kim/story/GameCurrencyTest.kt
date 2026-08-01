package es.kim.story

import org.junit.Assert.assertEquals
import org.junit.Test

class GameCurrencyTest {
    @Test
    fun displaysPrimaryAndSecondaryCurrencyUnits() {
        assertEquals("1루비 50골드", formatGameCurrency(100_500_000L))
        assertEquals("2골드 350실버", formatGameCurrency(20_350L))
        assertEquals("1다이아 25루비", formatGameCurrency(1_002_500_000_000L))
    }

    @Test
    fun omitsZeroSecondaryUnitAndPreventsNegativeCurrency() {
        assertEquals("1루비", formatGameCurrency(100_000_000L))
        assertEquals("0실버", formatGameCurrency(-100_500_000L))
        assertEquals("9,999실버", formatGameCurrency(9_999L))
    }

    @Test
    fun keepsNegativeSignOnlyForSignedProfitAndLossFormatting() {
        assertEquals("-1루비 50골드", formatSignedGameCurrency(-100_500_000L))
    }
}
