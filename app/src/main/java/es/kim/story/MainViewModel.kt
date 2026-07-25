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
    fun resetMoleGameCount() = viewModelScope.launch {
        if (!workManager.canResetMoleGame()) return@launch
        if (repository.settleGamble(MOLE_GAME_RESET_COST, 0L)) {
            workManager.resetMoleGame()
        }
    }
    fun claimMoleReward(hits: Int, rewardPerHit: Long) {
        if (hits <= 0 || rewardPerHit <= 0) return
        val currentUser = user.value ?: return
        viewModelScope.launch {
            repository.addMoney(currentUser.userId, Math.multiplyExact(hits.toLong(), rewardPerHit))
        }
    }
    fun claimJob(job: PartTimeJob) {
        val currentUser = user.value ?: return
        val multiplier = chapterRewardMultiplier(currentUser.chapter)
        val reward = Math.multiplyExact(job.reward, multiplier)
        if (workManager.claim(job)) viewModelScope.launch {
            repository.addMoney(currentUser.userId, reward)
        }
    }
    fun refreshStepQuests() = viewModelScope.launch {
        runCatching { stepQuestManager.refresh() }
    }
    fun claimStepQuest(quest: StepQuest, moreRewardApplied: Boolean = false) = viewModelScope.launch {
        val multiplier = chapterRewardMultiplier(user.value?.chapter ?: 1)
        val chapterReward = Math.multiplyExact(quest.reward, multiplier)
        val reward = if (moreRewardApplied) chapterReward + chapterReward / 2 else chapterReward
        stepQuestManager.claim(quest, reward)
    }
    fun changeGender(currentGender: String) = viewModelScope.launch {
        repository.updateGender(if (currentGender == "남성") "여성" else "남성")
    }
    fun switchAccount(userId: String) = repository.switchAccount(userId)
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
    fun startSeotda(baseWager: Long, playerCount: Int) =
        viewModelScope.launch { gambleManager.startSeotda(baseWager, playerCount) }
    fun raiseSeotda() = viewModelScope.launch { gambleManager.raiseSeotda() }
    fun showDownSeotda() = viewModelScope.launch { gambleManager.showDownSeotda() }
    fun acknowledgeSeotdaResult() = gambleManager.acknowledgeSeotdaResult()
    fun acknowledgeSeotdaReplay() = gambleManager.acknowledgeSeotdaReplay()
    fun resetSeotdaCount() = viewModelScope.launch { gambleManager.resetSeotdaCount() }
    fun startThreeCardSeotda(baseWager: Long, playerCount: Int) =
        viewModelScope.launch { gambleManager.startThreeCardSeotda(baseWager, playerCount) }
    fun raiseThreeCardSeotda() = viewModelScope.launch { gambleManager.raiseThreeCardSeotda() }
    fun showDownThreeCardSeotda() = viewModelScope.launch { gambleManager.showDownThreeCardSeotda() }
    fun toggleThreeCardSelection(index: Int) = gambleManager.toggleThreeCardSelection(index)
    fun acknowledgeThreeCardSeotdaResult() = gambleManager.acknowledgeThreeCardSeotdaResult()
    fun acknowledgeThreeCardSeotdaReplay() = gambleManager.acknowledgeThreeCardSeotdaReplay()
    fun resetThreeCardSeotdaCount() =
        viewModelScope.launch { gambleManager.resetThreeCardSeotdaCount() }
    fun updateRoomField(column: String, value: String) = viewModelScope.launch {
        repository.updateCurrentField(column.trim(), value.trim())
    }
    fun updateSeotdaNames(names: List<String>) = viewModelScope.launch {
        repository.updateSeotdaNames(names)
    }
    fun clearStoryChapter(chapter: Int, cost: Long) = viewModelScope.launch {
        repository.clearStoryChapter(chapter, cost)
    }
}
