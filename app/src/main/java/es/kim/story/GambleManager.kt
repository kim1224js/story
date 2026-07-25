package es.kim.story

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import es.kim.story.data.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

enum class RpsChoice(val label: String, val icon: String) {
    Rock("바위", "✊"), Paper("보", "✋"), Scissors("가위", "✌️")
}

enum class GambleOutcome { Win, Draw, Lose }

data class GambleResult(
    val player: RpsChoice,
    val computer: RpsChoice,
    val outcome: GambleOutcome,
    val wager: Long,
    val payout: Long,
)

data class GambleState(
    val playedToday: Int = 0,
    val result: GambleResult? = null,
    val replayWager: Long = 0,
)

data class SeotdaCard(val month: Int, val variant: Int, val bright: Boolean = false)

data class SeotdaHandRank(
    val strength: Int,
    val name: String,
    val ddaengNumber: Int = 0,
    val isDdaengCatcher: Boolean = false,
    val isNineFour: Boolean = false,
)

enum class SeotdaOutcome { Win, Draw, Lose }

data class SeotdaResult(
    val outcome: SeotdaOutcome,
    val playerRank: SeotdaHandRank,
    val computerRank: SeotdaHandRank,
    val wager: Long,
    val premium: Long = 0,
    val computerRanks: List<SeotdaHandRank> = listOf(computerRank),
    val playerPlacement: Int = 1,
    val firstPlaceComputerIndex: Int? = null,
)

data class SeotdaState(
    val playedToday: Int = 0,
    val playerCount: Int = 2,
    val playerCards: List<SeotdaCard> = emptyList(),
    val computerHands: List<List<SeotdaCard>> = emptyList(),
    val wager: Long = 0,
    val raises: Int = 0,
    val replayReason: String? = null,
    val replayPlayerCards: List<SeotdaCard> = emptyList(),
    val replayComputerHands: List<List<SeotdaCard>> = emptyList(),
    val result: SeotdaResult? = null,
) {
    val isPlaying get() = playerCards.size == 2 &&
        computerHands.size == playerCount - 1 &&
        computerHands.all { it.size == 2 } &&
        result == null
}

data class ThreeCardSeotdaState(
    val playedToday: Int = 0,
    val playerCount: Int = 2,
    val playerCards: List<SeotdaCard> = emptyList(),
    val computerHands: List<List<SeotdaCard>> = emptyList(),
    val wager: Long = 0,
    val raises: Int = 0,
    val selectedCardIndices: Set<Int> = emptySet(),
    val replayReason: String? = null,
    val replayPlayerCards: List<SeotdaCard> = emptyList(),
    val replayComputerHands: List<List<SeotdaCard>> = emptyList(),
    val result: SeotdaResult? = null,
) {
    val isPlaying get() = playerCards.size == 3 &&
        computerHands.size == playerCount - 1 &&
        computerHands.all { it.size == 3 } &&
        result == null
}

