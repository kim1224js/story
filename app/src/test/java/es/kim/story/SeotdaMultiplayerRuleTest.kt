package es.kim.story

import org.junit.Assert.assertEquals
import org.junit.Test

class SeotdaMultiplayerRuleTest {
    @Test
    fun ddaengCatcherBeatsDdaengAndSeryukInMultiplayerTable() {
        val ranks = listOf(
            GambleManager.rankSeotda(listOf(card(3), card(7))),
            GambleManager.rankSeotda(listOf(card(2, 1), card(2, 2))),
            GambleManager.rankSeotda(listOf(card(4), card(6))),
        )

        assertEquals(listOf(0), GambleManager.tableSpecialWinnerIndices(ranks))
    }

    @Test
    fun brightDdaengStillBeatsDdaengCatcher() {
        val ranks = listOf(
            GambleManager.rankSeotda(listOf(card(3), card(7))),
            GambleManager.rankSeotda(listOf(card(2, 1), card(2, 2))),
            GambleManager.rankSeotda(listOf(card(1, bright = true), card(3, bright = true))),
        )

        assertEquals(emptyList<Int>(), GambleManager.tableSpecialWinnerIndices(ranks))
    }

    @Test
    fun undercoverInspectorCatchesThirteenAndEighteenBrightDdaeng() {
        val inspector = GambleManager.rankSeotda(listOf(card(4), card(7)))
        val thirteen = GambleManager.rankSeotda(
            listOf(card(1, bright = true), card(3, bright = true)),
        )
        val jangDdaeng = GambleManager.rankSeotda(listOf(card(10, 1), card(10, 2)))

        assertEquals(
            listOf(0),
            GambleManager.tableSpecialWinnerIndices(listOf(inspector, thirteen, jangDdaeng)),
        )
    }

    @Test
    fun thirtyEightBrightDdaengCannotBeCaught() {
        val inspector = GambleManager.rankSeotda(listOf(card(4), card(7)))
        val thirteen = GambleManager.rankSeotda(
            listOf(card(1, bright = true), card(3, bright = true)),
        )
        val thirtyEight = GambleManager.rankSeotda(
            listOf(card(3, bright = true), card(8, bright = true)),
        )

        assertEquals(
            emptyList<Int>(),
            GambleManager.tableSpecialWinnerIndices(listOf(inspector, thirteen, thirtyEight)),
        )
    }

    @Test
    fun duplicateDdaengCatchersCauseReplayTie() {
        val catcher = GambleManager.rankSeotda(listOf(card(3), card(7)))
        val ddaeng = GambleManager.rankSeotda(listOf(card(5, 1), card(5, 2)))

        assertEquals(
            listOf(0, 1),
            GambleManager.tableSpecialWinnerIndices(listOf(catcher, catcher, ddaeng)),
        )
    }

    private fun card(month: Int, variant: Int = 1, bright: Boolean = false) =
        SeotdaCard(month, variant, bright)
}
