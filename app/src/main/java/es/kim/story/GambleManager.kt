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
enum class SeotdaBetCurrency { Money, BlueChip }

data class SeotdaHandRank(
    val strength: Int,
    val name: String,
    val ddaengNumber: Int = 0,
    val isDdaengCatcher: Boolean = false,
    val isBrightCatcher: Boolean = false,
    val isNineFour: Boolean = false,
    val isMungtunguriNineFour: Boolean = false,
)

enum class SeotdaOutcome { Win, Draw, Lose }

data class SeotdaResult(
    val outcome: SeotdaOutcome,
    val playerRank: SeotdaHandRank,
    val computerRank: SeotdaHandRank,
    val wager: Long,
    val betCurrency: SeotdaBetCurrency = SeotdaBetCurrency.Money,
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
    val betCurrency: SeotdaBetCurrency = SeotdaBetCurrency.Money,
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
    val betCurrency: SeotdaBetCurrency = SeotdaBetCurrency.Money,
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
    private var allowedRpsWagers = baseWagersForChapter(1)
    private var allowedSeotdaWagers = seotdaBaseWagersForChapter(1)
    private var currentStageClearCost = stageClearCost(1)
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
        allowedRpsWagers = baseWagersForChapter(chapter)
        allowedSeotdaWagers = seotdaBaseWagersForChapter(chapter)
        currentStageClearCost = stageClearCost(chapter)
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
        if (currentAccount.isBlank() || wager !in allowedRpsWagers) return false
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

    suspend fun startSeotda(
        baseWager: Long,
        playerCount: Int,
        betCurrency: SeotdaBetCurrency = SeotdaBetCurrency.Money,
    ): Boolean {
        resetDayIfNeeded()
        val current = _seotdaState.value
        if (!validBaseWager(baseWager, betCurrency) || playerCount !in 2..4) return false
        if (currentAccount.isBlank() || current.isPlaying) return false
        if (!settleBet(betCurrency, baseWager, 0)) return false
        val cards = seotdaDeck().shuffled().take(playerCount * 2)
        val played = current.playedToday + 1
        prefs.edit().putInt(seotdaPlayKey(), played).apply()
        _seotdaState.value = SeotdaState(
            playedToday = played,
            playerCount = playerCount,
            playerCards = cards.take(2),
            computerHands = cards.drop(2).chunked(2),
            wager = baseWager,
            betCurrency = betCurrency,
        )
        return true
    }

    suspend fun raiseSeotda(): Boolean {
        val current = _seotdaState.value
        if (!current.isPlaying || current.raises >= SEOTDA_MAX_RAISES) return false
        if (!settleBet(current.betCurrency, current.wager, 0)) return false
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

    suspend fun startThreeCardSeotda(
        baseWager: Long,
        playerCount: Int,
        betCurrency: SeotdaBetCurrency = SeotdaBetCurrency.Money,
    ): Boolean {
        resetDayIfNeeded()
        val current = _threeCardSeotdaState.value
        if (!validBaseWager(baseWager, betCurrency) || playerCount !in 2..4) return false
        if (currentAccount.isBlank() || current.isPlaying) return false
        if (!settleBet(betCurrency, baseWager, 0)) return false
        val cards = seotdaDeck().shuffled().take(playerCount * 3)
        val played = current.playedToday + 1
        prefs.edit().putInt(threeCardSeotdaPlayKey(), played).apply()
        _threeCardSeotdaState.value = ThreeCardSeotdaState(
            playedToday = played,
            playerCount = playerCount,
            playerCards = cards.take(3),
            computerHands = cards.drop(3).chunked(3),
            wager = baseWager,
            betCurrency = betCurrency,
        )
        return true
    }

    suspend fun raiseThreeCardSeotda(): Boolean {
        val current = _threeCardSeotdaState.value
        if (!current.isPlaying || current.raises >= SEOTDA_MAX_RAISES) return false
        if (current.raises == SEOTDA_MAX_RAISES - 1 && current.selectedCardIndices.size != 2) return false
        if (!settleBet(current.betCurrency, current.wager, 0)) return false
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
        var computerRanks = current.computerHands.map { rankThreeCardSeotda(it) }

        // 이미 선택된 땡/광땡을 다른 패로 덮어쓰지 않고, 다른 참가자의
        // 특수패 후보만 활성화해야 다인전 상성이 사라지지 않는다.
        if ((listOf(playerRank) + computerRanks).any { it.ddaengNumber in 1..9 }) {
            computerRanks = current.computerHands.mapIndexed { index, hand ->
                val selected = computerRanks[index]
                if (selected.ddaengNumber > 0 || selected.name.endsWith("광땡")) selected
                else specialRankFromThreeCards(hand) { it.isDdaengCatcher } ?: selected
            }
        }
        if ((listOf(playerRank) + computerRanks).any { it.name in CATCHABLE_BRIGHT_DDAENGS }) {
            computerRanks = current.computerHands.mapIndexed { index, hand ->
                val selected = computerRanks[index]
                if (selected.name.endsWith("광땡")) selected
                else specialRankFromThreeCards(hand) { it.isBrightCatcher } ?: selected
            }
        }
        if (shouldReplayForNineFour(listOf(playerRank) + computerRanks)) {
            val holders = nineFourHolders(playerRank, computerRanks)
            replayThreeCardSeotda(current, "${participantsAsSubject(holders)} 9·4 구사라 재경기합니다.")
            return
        }
        val allRanks = listOf(playerRank) + computerRanks
        val specialWinners = tableSpecialWinnerIndices(allRanks)
        if (specialWinners.size > 1) {
            replayThreeCardSeotda(current, "특수패가 겹쳐 무승부로 재경기합니다.")
            return
        }
        val specialWinner = specialWinners.singleOrNull()
        val comparisons = computerRanks.mapIndexed { index, computerRank ->
            when (specialWinner) {
                0 -> 1
                index + 1 -> -1
                else -> compareSeotda(playerRank, computerRank)
            }
        }
        if (comparisons.any { it == 0 }) {
            replayThreeCardSeotda(current, "무승부로 판수 차감 없이 재경기합니다.")
            return
        }
        val outcome = if (comparisons.all { it > 0 }) SeotdaOutcome.Win else SeotdaOutcome.Lose
        val premium = ddaengPremium(outcome, playerRank)
        val betPayout = when (outcome) {
            SeotdaOutcome.Win -> Math.multiplyExact(current.wager, 2L)
            SeotdaOutcome.Draw -> current.wager
            SeotdaOutcome.Lose -> 0
        }
        if (current.betCurrency == SeotdaBetCurrency.Money) {
            val moneyPayout = Math.addExact(betPayout, premium)
            if (moneyPayout > 0) repository.addMoney(moneyPayout)
        } else {
            if (betPayout > 0) repository.addBlueChips(betPayout)
            if (premium > 0) repository.addMoney(premium)
        }
        val firstPlaceComputerIndex = if (outcome == SeotdaOutcome.Win) null else {
            specialWinner?.takeIf { it > 0 }?.minus(1) ?:
            computerRanks.indices.maxWithOrNull { first, second ->
                compareSeotda(computerRanks[first], computerRanks[second])
            }
        }
        val strongestComputer = firstPlaceComputerIndex?.let(computerRanks::get)
            ?: computerRanks.maxBy { rankAgainst(it, playerRank) }
        val playerPlacement = 1 + comparisons.count { it < 0 }
        _threeCardSeotdaState.value = current.copy(
            result = SeotdaResult(
                outcome, playerRank, strongestComputer, current.wager, current.betCurrency,
                premium, computerRanks,
                playerPlacement, firstPlaceComputerIndex,
            ),
        )
    }

    private suspend fun settleSeotda(current: SeotdaState) {
        val playerRank = rankSeotda(current.playerCards)
        val computerRanks = current.computerHands.map(::rankSeotda)
        if (shouldReplayForNineFour(listOf(playerRank) + computerRanks)) {
            val holders = nineFourHolders(playerRank, computerRanks)
            replaySeotda(current, "${participantsAsSubject(holders)} 9·4 구사라 재경기합니다.")
            return
        }
        val allRanks = listOf(playerRank) + computerRanks
        val specialWinners = tableSpecialWinnerIndices(allRanks)
        if (specialWinners.size > 1) {
            replaySeotda(current, "특수패가 겹쳐 무승부로 재경기합니다.")
            return
        }
        val specialWinner = specialWinners.singleOrNull()
        val comparisons = computerRanks.mapIndexed { index, computerRank ->
            when (specialWinner) {
                0 -> 1
                index + 1 -> -1
                else -> compareSeotda(playerRank, computerRank)
            }
        }
        if (comparisons.any { it == 0 }) {
            replaySeotda(current, "무승부로 판수 차감 없이 재경기합니다.")
            return
        }
        val outcome = if (comparisons.all { it > 0 }) SeotdaOutcome.Win else SeotdaOutcome.Lose
        val premium = ddaengPremium(outcome, playerRank)
        val betPayout = when (outcome) {
            SeotdaOutcome.Win -> Math.multiplyExact(current.wager, 2L)
            SeotdaOutcome.Draw -> current.wager
            SeotdaOutcome.Lose -> 0
        }
        if (current.betCurrency == SeotdaBetCurrency.Money) {
            val moneyPayout = Math.addExact(betPayout, premium)
            if (moneyPayout > 0) repository.addMoney(moneyPayout)
        } else {
            if (betPayout > 0) repository.addBlueChips(betPayout)
            if (premium > 0) repository.addMoney(premium)
        }
        val firstPlaceComputerIndex = if (outcome == SeotdaOutcome.Win) null else {
            specialWinner?.takeIf { it > 0 }?.minus(1) ?:
            computerRanks.indices.maxWithOrNull { first, second ->
                compareSeotda(computerRanks[first], computerRanks[second])
            }
        }
        val strongestComputer = firstPlaceComputerIndex?.let(computerRanks::get)
            ?: computerRanks.maxBy { rankAgainst(it, playerRank) }
        val playerPlacement = 1 + comparisons.count { it < 0 }
        _seotdaState.value = current.copy(
            result = SeotdaResult(
                outcome, playerRank, strongestComputer, current.wager, current.betCurrency,
                premium, computerRanks,
                playerPlacement, firstPlaceComputerIndex,
            ),
        )
    }

    private fun ddaengPremium(outcome: SeotdaOutcome, rank: SeotdaHandRank): Long {
        if (outcome != SeotdaOutcome.Win) return 0L
        return when (rank.name) {
            "38광땡" -> currentStageClearCost * 3L / 100L
            "18광땡", "13광땡" -> currentStageClearCost / 50L
            else -> if (rank.ddaengNumber > 0) {
                val bonusBasisPoints = 100L + (rank.ddaengNumber - 1).coerceAtLeast(0) * 20L
                currentStageClearCost * bonusBasisPoints / 10_000L
            } else {
                0L
            }
        }
    }

    private fun validBaseWager(wager: Long, currency: SeotdaBetCurrency): Boolean =
        if (currency == SeotdaBetCurrency.Money) wager in allowedSeotdaWagers
        else wager == BLUE_CHIP_BASE_WAGER

    private suspend fun settleBet(
        currency: SeotdaBetCurrency,
        wager: Long,
        payout: Long,
    ): Boolean = if (currency == SeotdaBetCurrency.Money) {
        repository.settleGamble(wager, payout)
    } else {
        repository.settleBlueChipGamble(wager, payout)
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
                (clearCost / 100L).coerceAtLeast(1L),
                (clearCost / 40L).coerceAtLeast(1L),
                (clearCost / 20L).coerceAtLeast(1L),
            )
        }

        fun seotdaBaseWagersForChapter(chapter: Int): List<Long> {
            val clearCost = stageClearCost(chapter)
            return listOf(
                (clearCost / 400L).coerceAtLeast(1L),
                (clearCost / 200L).coerceAtLeast(1L),
                (clearCost / 100L).coerceAtLeast(1L),
            )
        }
        const val SEOTDA_MAX_RAISES = 3
        const val GAMBLE_COUNT_RESET_COST = 1_000_000L
        const val BLUE_CHIP_BASE_WAGER = 1L
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
            if (months == listOf(4, 7)) {
                return SeotdaHandRank(6_001, "암행어사", isBrightCatcher = true)
            }
            if (months == listOf(4, 9)) {
                val mungtunguri = cards.all { it.variant == 1 }
                return SeotdaHandRank(
                    strength = 6_003,
                    name = if (mungtunguri) "멍텅구리 구사" else "구사",
                    isNineFour = true,
                    isMungtunguriNineFour = mungtunguri,
                )
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
                rankAgainst(it, opponent)
            }
        }

        private fun specialRankFromThreeCards(
            cards: List<SeotdaCard>,
            predicate: (SeotdaHandRank) -> Boolean,
        ): SeotdaHandRank? = listOf(
            listOf(cards[0], cards[1]),
            listOf(cards[0], cards[2]),
            listOf(cards[1], cards[2]),
        ).map(::rankSeotda).firstOrNull(predicate)

        fun compareSeotda(first: SeotdaHandRank, second: SeotdaHandRank): Int {
            if (first.isDdaengCatcher && second.ddaengNumber in 1..9) return 1
            if (second.isDdaengCatcher && first.ddaengNumber in 1..9) return -1
            if (first.isBrightCatcher && second.name in CATCHABLE_BRIGHT_DDAENGS) return 1
            if (second.isBrightCatcher && first.name in CATCHABLE_BRIGHT_DDAENGS) return -1
            return first.strength.compareTo(second.strength)
        }

        private fun rankAgainst(rank: SeotdaHandRank, opponent: SeotdaHandRank?): Int = when {
            opponent != null && rank.isDdaengCatcher && opponent.ddaengNumber in 1..9 -> 20_000
            opponent != null && rank.isBrightCatcher && opponent.name in CATCHABLE_BRIGHT_DDAENGS -> 19_000
            else -> rank.strength
        }

        private fun shouldReplayForNineFour(ranks: List<SeotdaHandRank>): Boolean {
            val nineFourRanks = ranks.filter { it.isNineFour }
            if (nineFourRanks.isEmpty()) return false

            // 다인전에서도 땡과 땡잡이가 함께 있으면 땡잡이의 특수승을 먼저 적용한다.
            if (ranks.any { it.isDdaengCatcher } && ranks.any { it.ddaengNumber in 1..9 }) {
                return false
            }
            // 암행어사와 잡을 수 있는 광땡이 함께 있으면 암행어사의 특수승을 먼저 적용한다.
            if (ranks.any { it.isBrightCatcher } && ranks.any { it.name in CATCHABLE_BRIGHT_DDAENGS }) {
                return false
            }

            val strongestOther = ranks.filterNot { it.isNineFour }.maxOfOrNull { it.strength }
                ?: return true
            return if (nineFourRanks.any { it.isMungtunguriNineFour }) {
                strongestOther <= 8_010 // 장땡 이하일 때 재경기
            } else {
                strongestOther <= 7_006 // 알리 이하일 때 재경기
            }
        }

        internal fun tableSpecialWinnerIndices(ranks: List<SeotdaHandRank>): List<Int> {
            // 38광땡은 어떤 잡이 패로도 잡을 수 없는 절대 최상위 패다.
            if (ranks.any { it.name == "38광땡" }) return emptyList()

            val normalBest = ranks.maxOfOrNull { it.strength } ?: return emptyList()
            val specialScores = buildMap<Int, Int> {
                if (ranks.any { it.ddaengNumber in 1..9 }) {
                    ranks.indices.filter { ranks[it].isDdaengCatcher }
                        .forEach { put(it, 9_500) }
                }
                if (ranks.any { it.name in CATCHABLE_BRIGHT_DDAENGS }) {
                    ranks.indices.filter { ranks[it].isBrightCatcher }
                        .forEach { put(it, 9_950) }
                }
            }
            val bestSpecial = specialScores.values.maxOrNull() ?: return emptyList()
            if (bestSpecial <= normalBest) return emptyList()
            return specialScores.filterValues { it == bestSpecial }.keys.toList()
        }

        private val CATCHABLE_BRIGHT_DDAENGS = setOf("13광땡", "18광땡")
    }
}