@Singleton
class GambleManager @Inject constructor(
    @param:ApplicationContext context: Context,
    private val repository: UserRepository,
) {
    private val prefs = context.getSharedPreferences("gamble", Context.MODE_PRIVATE)
    private var currentAccount = ""
    private var opponentNames = DEFAULT_SEOTDA_OPPONENT_NAMES
    private var allowedBaseWagers = baseWagersForChapter(1)
    private var loadedDate = LocalDate.now().toString()
    private var preparedChoice = randomChoice()
    private val _state = MutableStateFlow(GambleState())
    val state = _state.asStateFlow()
    private val _seotdaState = MutableStateFlow(SeotdaState())
    val seotdaState = _seotdaState.asStateFlow()
    private val _threeCardSeotdaState = MutableStateFlow(ThreeCardSeotdaState())
    val threeCardSeotdaState = _threeCardSeotdaState.asStateFlow()

    fun selectAccount(userId: String, seotdaOpponentNames: List<String>, chapter: Int) {
        opponentNames = seotdaOpponentNames.takeIf { it.size == 3 && it.none(String::isBlank) }
            ?: DEFAULT_SEOTDA_OPPONENT_NAMES
        allowedBaseWagers = baseWagersForChapter(chapter)
        if (currentAccount == userId) return
        currentAccount = userId
        loadedDate = LocalDate.now().toString()
        preparedChoice = randomChoice()
        _state.value = GambleState(playedToday = prefs.getInt(rpsPlayKey(), 0))
        _seotdaState.value = SeotdaState(playedToday = prefs.getInt(seotdaPlayKey(), 0))
        _threeCardSeotdaState.value =
            ThreeCardSeotdaState(playedToday = prefs.getInt(threeCardSeotdaPlayKey(), 0))
    }

    suspend fun play(player: RpsChoice, wager: Long): Boolean {
        resetDayIfNeeded()
        val current = _state.value
        if (currentAccount.isBlank() || current.playedToday >= 10 || wager !in allowedBaseWagers) return false
        if (current.replayWager > 0 && wager != current.replayWager) return false
        val computer = preparedChoice
        val outcome = outcome(player, computer)
        val payout = when (outcome) {
            GambleOutcome.Win -> Math.multiplyExact(wager, 2)
            GambleOutcome.Draw -> wager
            GambleOutcome.Lose -> 0
        }
        if (!repository.settleGamble(wager, payout)) return false
        val played = if (outcome == GambleOutcome.Draw) current.playedToday else current.playedToday + 1
        if (outcome != GambleOutcome.Draw) prefs.edit().putInt(rpsPlayKey(), played).apply()
        _state.value = GambleState(
            playedToday = played,
            result = GambleResult(player, computer, outcome, wager, payout),
            replayWager = if (outcome == GambleOutcome.Draw) wager else 0,
        )
        preparedChoice = randomChoice()
        return true
    }

    fun acknowledgeResult() {
        _state.value = _state.value.copy(result = null)
    }

    fun deleteAccountData(userId: String) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("$userId|") }.forEach(editor::remove)
        editor.apply()
    }

    suspend fun resetRpsCount(): Boolean {
        resetDayIfNeeded()
        val current = _state.value
        if (current.playedToday == 0 || current.result != null || current.replayWager > 0) return false
        if (!repository.settleGamble(GAMBLE_COUNT_RESET_COST, 0)) return false
        prefs.edit().putInt(rpsPlayKey(), 0).apply()
        _state.value = current.copy(playedToday = 0)
        return true
    }

    suspend fun startSeotda(baseWager: Long, playerCount: Int): Boolean {
        resetDayIfNeeded()
        val current = _seotdaState.value
        if (baseWager !in allowedBaseWagers || playerCount !in 2..4) return false
        if (currentAccount.isBlank() || current.playedToday >= 10 || current.isPlaying) return false
        if (!repository.settleGamble(baseWager, 0)) return false
        val cards = seotdaDeck().shuffled().take(playerCount * 2)
        val played = current.playedToday + 1
        prefs.edit().putInt(seotdaPlayKey(), played).apply()
        _seotdaState.value = SeotdaState(
            playedToday = played,
            playerCount = playerCount,
            playerCards = cards.take(2),
            computerHands = cards.drop(2).chunked(2),
            wager = baseWager,
        )
        return true
    }

    suspend fun raiseSeotda(): Boolean {
        val current = _seotdaState.value
        if (!current.isPlaying || current.raises >= SEOTDA_MAX_RAISES) return false
        if (!repository.settleGamble(current.wager, 0)) return false
        val raised = current.copy(wager = Math.multiplyExact(current.wager, 2), raises = current.raises + 1)
        _seotdaState.value = raised
        if (raised.raises == SEOTDA_MAX_RAISES) settleSeotda(raised)
        return true
    }

    suspend fun showDownSeotda(): Boolean {
        val current = _seotdaState.value
        if (!current.isPlaying) return false
        settleSeotda(current)
        return true
    }

    fun acknowledgeSeotdaResult() {
        _seotdaState.value = SeotdaState(playedToday = _seotdaState.value.playedToday)
    }

    fun acknowledgeSeotdaReplay() {
        _seotdaState.value = _seotdaState.value.copy(
            replayReason = null,
            replayPlayerCards = emptyList(),
            replayComputerHands = emptyList(),
        )
    }

    suspend fun resetSeotdaCount(): Boolean {
        resetDayIfNeeded()
        val current = _seotdaState.value
        if (current.playedToday == 0 || current.isPlaying || current.result != null) return false
        if (!repository.settleGamble(GAMBLE_COUNT_RESET_COST, 0)) return false
        prefs.edit().putInt(seotdaPlayKey(), 0).apply()
        _seotdaState.value = SeotdaState()
        return true
    }

    suspend fun startThreeCardSeotda(baseWager: Long, playerCount: Int): Boolean {
        resetDayIfNeeded()
        val current = _threeCardSeotdaState.value
        if (baseWager !in allowedBaseWagers || playerCount !in 2..4) return false
        if (currentAccount.isBlank() || current.playedToday >= 10 || current.isPlaying) return false
        if (!repository.settleGamble(baseWager, 0)) return false
        val cards = seotdaDeck().shuffled().take(playerCount * 3)
        val played = current.playedToday + 1
        prefs.edit().putInt(threeCardSeotdaPlayKey(), played).apply()
        _threeCardSeotdaState.value = ThreeCardSeotdaState(
            playedToday = played,
            playerCount = playerCount,
            playerCards = cards.take(3),
            computerHands = cards.drop(3).chunked(3),
            wager = baseWager,
        )
        return true
    }

    suspend fun raiseThreeCardSeotda(): Boolean {
        val current = _threeCardSeotdaState.value
        if (!current.isPlaying || current.raises >= SEOTDA_MAX_RAISES) return false
        if (current.raises == SEOTDA_MAX_RAISES - 1 && current.selectedCardIndices.size != 2) return false
        if (!repository.settleGamble(current.wager, 0)) return false
        val raised = current.copy(wager = Math.multiplyExact(current.wager, 2), raises = current.raises + 1)
        _threeCardSeotdaState.value = raised
        if (raised.raises == SEOTDA_MAX_RAISES) settleThreeCardSeotda(raised)
        return true
    }

    suspend fun showDownThreeCardSeotda(): Boolean {
        val current = _threeCardSeotdaState.value
        if (!current.isPlaying || current.selectedCardIndices.size != 2) return false
        settleThreeCardSeotda(current)
        return true
    }

    fun toggleThreeCardSelection(index: Int) {
        val current = _threeCardSeotdaState.value
        if (!current.isPlaying || index !in current.playerCards.indices) return
        val selected = current.selectedCardIndices
        val updated = when {
            index in selected -> selected - index
            selected.size < 2 -> selected + index
            else -> selected
        }
        _threeCardSeotdaState.value = current.copy(selectedCardIndices = updated)
    }

    fun acknowledgeThreeCardSeotdaResult() {
        _threeCardSeotdaState.value =
            ThreeCardSeotdaState(playedToday = _threeCardSeotdaState.value.playedToday)
    }

    fun acknowledgeThreeCardSeotdaReplay() {
        _threeCardSeotdaState.value = _threeCardSeotdaState.value.copy(
            replayReason = null,
            replayPlayerCards = emptyList(),
            replayComputerHands = emptyList(),
        )
    }

    suspend fun resetThreeCardSeotdaCount(): Boolean {
        resetDayIfNeeded()
        val current = _threeCardSeotdaState.value
        if (current.playedToday == 0 || current.isPlaying || current.result != null) return false
        if (!repository.settleGamble(GAMBLE_COUNT_RESET_COST, 0)) return false
        prefs.edit().putInt(threeCardSeotdaPlayKey(), 0).apply()
        _threeCardSeotdaState.value = ThreeCardSeotdaState()
        return true
    }

    private suspend fun settleThreeCardSeotda(current: ThreeCardSeotdaState) {
        val submittedCards = current.selectedCardIndices.sorted().map(current.playerCards::get)
        if (submittedCards.size != 2) return
        val playerRank = rankSeotda(submittedCards)
        val computerRanks = current.computerHands.map { rankThreeCardSeotda(it, playerRank) }
        if (playerRank.isNineFour || computerRanks.any { it.isNineFour }) {
            val holders = nineFourHolders(playerRank, computerRanks)
            replayThreeCardSeotda(current, "${participantsAsSubject(holders)} 9·4 구사라 재경기합니다.")
            return
        }
        val comparisons = computerRanks.map { compareSeotda(playerRank, it) }
        if (comparisons.any { it == 0 }) {
            replayThreeCardSeotda(current, "무승부로 판수 차감 없이 재경기합니다.")
            return
        }
        val outcome = if (comparisons.all { it > 0 }) SeotdaOutcome.Win else SeotdaOutcome.Lose
        val premium = ddaengPremium(outcome, playerRank)
        val payout = when (outcome) {
            SeotdaOutcome.Win -> Math.addExact(
                Math.multiplyExact(current.wager, current.playerCount.toLong()),
                premium,
            )
            SeotdaOutcome.Draw -> current.wager
            SeotdaOutcome.Lose -> 0
        }
        if (payout > 0) repository.addMoney(payout)
        val firstPlaceComputerIndex = if (outcome == SeotdaOutcome.Win) null else {
            computerRanks.indices.maxWithOrNull { first, second ->
                compareSeotda(computerRanks[first], computerRanks[second])
            }
        }
        val strongestComputer = firstPlaceComputerIndex?.let(computerRanks::get)
            ?: computerRanks.maxBy { rankAgainst(it, playerRank) }
        val playerPlacement = 1 + comparisons.count { it < 0 }
        _threeCardSeotdaState.value = current.copy(
            result = SeotdaResult(
                outcome, playerRank, strongestComputer, current.wager, premium, computerRanks,
                playerPlacement, firstPlaceComputerIndex,
            ),
        )
    }

    private suspend fun settleSeotda(current: SeotdaState) {
        val playerRank = rankSeotda(current.playerCards)
        val computerRanks = current.computerHands.map(::rankSeotda)
        if (playerRank.isNineFour || computerRanks.any { it.isNineFour }) {
            val holders = nineFourHolders(playerRank, computerRanks)
            replaySeotda(current, "${participantsAsSubject(holders)} 9·4 구사라 재경기합니다.")
            return
        }
        val comparisons = computerRanks.map { compareSeotda(playerRank, it) }
        if (comparisons.any { it == 0 }) {
            replaySeotda(current, "무승부로 판수 차감 없이 재경기합니다.")
            return
        }
        val outcome = if (comparisons.all { it > 0 }) SeotdaOutcome.Win else SeotdaOutcome.Lose
        val premium = ddaengPremium(outcome, playerRank)
        val payout = when (outcome) {
            SeotdaOutcome.Win -> Math.addExact(
                Math.multiplyExact(current.wager, current.playerCount.toLong()),
                premium,
            )
            SeotdaOutcome.Draw -> current.wager
            SeotdaOutcome.Lose -> 0
        }
        if (payout > 0) repository.addMoney(payout)
        val firstPlaceComputerIndex = if (outcome == SeotdaOutcome.Win) null else {
            computerRanks.indices.maxWithOrNull { first, second ->
                compareSeotda(computerRanks[first], computerRanks[second])
            }
        }
        val strongestComputer = firstPlaceComputerIndex?.let(computerRanks::get)
            ?: computerRanks.maxBy { rankAgainst(it, playerRank) }
        val playerPlacement = 1 + comparisons.count { it < 0 }
        _seotdaState.value = current.copy(
            result = SeotdaResult(
                outcome, playerRank, strongestComputer, current.wager, premium, computerRanks,
                playerPlacement, firstPlaceComputerIndex,
            ),
        )
    }

    private fun ddaengPremium(outcome: SeotdaOutcome, rank: SeotdaHandRank): Long {
        if (outcome != SeotdaOutcome.Win) return 0L
        return when (rank.name) {
            "38광땡" -> THIRTY_EIGHT_BRIGHT_PREMIUM
            "18광땡", "13광땡" -> BRIGHT_DDAENG_PREMIUM
            else -> if (rank.ddaengNumber > 0) rank.ddaengNumber * DDAENG_PREMIUM_UNIT else 0L
        }
    }

    private fun replaySeotda(current: SeotdaState, reason: String) {
        val cards = seotdaDeck().shuffled().take(current.playerCount * 2)
        _seotdaState.value = current.copy(
            playerCards = cards.take(2),
            computerHands = cards.drop(2).chunked(2),
            replayReason = reason,
            replayPlayerCards = current.playerCards,
            replayComputerHands = current.computerHands,
            result = null,
        )
    }

    private fun replayThreeCardSeotda(current: ThreeCardSeotdaState, reason: String) {
        val cards = seotdaDeck().shuffled().take(current.playerCount * 3)
        _threeCardSeotdaState.value = current.copy(
            playerCards = cards.take(3),
            computerHands = cards.drop(3).chunked(3),
            selectedCardIndices = emptySet(),
            replayReason = reason,
            replayPlayerCards = current.playerCards,
            replayComputerHands = current.computerHands,
            result = null,
        )
    }

    private fun nineFourHolders(
        playerRank: SeotdaHandRank,
        computerRanks: List<SeotdaHandRank>,
    ): List<String> = buildList {
        if (playerRank.isNineFour) add("나")
        computerRanks.forEachIndexed { index, rank ->
            if (rank.isNineFour) add(opponentNames[index])
        }
    }

    private fun resetDayIfNeeded() {
        if (loadedDate != LocalDate.now().toString()) {
            loadedDate = LocalDate.now().toString()
            _state.value = GambleState()
            _seotdaState.value = SeotdaState()
            _threeCardSeotdaState.value = ThreeCardSeotdaState()
        }
    }

    private fun rpsPlayKey() = "$currentAccount|${LocalDate.now()}|rps_played"
    private fun seotdaPlayKey() = "$currentAccount|${LocalDate.now()}|seotda_played"
    private fun threeCardSeotdaPlayKey() = "$currentAccount|${LocalDate.now()}|three_card_seotda_played"
    private fun randomChoice() = RpsChoice.entries[Random.nextInt(RpsChoice.entries.size)]
    private fun outcome(player: RpsChoice, computer: RpsChoice): GambleOutcome = when {
        player == computer -> GambleOutcome.Draw
        player == RpsChoice.Rock && computer == RpsChoice.Scissors -> GambleOutcome.Win
        player == RpsChoice.Paper && computer == RpsChoice.Rock -> GambleOutcome.Win
        player == RpsChoice.Scissors && computer == RpsChoice.Paper -> GambleOutcome.Win
        else -> GambleOutcome.Lose
    }

    companion object {
        fun baseWagersForChapter(chapter: Int): List<Long> {
            val clearCost = stageClearCost(chapter)
            return listOf(
                (clearCost / 200L).coerceAtLeast(1L),
                (clearCost / 100L).coerceAtLeast(1L),
                (clearCost / 10L).coerceAtLeast(1L),
            )
        }
        const val SEOTDA_MAX_RAISES = 3
        const val GAMBLE_COUNT_RESET_COST = 1_000_000L
        const val DDAENG_PREMIUM_UNIT = 1_000_000L
        const val BRIGHT_DDAENG_PREMIUM = 20_000_000L
        const val THIRTY_EIGHT_BRIGHT_PREMIUM = 100_000_000L
        val DEFAULT_SEOTDA_OPPONENT_NAMES = listOf("졸린", "토끼", "콜라")

        private fun participantsAsSubject(names: List<String>): String = when (names.size) {
            0 -> "참가자가"
            1 -> if (names[0] == "나") "내가" else "${names[0]}가"
            else -> names.dropLast(1).joinToString(", ") + "와 ${names.last()}가"
        }

        fun seotdaDeck(): List<SeotdaCard> = (1..10).flatMap { month ->
            listOf(
                SeotdaCard(month, 1, month in setOf(1, 3, 8)),
                SeotdaCard(month, 2),
            )
        }

        fun rankSeotda(cards: List<SeotdaCard>): SeotdaHandRank {
            require(cards.size == 2)
            val months = cards.map { it.month }.sorted()
            val pair = months[0] == months[1]
            val brightMonths = cards.filter { it.bright }.map { it.month }.sorted()
            if (brightMonths == listOf(3, 8)) return SeotdaHandRank(10_000, "38광땡")
            if (brightMonths == listOf(1, 8)) return SeotdaHandRank(9_900, "18광땡")
            if (brightMonths == listOf(1, 3)) return SeotdaHandRank(9_800, "13광땡")
            if (pair) {
                val month = months[0]
                return SeotdaHandRank(
                    8_000 + month,
                    if (month == 10) "장땡" else "${month}땡",
                    ddaengNumber = month,
                )
            }
            if (months == listOf(3, 7)) {
                return SeotdaHandRank(6_000, "땡잡이", isDdaengCatcher = true)
            }
            if (months == listOf(4, 9)) {
                return SeotdaHandRank(6_003, "구사", isNineFour = true)
            }
            val named = mapOf(
                listOf(1, 2) to SeotdaHandRank(7_006, "알리"),
                listOf(1, 4) to SeotdaHandRank(7_005, "독사"),
                listOf(1, 9) to SeotdaHandRank(7_004, "구삥"),
                listOf(1, 10) to SeotdaHandRank(7_003, "장삥"),
                listOf(4, 10) to SeotdaHandRank(7_002, "장사"),
                listOf(4, 6) to SeotdaHandRank(7_001, "세륙"),
            )
            named[months]?.let { return it }
            val points = months.sum() % 10
            return SeotdaHandRank(6_000 + points, if (points == 9) "갑오" else if (points == 0) "망통" else "${points}끗")
        }

        fun rankThreeCardSeotda(
            cards: List<SeotdaCard>,
            opponent: SeotdaHandRank? = null,
        ): SeotdaHandRank {
            require(cards.size == 3)
            return listOf(
                listOf(cards[0], cards[1]),
                listOf(cards[0], cards[2]),
                listOf(cards[1], cards[2]),
            ).map(::rankSeotda).maxBy {
                if (opponent != null && it.isDdaengCatcher && opponent.ddaengNumber in 1..9) 20_000
                else it.strength
            }
        }

        fun compareSeotda(first: SeotdaHandRank, second: SeotdaHandRank): Int {
            if (first.isDdaengCatcher && second.ddaengNumber in 1..9) return 1
            if (second.isDdaengCatcher && first.ddaengNumber in 1..9) return -1
            return first.strength.compareTo(second.strength)
        }

        private fun rankAgainst(rank: SeotdaHandRank, opponent: SeotdaHandRank): Int =
            if (rank.isDdaengCatcher && opponent.ddaengNumber in 1..9) 20_000 else rank.strength
    }
}
