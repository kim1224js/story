package es.kim.story

import org.junit.Assert.assertEquals
import org.junit.Test

class GameCurrencyTest {
    @Test
    fun displaysLargestCurrencyUnitWithAtMostOneDecimalPlace() {
        assertEquals("1루비", formatGameCurrency(100_500_000L))
        assertEquals("2골드", formatGameCurrency(20_350L))
        assertEquals("1다이아", formatGameCurrency(1_002_500_000_000L))
        assertEquals("89.1루비", formatGameCurrency(8_910_000_000L))
    }

    @Test
    fun omitsZeroSecondaryUnitAndPreventsNegativeCurrency() {
        assertEquals("1루비", formatGameCurrency(100_000_000L))
        assertEquals("0실버", formatGameCurrency(-100_500_000L))
        assertEquals("9,999실버", formatGameCurrency(9_999L))
    }

    @Test
    fun keepsNegativeSignOnlyForSignedProfitAndLossFormatting() {
        assertEquals("-1루비", formatSignedGameCurrency(-100_500_000L))
    }
}
