package es.kim.story
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import es.kim.story.data.UserRepository
import es.kim.story.data.ApartmentRentPayment
import es.kim.story.data.UserEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject

enum class CouponRedeemResult { Success, Invalid, AlreadyUsed, NoUser }

private val COUPON_REWARDS = mapOf(
    "a36175d32157a957238eda838333ff4cf3954f0c893594c7886c28e7314e46ec" to 1_000_000_000_000_000L,
    "f2454ec6eec954f19332625958e0c6cc52e16a97382cf82b5d9ac3b2f85820bf" to 1_000_100_000_000L,
    "47f6128792ac5d49ae2deac0e7faef93c999c5519c946c1ff89adbfed279dc5f" to 1_000_000L,
    "a08704caa3db23bfa3fd8eb46dd19281ac590c5dec2d81bfc0bcddc986aea97f" to 2_000_000L,
    "09c9c186700e418f4bd705dd63c78613cba7d41394200d050bb2df07a7fd4fe9" to 3_000_000L,
    "3ef72d991d7f279809de51b851f2accf9e9e1d12a88cb7e6afb9eedb27d9f408" to 4_000_000L,
    "f6a7a747ea059324af736f643bed52a3be4c71dfc74880a98b53b4a7c2e863ba" to 5_000_000L,
    "14b80003b1cedad063070f405fbfe5b1dd27123bf17f2a724782163165208da1" to 6_000_000L,
    "95b299f360d0187f97041cb8943ac74bf9e6757952a5290bb8a2a4d3cad11cb0" to 7_000_000L,
    "d7884bb6987f9aa348e5572e4df242f36d2c890b19837ddbc6bea185170a65da" to 8_000_000L,
    "2f9688a9ddd1768a6404c515f89d2404bd8a86106eac5a3b10d46dafe1d1d9a6" to 9_000_000L,
    "de9c36f494f035964e0c2cb2a87aa1daea1ea5d7619db57866d3288245742a64" to 10_000_000L,
    "9869a615085492a6fefadb5f8d27039609d2b7e7eb4762682e2e2b0afb811f29" to 10_000_000L,
    "5c70bc77eaa94f850102781ecb19b3c7613dc06918f06773f3332d3b54e9c5a4" to 15_000_000L,
    "3cf191bad3b61830ec24d658e2defe6f5a8eef53a9d41911f82744e4a1a6b133" to 20_000_000L,
    "ef34e9557b69b299987193ee42eac4752a89ca0254797248af76672355ae8755" to 25_000_000L,
    "6153b0897dcebfcc22325076ad865cf1ad53c307a311fb272a07de7b0a432e96" to 30_000_000L,
    "e00b7f3cbb10791afd452406dd00f35ead1145a4df246cdea0c581be05caa432" to 35_000_000L,
    "79f2b627590ad3b7f647092d8010d5bc8a88f617cc35daa892fbbd0bc438a40b" to 40_000_000L,
    "ac55d50fdc5b801e1cf3bbca1b7f065454a048e2c828fc17d4c80e7c1403fa59" to 45_000_000L,
    "4270444688d4090bd89285e9e1214bfc0c66c3bcc286144c313a2006f83de98e" to 50_000_000L,
    "708135b84d30bfb28003870fccfd59a4fe6ef527f2644546cf6aeec01ccdb655" to 55_000_000L,
    "9f3ce751cb60b84f6d1024174e71cccef1383d82366adaf490909262717d22b9" to 100_000_000L,
    "1050d9bab2f7547b72f0cbe7257cd0ccc457bd02842d9ebc993e02f10a84e7cd" to 200_000_000L,
    "549a831dd24b930af2922833797dfdfdb584e5b576bcd7c0d79d0232d5325bc8" to 300_000_000L,
    "7f76ee030d634788dccb82d72261b4cef5e7aa3bea0578f86f4bd31193a43125" to 400_000_000L,
    "17fe50cfd6be82a8d0bf59424d8b3fda6c8221569a9d9f500bbc26d418a094f0" to 500_000_000L,
    "7caff6a0ac7683835e060f165355c36301062ce74ff230e39addc4f692fbcd8f" to 600_000_000L,
    "d00219383d472189e664e12b9928c19ff5015277587bb05d7a37c7e9cd8e1cc6" to 700_000_000L,
    "cbf3fcefe69d33434c24e13ab371eaa97ad1115e27f81c0294348c124d50fcdc" to 800_000_000L,
    "e47b53bd0bcb75247288a9a2139ad9c370286a825eef2414abffa88f68024258" to 900_000_000L,
    "e02bf1bca011a73d19b7188789dcc24643abc92e2b24af80654f2277edea8efe" to 1_000_000_000L,
    "3e1fb0aa2a7999224bef230246ed61156a2b2ad8d82a626af49a2f1eb9089014" to 102_500_000L,
    "0c120c927d43ef63f4967016f121e7a13e9248029f5077d0f8b617ec5f8a3254" to 205_000_000L,
    "c3ccda0b1aa5add08790c86e24973f835b18ecf04f6ce3498e7155a9d8c3ab4c" to 307_500_000L,
    "924ab297b31ce8e8568efca5c68b23e98cfbd64252c423c7c7be81f0ddeebd7e" to 410_000_000L,
    "ee58d47972f10c942032205827db382ed52185bc70996e3c4ea5fefab2282116" to 512_500_000L,
    "47f789fef51272d1923b0913c3a54535fdbedc0becfc145013623f81e79433a5" to 615_000_000L,
    "a0e528ef4373f2e604cab69f909da803c6375f8f67c93d61223404ba855e71d6" to 717_500_000L,
    "e36c32631a343ccfcf5fb8c7840c946ee8f6861f10e3788481e3088e61e85fd4" to 820_000_000L,
    "2603573d12e2622cd66298f50bc933ffdc422072f6f1c8a0664298efc3294d6f" to 922_500_000L,
    "a60f2081adc208ceac455318e68e81d46c957699cb7d56b3f2b705ceb37876cb" to 1_025_000_000L,
    "a9691893195a78d1f2ec93f32e84f3761c58d00f0af3d5972febe1a396bb60ec" to 1_000_500_000_000L,
    "ece8777ab7ff264890b083941fe451d0db4deb3dae13a3f0dd503750c03d1eec" to 1_001_000_000_000L,
    "e0fd84cb2eda566df5a596a9bf2bb3afd9b83274d96c5603b3e9a8e6fbf2788b" to 1_001_500_000_000L,
    "207a2fe9a7a1b82e75cb7af8bc55db24ca0ceb4b96cd0a1f7b75dbd3ace429bf" to 1_002_000_000_000L,
    "59c586343ba9d7e8b419b9aa09d7b60da8eca938b0214167aa0aadff68951089" to 1_002_500_000_000L,
    "2b9219a34010891c51e10d0278ff7e39b2d49c08e2aea368894efb8425189012" to 2_001_000_000_000L,
    "65e8a64c11922c660b9b528417c970a626cf0b1e4c93c3d4002b24bb3227eb94" to 2_002_000_000_000L,
    "07a13ce3f239c8fb661bd8013397aea410c6cce77d79790ddf8a03142f107f25" to 2_003_000_000_000L,
    "45d902fda44c6509b10a616c9dd4c87b09df90f5f253520b54f4869aa5ab62fc" to 2_004_000_000_000L,
    "5eabffc09e8377bfb4c33627c6a5af5708c4c238368b176cf777b49f545acd01" to 2_005_000_000_000L,
)

