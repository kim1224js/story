package es.kim.story

import org.junit.Assert.assertEquals
import org.junit.Test

class SeotdaPayoutTest {
    @Test
    fun twoPlayerWinnerReceivesBothWagers() {
        assertEquals(20_000L, GambleManager.seotdaTotalPot(10_000L, 2))
        assertEquals(10_000L, GambleManager.seotdaNetProfit(10_000L, 2))
    }

    @Test
    fun threePlayerWinnerReceivesAllThreeWagers() {
        assertEquals(30_000L, GambleManager.seotdaTotalPot(10_000L, 3))
        assertEquals(20_000L, GambleManager.seotdaNetProfit(10_000L, 3))
    }

    @Test
    fun fourPlayerWinnerReceivesAllFourWagers() {
        assertEquals(40_000L, GambleManager.seotdaTotalPot(10_000L, 4))
        assertEquals(30_000L, GambleManager.seotdaNetProfit(10_000L, 4))
    }

    @Test
    fun raisedWagerUsesTheFinalAccumulatedWager() {
        assertEquals(320_000L, GambleManager.seotdaTotalPot(80_000L, 4))
        assertEquals(240_000L, GambleManager.seotdaNetProfit(80_000L, 4))
    }
}
