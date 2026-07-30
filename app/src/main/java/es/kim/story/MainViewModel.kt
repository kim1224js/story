package es.kim.story
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.kim.story.data.UserRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
@HiltViewModel class MainViewModel @Inject constructor(
    private val repository: UserRepository,
    private val workManager: WorkManager,
    private val stepQuestManager: StepQuestManager,
    private val gambleManager: GambleManager,
) : ViewModel() {
    val user = repository.user.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val accounts = repository.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val workState = workManager.state
    val stepQuestState = stepQuestManager.state
    val gambleState = gambleManager.state
    val seotdaState = gambleManager.seotdaState
    val threeCardSeotdaState = gambleManager.threeCardSeotdaState
    init {
        viewModelScope.launch {
            user.filterNotNull().collect {
                workManager.selectAccount(it.userId)
                stepQuestManager.selectAccount(it.userId)
                gambleManager.selectAccount(
                    it.userId,
                    listOf(it.seotdaName1, it.seotdaName2, it.seotdaName3),
                    it.chapter,
                )
            }
        }
    }
    fun saveUserId(id: String, onResult: (Boolean) -> Unit = {}) {
        if (id.isBlank()) return onResult(false)
        viewModelScope.launch { onResult(repository.saveUserId(id)) }
    }
    fun startJob(job: PartTimeJob) = workManager.start(job)
    fun cancelJob() = workManager.cancel()
    fun startMoleGame(): Boolean = workManager.startMoleGame()
    fun startIceGame(): Boolean = workManager.startIceGame()
    fun breakIce(index: Int): IceBreakResult? = workManager.breakIce(index)
    fun ensureMaze() = workManager.ensureMaze()
    fun moveMaze(targetX: Int, targetY: Int, itemId: Int?): Boolean =
        workManager.moveMaze(targetX, targetY, itemId)
    fun completeMaze(reward: Long) {
        if (reward <= 0 || !workManager.completeMaze()) return
        val currentUser = user.value ?: return
        viewModelScope.launch { repository.addMoney(currentUser.userId, reward) }
    }
    fun startNewMaze() = workManager.startNewMaze()
    fun resetMazeMoves(cost: Long, onResult: (Boolean) -> Unit) {
        if (cost <= 0 || workState.value.mazeCompleted) return onResult(false)
        viewModelScope.launch {
            if (!repository.spendMoney(cost)) {
                onResult(false)
                return@launch
            }
            onResult(workManager.resetMazeMovesToday())
        }
    }
    fun claimMoleReward(hits: Int, rewardPerHit: Long) {
        if (hits <= 0 || rewardPerHit <= 0) return
        val currentUser = user.value ?: return
        viewModelScope.launch {
            repository.addMoney(currentUser.userId, Math.multiplyExact(hits.toLong(), rewardPerHit))
        }
    }
    fun claimIcePenguinReward(reward: Long) {
        if (reward <= 0) return
        val currentUser = user.value ?: return
        viewModelScope.launch { repository.addMoney(currentUser.userId, reward) }
    }
    fun claimPuzzleReward(score: Int, clearReward: Long) {
        workManager.recordPuzzleScore(score)
        if (score <= 0 || clearReward <= 0) return
        val currentUser = user.value ?: return
        val reward = calculatePuzzleReward(score, clearReward)
        if (reward <= 0) return
        viewModelScope.launch { repository.addMoney(currentUser.userId, reward) }
    }
    fun claimJob(job: PartTimeJob) {
        val currentUser = user.value ?: return
        val reward = stagePercentReward(currentUser.chapter, job.rewardPercent)
        if (workManager.claim(job)) viewModelScope.launch {
            repository.addMoney(currentUser.userId, reward)
        }
    }
    fun refreshStepQuests() = viewModelScope.launch {
        runCatching { stepQuestManager.refresh() }
    }
    fun claimStepQuest(quest: StepQuest, moreRewardApplied: Boolean = false) = viewModelScope.launch {
        val chapterReward = stagePercentReward(user.value?.chapter ?: 1, quest.rewardPercent)
        val reward = if (moreRewardApplied) chapterReward + chapterReward / 2 else chapterReward
        stepQuestManager.claim(quest, reward)
    }
    fun changeGender(currentGender: String) = viewModelScope.launch {
        repository.updateGender(if (currentGender == "남성") "여성" else "남성")
    }
    fun switchAccount(userId: String) = viewModelScope.launch { repository.switchAccount(userId) }
    fun deleteCharacter(userId: String) = viewModelScope.launch {
        if (repository.deleteAccount(userId)) {
            workManager.deleteAccountData(userId)
            stepQuestManager.deleteAccountData(userId)
            gambleManager.deleteAccountData(userId)
        }
    }
    fun playRps(choice: RpsChoice, wager: Long) = viewModelScope.launch {
        gambleManager.play(choice, wager)
    }
    fun acknowledgeGambleResult() = gambleManager.acknowledgeResult()
    fun resetRpsCount() = viewModelScope.launch { gambleManager.resetRpsCount() }
    fun startSeotda(
        baseWager: Long,
        playerCount: Int,
        betCurrency: SeotdaBetCurrency = SeotdaBetCurrency.Money,
    ) = viewModelScope.launch { gambleManager.startSeotda(baseWager, playerCount, betCurrency) }
    fun raiseSeotda() = viewModelScope.launch { gambleManager.raiseSeotda() }
    fun showDownSeotda() = viewModelScope.launch { gambleManager.showDownSeotda() }
    fun acknowledgeSeotdaResult() = gambleManager.acknowledgeSeotdaResult()
    fun acknowledgeSeotdaReplay() = gambleManager.acknowledgeSeotdaReplay()
    fun resetSeotdaCount() = viewModelScope.launch { gambleManager.resetSeotdaCount() }
    fun startThreeCardSeotda(
        baseWager: Long,
        playerCount: Int,
        betCurrency: SeotdaBetCurrency = SeotdaBetCurrency.Money,
    ) = viewModelScope.launch {
        gambleManager.startThreeCardSeotda(baseWager, playerCount, betCurrency)
    }
    fun raiseThreeCardSeotda() = viewModelScope.launch { gambleManager.raiseThreeCardSeotda() }
    fun showDownThreeCardSeotda() = viewModelScope.launch { gambleManager.showDownThreeCardSeotda() }
    fun toggleThreeCardSelection(index: Int) = gambleManager.toggleThreeCardSelection(index)
    fun acknowledgeThreeCardSeotdaResult() = gambleManager.acknowledgeThreeCardSeotdaResult()
    fun acknowledgeThreeCardSeotdaReplay() = gambleManager.acknowledgeThreeCardSeotdaReplay()
    fun resetThreeCardSeotdaCount() =
        viewModelScope.launch { gambleManager.resetThreeCardSeotdaCount() }
    fun updateSeotdaNames(names: List<String>) = viewModelScope.launch {
        repository.updateSeotdaNames(names)
    }
    fun exchangeBlueChip(onSuccess: () -> Unit = {}) = viewModelScope.launch {
        if (repository.exchangeBlueChip()) onSuccess()
    }
    fun sellBlueChip(onSuccess: () -> Unit = {}) = viewModelScope.launch {
        if (repository.sellBlueChip()) onSuccess()
    }
    fun buyPremiumIdColor() = viewModelScope.launch { repository.buyPremiumIdColor() }
    fun clearStoryChapter(chapter: Int, cost: Long) = viewModelScope.launch {
        repository.clearStoryChapter(chapter, cost)
    }
}