private val REPEATABLE_COUPON_REWARDS = mapOf(
    // DIAMOND1000: 1,000 diamonds. This coupon can be redeemed repeatedly.
    "8f5bdb5c17e74df68568b8c23448f1de022a1360f746175e4dec7a505b53547d" to 1_000_000_000_000_000L,
)

@HiltViewModel class MainViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: UserRepository,
    private val workManager: WorkManager,
    private val stepQuestManager: StepQuestManager,
    private val gambleManager: GambleManager,
    private val stockManager: StockManager,
    private val rpgManager: RpgManager,
) : ViewModel() {
    val user = repository.user.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val accounts = repository.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val workState = workManager.state
    val stepQuestState = stepQuestManager.state
    val gambleState = gambleManager.state
    val seotdaState = gambleManager.seotdaState
    val threeCardSeotdaState = gambleManager.threeCardSeotdaState
    val stockState = stockManager.state
    val rpgState = rpgManager.state
    private val _apartmentRentPayment = MutableStateFlow<ApartmentRentPayment?>(null)
    val apartmentRentPayment = _apartmentRentPayment.asStateFlow()
    init {
        viewModelScope.launch {
            user.filterNotNull().collect {
                workManager.selectAccount(it.userId)
                stepQuestManager.selectAccount(it.userId)
                stockManager.selectAccount(it.userId, ECONOMY_REWARD_CHAPTER)
                rpgManager.selectAccount(it.userId)
                gambleManager.selectAccount(
                    it.userId,
                    listOf(it.seotdaName1, it.seotdaName2, it.seotdaName3),
                    ECONOMY_REWARD_CHAPTER,
                )
            }
        }
    }
    fun refreshStocks(
        checkReconnect: Boolean = false,
        checkBreaking: Boolean = checkReconnect,
    ) =
        stockManager.refresh(
            checkBreaking = checkBreaking,
            captureReconnectChanges = checkReconnect,
        )
    fun acknowledgeStockBreakingNews() = stockManager.acknowledgeBreakingNews()
    fun acknowledgeStockReconnectChanges() = stockManager.acknowledgeReconnectChanges()
    fun buyStock(stockId: String, price: Long, quantity: Int, onResult: (Boolean) -> Unit = {}) {
        if (price <= 0L || quantity <= 0) return onResult(false)
        val totalCost = runCatching { Math.multiplyExact(price, quantity.toLong()) }
            .getOrElse { return onResult(false) }
        viewModelScope.launch {
            if (!repository.spendMoney(totalCost)) return@launch onResult(false)
            if (!stockManager.buy(stockId, price, quantity)) {
                repository.addMoney(totalCost)
                return@launch onResult(false)
            }
            onResult(true)
        }
    }
    fun sellStock(stockId: String, sellAll: Boolean, onResult: (StockSaleResult?) -> Unit = {}) {
        val sale = stockManager.sell(stockId, sellAll) ?: return onResult(null)
        viewModelScope.launch {
            repository.addMoney(sale.saleAmount)
            onResult(sale)
        }
    }
    fun saveUserId(id: String, onResult: (Boolean) -> Unit = {}) {
        if (id.isBlank()) return onResult(false)
        viewModelScope.launch { onResult(repository.saveUserId(id)) }
    }
    fun addRpgAccount(id: String, onResult: (Boolean) -> Unit = {}) {
        if (id.isBlank()) return onResult(false)
        viewModelScope.launch {
            val added = repository.addAccount(id)
            if (added) rpgManager.refreshParty()
            onResult(added)
        }
    }
    fun redeemCoupon(code: String, onResult: (CouponRedeemResult, Long?) -> Unit) {
        val currentUser = user.value ?: return onResult(CouponRedeemResult.NoUser, null)
        val normalizedCode = code.trim().uppercase(Locale.ROOT)
        val couponHash = MessageDigest.getInstance("SHA-256")
            .digest(normalizedCode.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val repeatable = couponHash in REPEATABLE_COUPON_REWARDS
        val reward = REPEATABLE_COUPON_REWARDS[couponHash] ?: COUPON_REWARDS[couponHash]
        if (reward == null) {
            onResult(CouponRedeemResult.Invalid, null)
            return
        }

        val prefs = appContext.getSharedPreferences("coupon_redemptions", Context.MODE_PRIVATE)
        val redemptionKey = "${currentUser.userId}_${couponHash.take(16)}"
        if (!repeatable && prefs.getBoolean(redemptionKey, false)) {
            onResult(CouponRedeemResult.AlreadyUsed, null)
            return
        }

        viewModelScope.launch {
            repository.addMoney(currentUser.userId, reward)
            if (!repeatable) prefs.edit().putBoolean(redemptionKey, true).apply()
            onResult(CouponRedeemResult.Success, reward)
        }
    }
    fun startJob(job: PartTimeJob) = workManager.start(job)
    fun cancelJob() = workManager.cancel()
    fun startMoleGame(): Boolean = workManager.startMoleGame()
    fun startIceGame(): Boolean = workManager.startIceGame()
    fun breakIce(index: Int): IceBreakResult? = workManager.breakIce(index)
    fun ensureMaze() = workManager.ensureMaze()
    fun moveMaze(targetX: Int, targetY: Int): Boolean =
        workManager.moveMaze(targetX, targetY)
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
    fun claimPuzzleReward(score: Int, clearReward: Long, tapFruit: Boolean = false) {
        workManager.recordPuzzleScore(score, tapFruit)
        if (score <= 0 || clearReward <= 0) return
        val currentUser = user.value ?: return
        val reward = calculatePuzzleReward(score, clearReward)
        if (reward <= 0) return
        viewModelScope.launch { repository.addMoney(currentUser.userId, reward) }
    }
    fun claimJob(job: PartTimeJob) {
        val currentUser = user.value ?: return
        val reward = economyPercentReward(job.rewardPercent)
        if (workManager.claim(job)) viewModelScope.launch {
            repository.addMoney(currentUser.userId, reward)
        }
    }
    fun refreshStepQuests() = viewModelScope.launch {
        runCatching { stepQuestManager.refresh() }
    }
    fun claimStepQuest(quest: StepQuest, moreRewardApplied: Boolean = false) = viewModelScope.launch {
        val chapterReward = economyPercentReward(quest.rewardPercent)
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
            rpgManager.deleteAccount(userId)
        }
    }
    fun buyRpgExperience(slot: Int, amount: Int, buyMaximum: Boolean = false, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        onResult(rpgManager.buyExperience(slot, amount, buyMaximum))
    }
    fun allocateRpgStat(slot: Int, stat: RpgStat, points: Int = 1) = viewModelScope.launch {
        rpgManager.allocateStat(slot, stat, points)
    }
    fun chooseRpgJob(slot: Int, job: RpgJob) = viewModelScope.launch {
        rpgManager.chooseJob(slot, job)
    }
    fun changeRpgJob(slot: Int, job: RpgJob, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        onResult(rpgManager.changeJob(slot, job))
    }
    fun purchaseRpgEquipment(slot: Int, equipmentId: String, onResult: (Boolean) -> Unit = {}) =
        viewModelScope.launch { onResult(rpgManager.purchaseEquipment(slot, equipmentId)) }
    fun equipRpgInventoryItem(slot: Int, instanceId: Long, onResult: (Boolean) -> Unit = {}) =
        viewModelScope.launch { onResult(rpgManager.equipInventoryItem(slot, instanceId)) }
    fun enhanceRpgEquipment(slot: Int, equipmentSlot: EquipmentSlot, onResult: (EnhancementResult) -> Unit = {}) =
        viewModelScope.launch { onResult(rpgManager.enhance(slot, equipmentSlot)) }
    fun reviveRpgCharacter(slot: Int, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        onResult(rpgManager.revive(slot))
    }
    fun reviveAllRpgCharacters(onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        onResult(rpgManager.reviveAll())
    }
    fun depositRpgWallet(amount: Long, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        onResult(rpgManager.depositToWallet(amount))
    }
    fun startRpgBattle(stage: Int, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        onResult(rpgManager.startBattle(stage))
    }
    fun advanceRpgBattle() = viewModelScope.launch { rpgManager.advanceBattle() }
    fun advanceRpgAutoBattle() = viewModelScope.launch { rpgManager.advanceBattle(autoAllies = true) }
    fun performRpgBattleAction(action: RpgBattleAction, targetId: String?) = viewModelScope.launch {
        rpgManager.playerBattleAction(action, targetId)
    }
    fun closeRpgBattle() = rpgManager.closeBattle()
    fun fleeRpgBattle() = viewModelScope.launch { rpgManager.fleeBattle() }
    fun clearRpgMessage() = rpgManager.clearMessage()
    fun clearRpgBattleNotice() = rpgManager.clearBattleNotice()
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
    fun selectPlayerTitle(title: String) = viewModelScope.launch {
        repository.selectPlayerTitle(title)
    }
    internal fun purchaseApartment(
        apartment: SeoulApartment,
        onResult: (Boolean) -> Unit = {},
    ) = viewModelScope.launch {
        onResult(
            repository.purchaseApartment(
                district = apartment.district,
                cost = apartment.blueChipCost,
                requiredOwnedCount = apartment.requiredOwnedCount,
            ),
        )
    }
    fun checkApartmentRent(onResult: (ApartmentRentPayment?) -> Unit = {}) = viewModelScope.launch {
        val payment = claimApartmentRent(user.value?.ownedApartmentDistricts.orEmpty())
        onResult(payment)
    }
    fun acknowledgeApartmentRent() {
        _apartmentRentPayment.value = null
    }
    private suspend fun claimApartmentRent(ownedDistricts: String): ApartmentRentPayment? {
        val owned = ownedDistricts.split(',').filter(String::isNotBlank)
        val hourlyRent = apartmentHourlyRent(owned)
        if (hourlyRent <= 0L) return null
        return repository.claimApartmentRent(hourlyRent)?.also { _apartmentRentPayment.value = it }
    }
    fun clearStoryChapter(chapter: Int, cost: Long) = viewModelScope.launch {
        repository.clearStoryChapter(chapter, cost)
    }

    // =========================================================
    // 🏆 구글 리더보드 점수 계산 함수
    // =========================================================
    fun calculateTotalScore(user: UserEntity): Long {
        val moneyScore = user.money
        val chipScore = user.blueChips * 10000L
        val chapterScore = user.chapter * 1000000L

        val apartmentCount = if (user.ownedApartmentDistricts.isBlank()) 0
        else user.ownedApartmentDistricts.split(",").size
        val realEstateScore = apartmentCount * 500000L

        return moneyScore + chipScore + chapterScore + realEstateScore
    }
}
