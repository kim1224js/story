package es.kim.story

import androidx.room.withTransaction
import es.kim.story.data.AppDatabase
import es.kim.story.data.RpgCharacterEntity
import es.kim.story.data.RpgDao
import es.kim.story.data.RpgProgressEntity
import es.kim.story.data.RpgEquipmentInventoryEntity
import es.kim.story.data.UserDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RpgManager @Inject constructor(
    private val database: AppDatabase,
    private val dao: RpgDao,
    private val userDao: UserDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(RpgUiState())
    val state = _state.asStateFlow()
    private var accountJob: Job? = null

    fun selectAccount(userId: String) {
        if (userId.isBlank() || _state.value.userId == userId) return
        accountJob?.cancel()
        accountJob = scope.launch {
            ensureParty(userId)
            if ((dao.getProgress(PARTY_OWNER_ID)?.highestClearedStage ?: 0) >= 9) {
                userDao.unlockHeroTitle(userId)
            }
            combine(dao.observeParty(PARTY_OWNER_ID), dao.observeProgress(PARTY_OWNER_ID), dao.observeInventory(PARTY_OWNER_ID)) { party, progress, inventory ->
                Triple(party, progress ?: RpgProgressEntity(PARTY_OWNER_ID), inventory)
            }.collect { (party, progress, inventory) ->
                _state.value = _state.value.copy(
                    userId = userId,
                    party = party,
                    unlockedStage = progress.unlockedStage,
                    highestClearedStage = progress.highestClearedStage,
                    walletBalance = progress.walletBalance,
                    inventory = inventory,
                )
            }
        }
    }

    fun refreshParty() {
        val activeUserId = _state.value.userId
        if (activeUserId.isNotBlank()) scope.launch { ensureParty(activeUserId) }
    }

    private suspend fun ensureParty(activeUserId: String) {
        database.withTransaction {
            val accounts = userDao.getAllUsers().take(3)
            val globalParty = dao.getParty(PARTY_OWNER_ID)
            val legacyParty = if (globalParty.isEmpty()) dao.getParty(activeUserId) else emptyList()
            val synced = accounts.mapIndexed { slot, account ->
                val saved = globalParty.firstOrNull { it.name == account.userId }
                    ?: legacyParty.getOrNull(slot)
                    ?: RpgCharacterEntity(PARTY_OWNER_ID, slot, account.userId, slot)
                saved.copy(
                    userId = PARTY_OWNER_ID,
                    slot = slot,
                    name = account.userId,
                    avatarIndex = if (saved.jobType() == RpgJob.NOVICE) slot else saved.avatarIndex,
                )
            }
            dao.deleteParty(PARTY_OWNER_ID)
            if (synced.isNotEmpty()) dao.saveCharacters(synced)
            dao.deleteLegacyParties(PARTY_OWNER_ID)

            if (dao.getProgress(PARTY_OWNER_ID) == null) {
                val legacyProgress = dao.getProgress(activeUserId)
                dao.saveProgress((legacyProgress ?: RpgProgressEntity(PARTY_OWNER_ID)).copy(userId = PARTY_OWNER_ID))
            }
        }
    }

    suspend fun buyExperience(slot: Int, requestedAmount: Int, buyMaximum: Boolean = false): Boolean {
        return database.withTransaction {
            val character = dao.getParty(PARTY_OWNER_ID).firstOrNull { it.slot == slot } ?: return@withTransaction false
            if (character.isDead) return@withTransaction false
            val progress = dao.getProgress(PARTY_OWNER_ID) ?: return@withTransaction false
            val affordableBundles = progress.walletBalance / EXP_BUNDLE_COST
            val bundles = if (buyMaximum) {
                affordableBundles.coerceAtMost(Int.MAX_VALUE.toLong() / EXP_BUNDLE_SIZE).toInt()
            } else {
                if (requestedAmount <= 0 || requestedAmount % EXP_BUNDLE_SIZE != 0) return@withTransaction false
                requestedAmount / EXP_BUNDLE_SIZE
            }
            if (bundles <= 0) return@withTransaction false
            val cost = EXP_BUNDLE_COST * bundles
            if (!spendWallet(PARTY_OWNER_ID, cost)) return@withTransaction false
            dao.saveCharacter(addExperience(character, bundles * EXP_BUNDLE_SIZE))
            true
        }
    }

    suspend fun allocateStat(slot: Int, stat: RpgStat, requestedPoints: Int = 1): Boolean {
        if (requestedPoints <= 0) return false
        return database.withTransaction {
            val character = dao.getParty(PARTY_OWNER_ID).firstOrNull { it.slot == slot }
                ?: return@withTransaction false
            val points = minOf(requestedPoints, character.statPoints)
            if (points <= 0 || character.isDead) return@withTransaction false
            val updated = when (stat) {
                RpgStat.STR -> character.copy(strength = character.strength + points)
                RpgStat.DEX -> character.copy(dexterity = character.dexterity + points)
                RpgStat.INT -> character.copy(intelligence = character.intelligence + points)
                RpgStat.LUK -> character.copy(luck = character.luck + points)
                RpgStat.HP -> character.copy(maxHp = character.maxHp + 50 * points, currentHp = character.currentHp + 50 * points)
                RpgStat.MP -> character.copy(maxMp = character.maxMp + 50 * points, currentMp = character.currentMp + 50 * points)
            }.copy(statPoints = character.statPoints - points)
            dao.saveCharacter(updated)
            true
        }
    }

    suspend fun chooseJob(slot: Int, job: RpgJob): Boolean {
        if (job == RpgJob.NOVICE) return false
        val character = dao.getParty(PARTY_OWNER_ID).firstOrNull { it.slot == slot } ?: return false
        if (character.level < 30 || character.jobType() != RpgJob.NOVICE) return false
        if (job == RpgJob.SCORPION_SOLDIER || job == RpgJob.PARROT_ROGUE) {
            unequipCurrentInventoryItem(character, EquipmentSlot.WEAPON)
        }
        if (job == RpgJob.PARROT_ROGUE) {
            unequipCurrentInventoryItem(character, EquipmentSlot.ARMOR)
        }
        val clearedEquipment = when (job) {
            RpgJob.SCORPION_SOLDIER -> character.copy(weaponId = "", weaponEnhancement = 0)
            RpgJob.PARROT_ROGUE -> character.copy(weaponId = "", weaponEnhancement = 0, armorId = "", armorEnhancement = 0)
            else -> character
        }
        dao.saveCharacter(clearedEquipment.copy(job = job.name, avatarIndex = job.avatarIndex))
        return true
    }

    suspend fun changeJob(slot: Int, job: RpgJob): Boolean {
        if (job == RpgJob.NOVICE) return false
        return database.withTransaction {
            val character = dao.getParty(PARTY_OWNER_ID).firstOrNull { it.slot == slot }
                ?: return@withTransaction false
            if (character.jobType() == RpgJob.NOVICE || character.jobType() == job || character.isDead) {
                return@withTransaction false
            }
            unequipCurrentInventoryItem(character, EquipmentSlot.WEAPON)
            unequipCurrentInventoryItem(character, EquipmentSlot.ARMOR)
            dao.saveCharacter(character.resetForJobChange(job))
            true
        }
    }

    suspend fun purchaseEquipment(slot: Int, equipmentId: String): Boolean {
        return database.withTransaction {
            val character = dao.getParty(PARTY_OWNER_ID).firstOrNull { it.slot == slot } ?: return@withTransaction false
            val item = rpgEquipment.firstOrNull { it.id == equipmentId } ?: return@withTransaction false
            val job = character.jobType()
            if (character.level < item.requiredLevel || character.isDead ||
                job == RpgJob.PARROT_ROGUE || (job == RpgJob.SCORPION_SOLDIER && item.slot == EquipmentSlot.WEAPON)
            ) return@withTransaction false
            if (!spendWallet(PARTY_OWNER_ID, item.price)) return@withTransaction false
            unequipCurrentInventoryItem(character, item.slot)
            dao.saveInventoryItem(
                RpgEquipmentInventoryEntity(
                    ownerId = PARTY_OWNER_ID,
                    equipmentId = item.id,
                    equippedCharacterSlot = character.slot,
                ),
            )
            dao.saveCharacter(if (item.slot == EquipmentSlot.WEAPON) {
                character.copy(weaponId = item.id, weaponEnhancement = 0)
            } else character.copy(armorId = item.id, armorEnhancement = 0))
            true
        }
    }

    suspend fun equipInventoryItem(slot: Int, instanceId: Long): Boolean {
        return database.withTransaction {
            val character = dao.getParty(PARTY_OWNER_ID).firstOrNull { it.slot == slot } ?: return@withTransaction false
            val inventoryItem = dao.getInventory(PARTY_OWNER_ID).firstOrNull { it.instanceId == instanceId && it.equippedCharacterSlot == null }
                ?: return@withTransaction false
            val item = rpgEquipment.firstOrNull { it.id == inventoryItem.equipmentId } ?: return@withTransaction false
            val job = character.jobType()
            if (character.isDead || character.level < item.requiredLevel || job == RpgJob.PARROT_ROGUE ||
                (job == RpgJob.SCORPION_SOLDIER && item.slot == EquipmentSlot.WEAPON)
            ) return@withTransaction false
            unequipCurrentInventoryItem(character, item.slot)
            dao.saveInventoryItem(inventoryItem.copy(equippedCharacterSlot = slot))
            dao.saveCharacter(
                if (item.slot == EquipmentSlot.WEAPON) character.copy(weaponId = item.id, weaponEnhancement = inventoryItem.enhancement)
                else character.copy(armorId = item.id, armorEnhancement = inventoryItem.enhancement),
            )
            true
        }
    }

    private suspend fun unequipCurrentInventoryItem(character: RpgCharacterEntity, slot: EquipmentSlot) {
        val equipped = dao.getInventory(PARTY_OWNER_ID).firstOrNull { inventory ->
            inventory.equippedCharacterSlot == character.slot &&
                rpgEquipment.firstOrNull { it.id == inventory.equipmentId }?.slot == slot
        }
        if (equipped != null) dao.saveInventoryItem(equipped.copy(equippedCharacterSlot = null))
    }

    suspend fun enhance(slot: Int, equipmentSlot: EquipmentSlot): EnhancementResult {
        return database.withTransaction {
            val character = dao.getParty(PARTY_OWNER_ID).firstOrNull { it.slot == slot }
                ?: return@withTransaction EnhancementResult.Unavailable
            val id = if (equipmentSlot == EquipmentSlot.WEAPON) character.weaponId else character.armorId
            val item = rpgEquipment.firstOrNull { it.id == id } ?: return@withTransaction EnhancementResult.Unavailable
            if (!spendWallet(PARTY_OWNER_ID, item.enhancementCost)) return@withTransaction EnhancementResult.NotEnoughMoney
            val current = if (equipmentSlot == EquipmentSlot.WEAPON) character.weaponEnhancement else character.armorEnhancement
            if (Random.nextBoolean()) {
                dao.saveCharacter(if (equipmentSlot == EquipmentSlot.WEAPON) character.copy(weaponEnhancement = current + 1)
                else character.copy(armorEnhancement = current + 1))
                updateInventoryEnhancement(character.slot, equipmentSlot, current + 1)
                return@withTransaction EnhancementResult.Success
            }
            val downChance = when {
                current >= 50 -> 70
                current >= 30 -> 50
                current >= 10 -> 30
                else -> 0
            }
            if (current > 0 && Random.nextInt(100) < downChance) {
                dao.saveCharacter(if (equipmentSlot == EquipmentSlot.WEAPON) character.copy(weaponEnhancement = current - 1)
                else character.copy(armorEnhancement = current - 1))
                updateInventoryEnhancement(character.slot, equipmentSlot, current - 1)
                return@withTransaction EnhancementResult.FailedAndDowngraded
            }
            EnhancementResult.Failed
        }
    }

    private suspend fun updateInventoryEnhancement(characterSlot: Int, slot: EquipmentSlot, enhancement: Int) {
        val equipped = dao.getInventory(PARTY_OWNER_ID).firstOrNull { inventory ->
            inventory.equippedCharacterSlot == characterSlot &&
                rpgEquipment.firstOrNull { it.id == inventory.equipmentId }?.slot == slot
        }
        if (equipped != null) dao.saveInventoryItem(equipped.copy(enhancement = enhancement))
    }

    suspend fun depositToWallet(amount: Long): Boolean {
        if (amount <= 0L) return false
        return database.withTransaction {
            val userId = _state.value.userId
            val progress = dao.getProgress(PARTY_OWNER_ID) ?: return@withTransaction false
            if (amount > Long.MAX_VALUE - progress.walletBalance) return@withTransaction false
            if (userDao.spendMoney(userId, amount) != 1) return@withTransaction false
            dao.saveProgress(progress.copy(walletBalance = progress.walletBalance + amount))
            true
        }
    }

    private suspend fun spendWallet(userId: String, amount: Long): Boolean {
        if (amount <= 0L) return false
        val progress = dao.getProgress(userId) ?: return false
        if (progress.walletBalance < amount) return false
        dao.saveProgress(progress.copy(walletBalance = progress.walletBalance - amount))
        return true
    }

    suspend fun revive(slot: Int): Boolean {
        return database.withTransaction {
            val character = dao.getParty(PARTY_OWNER_ID).firstOrNull { it.slot == slot }
                ?: return@withTransaction false
            if (!character.isDead || !spendWallet(PARTY_OWNER_ID, REVIVE_COST)) {
                return@withTransaction false
            }
            dao.saveCharacter(
                character.copy(
                    currentHp = character.combatMaxHp(),
                    currentMp = character.combatMaxMp(),
                    isDead = false,
                ),
            )
            true
        }
    }

    suspend fun reviveAll(): Boolean {
        return database.withTransaction {
            val deadCharacters = dao.getParty(PARTY_OWNER_ID).filter { it.isDead }
            if (deadCharacters.isEmpty()) return@withTransaction false
            val totalCost = REVIVE_COST * deadCharacters.size
            if (!spendWallet(PARTY_OWNER_ID, totalCost)) return@withTransaction false
            dao.saveCharacters(deadCharacters.map { character ->
                character.copy(
                    currentHp = character.combatMaxHp(),
                    currentMp = character.combatMaxMp(),
                    isDead = false,
                )
            })
            true
        }
    }

    suspend fun startBattle(stageNumber: Int): Boolean {
        val progress = dao.getProgress(PARTY_OWNER_ID) ?: return false
        val party = dao.getParty(PARTY_OWNER_ID)
        if (stageNumber !in 1..progress.unlockedStage || party.isEmpty() || party.any { it.isDead }) return false
        if (stageNumber == 9 && !qualifiesForDemonWorld(party)) return false
        val allies = party.map { it.toBattleUnit() }
        val enemies = enemiesForWave(stageNumber, 1)
        val order = (allies + enemies).sortedWith(compareByDescending<BattleUnit> { it.dex }.thenBy { Random.nextInt() }).map { it.id }
        _state.value = _state.value.copy(
            battle = RpgBattleState(stageNumber, 1, allies, enemies, order),
            message = null,
        )
        return true
    }

    suspend fun advanceBattle(autoAllies: Boolean = false) {
        val battle = _state.value.battle ?: return
        if (battle.finished) return
        val all = battle.allies + battle.enemies
        val actorId = battle.order.getOrNull(battle.orderIndex)
        val actor = all.firstOrNull { it.id == actorId && it.alive }
        var allies = battle.allies
        var enemies = battle.enemies
        val messages = mutableListOf<String>()
        if (actor != null) {
            if (actor.paralyzed > 0) messages += "${actor.name}은(는) 행동할 수 없다!"
            else if (actor.friendly) {
                if (!autoAllies) return
                val result = allyAction(actor, allies, enemies)
                allies = result.first; enemies = result.second; messages += result.third
            }
            else {
                val result = enemyAction(actor, allies, enemies)
                allies = result.first; enemies = result.second; messages += result.third
            }
        }
        resolveBattleTurn(battle, allies, enemies, messages, actor?.id)
    }

    suspend fun playerBattleAction(action: RpgBattleAction, targetId: String?) {
        val battle = _state.value.battle ?: return
        if (battle.finished) return
        val actorId = battle.order.getOrNull(battle.orderIndex) ?: return
        val actor = battle.allies.firstOrNull { it.id == actorId && it.alive } ?: return
        if (actor.paralyzed > 0) {
            resolveBattleTurn(battle, battle.allies, battle.enemies, listOf("${actor.name}은(는) 행동할 수 없다!"), actor.id)
            return
        }
        val barkUsed = actor.job == RpgJob.DOG_KNIGHT && action == RpgBattleAction.SKILL_TWO
        val beforeParalyzed = battle.enemies.filter { it.alive && it.paralyzed <= 0 }.map { it.id }.toSet()
        val result = manualAllyAction(actor, battle.allies, battle.enemies, action, targetId) ?: return
        val barkSuccessNames = if (barkUsed) {
            result.second.filter { it.id in beforeParalyzed && it.paralyzed > 0 }.map { it.name }
        } else emptyList()
        resolveBattleTurn(battle, result.first, result.second, listOf(result.third), actor.id)
        if (barkUsed) {
            val notice = if (barkSuccessNames.isEmpty()) "짖기 실패! 행동 불능에 걸린 적이 없습니다."
            else "짖기 성공! ${barkSuccessNames.joinToString(", ")} 행동 불능"
            _state.value.battle?.let { _state.value = _state.value.copy(battle = it.copy(notice = notice)) }
        }
    }

    private suspend fun resolveBattleTurn(
        battle: RpgBattleState,
        alliesInput: List<BattleUnit>,
        enemiesInput: List<BattleUnit>,
        messages: List<String>,
        actorId: String?,
    ) {
        var allies = alliesInput
        var enemies = enemiesInput
        val waveCleared = enemies.none { it.alive }
        val defeat = allies.none { it.alive }
        if (waveCleared && !defeat && battle.wave < 10) {
            val nextWave = battle.wave + 1
            allies = allies.map(::tickEffects)
            val nextEnemies = enemiesForWave(battle.stage, nextWave)
            val nextOrder = (allies.filter { it.alive } + nextEnemies)
                .sortedWith(compareByDescending<BattleUnit> { it.dex }.thenBy { Random.nextInt() }).map { it.id }
            _state.value = _state.value.copy(
                battle = battle.copy(
                    wave = nextWave, allies = allies, enemies = nextEnemies, order = nextOrder,
                    orderIndex = 0, round = 1,
                    log = (battle.log + messages + "ROUND $nextWave 시작${if (nextWave == 10) " · 보스 등장!" else ""}").takeLast(30),
                    motionUnitId = actorId,
                ),
            )
            return
        }
        val victory = waveCleared && battle.wave == 10
        var nextIndex = battle.orderIndex + 1
        var round = battle.round
        val aliveIds = (allies.filter { it.alive } + enemies.filter { it.alive }).map { it.id }.toSet()
        while (nextIndex < battle.order.size && battle.order[nextIndex] !in aliveIds) nextIndex++
        if (nextIndex >= battle.order.size && !victory && !defeat) {
            nextIndex = 0; round++
            allies = allies.map(::tickEffects); enemies = enemies.map(::tickEffects)
            while (nextIndex < battle.order.size && battle.order[nextIndex] !in aliveIds) nextIndex++
        }
        val updated = battle.copy(
            allies = allies,
            enemies = enemies,
            orderIndex = nextIndex,
            round = round,
            log = (battle.log + messages).takeLast(30),
            motionUnitId = actorId,
            finished = victory || defeat,
            victory = victory,
        )
        _state.value = _state.value.copy(battle = updated)
        if (updated.finished) finishBattle(updated)
    }

    fun closeBattle() { _state.value = _state.value.copy(battle = null) }
    suspend fun fleeBattle() {
        val battle = _state.value.battle ?: return
        val fallenBySlot = battle.allies
            .filterNot(BattleUnit::alive)
            .associateBy { it.id.removePrefix("p").toIntOrNull() }
        if (fallenBySlot.isNotEmpty()) {
            database.withTransaction {
                val party = dao.getParty(PARTY_OWNER_ID)
                dao.saveCharacters(party.map { character ->
                    val fallen = fallenBySlot[character.slot]
                    if (fallen == null) character else character.copy(
                        currentHp = 0,
                        currentMp = fallen.mp.coerceAtLeast(0),
                        isDead = true,
                    )
                })
            }
        }
        _state.value = _state.value.copy(battle = null, message = "전투에서 도망쳐 베이스캠프로 돌아왔습니다.")
    }
    fun clearMessage() { _state.value = _state.value.copy(message = null) }
    fun clearBattleNotice() {
        _state.value.battle?.let { _state.value = _state.value.copy(battle = it.copy(notice = null)) }
    }
    suspend fun deleteAccount(userId: String) {
        database.withTransaction {
            dao.deleteCharacterForAccount(PARTY_OWNER_ID, userId)
            val remaining = dao.getParty(PARTY_OWNER_ID).mapIndexed { index, character ->
                character.copy(slot = index, avatarIndex = if (character.jobType() == RpgJob.NOVICE) index else character.avatarIndex)
            }
            dao.deleteParty(PARTY_OWNER_ID)
            if (remaining.isNotEmpty()) dao.saveCharacters(remaining)
        }
    }

    private suspend fun finishBattle(battle: RpgBattleState) {
        val party = dao.getParty(PARTY_OWNER_ID)
        if (battle.victory) {
            val activeUserId = _state.value.userId
            val activeUser = userDao.getUser(activeUserId)
            val heroTitleUnlockedNow = battle.stage == 9 && activeUser?.heroTitleUnlocked == false
            if (heroTitleUnlockedNow) userDao.unlockHeroTitle(activeUserId)
            val rewardExp = rpgStages.firstOrNull { it.number == battle.stage }?.clearExperience ?: 0
            val battleAllies = battle.allies.associateBy { it.id.removePrefix("p").toIntOrNull() }
            dao.saveCharacters(party.map { character ->
                val unit = battleAllies[character.slot]
                if (unit?.alive == true) {
                    addExperience(
                        character.copy(currentHp = character.combatMaxHp(), currentMp = character.combatMaxMp()),
                        rewardExp,
                    )
                } else {
                    character.copy(currentHp = 0, currentMp = unit?.mp?.coerceAtLeast(0) ?: 0, isDead = true)
                }
            })
            val current = dao.getProgress(PARTY_OWNER_ID) ?: RpgProgressEntity(PARTY_OWNER_ID)
            dao.saveProgress(current.copy(
                unlockedStage = max(current.unlockedStage, (battle.stage + 1).coerceAtMost(9)),
                highestClearedStage = max(current.highestClearedStage, battle.stage),
                battlesWon = current.battlesWon + 1,
            ))
            _state.value = _state.value.copy(
                message = if (heroTitleUnlockedNow) {
                    "마계의 최종 보스를 물리쳤습니다! 축하합니다! [용사] 칭호를 획득했습니다. 설정에서 칭호를 선택할 수 있습니다."
                } else {
                    "승리! 캐릭터마다 경험치 $rewardExp 획득"
                },
            )
        } else {
            val bySlot = battle.allies.associateBy { it.id.removePrefix("p").toIntOrNull() }
            dao.saveCharacters(party.map { character ->
                val unit = bySlot[character.slot]
                character.copy(
                    currentHp = unit?.hp?.coerceAtLeast(0) ?: 0,
                    currentMp = unit?.mp?.coerceAtLeast(0) ?: 0,
                    isDead = unit?.alive != true,
                )
            })
            val current = dao.getProgress(PARTY_OWNER_ID) ?: RpgProgressEntity(PARTY_OWNER_ID)
            dao.saveProgress(current.copy(battlesLost = current.battlesLost + 1))
            _state.value = _state.value.copy(message = "패배했습니다. 쓰러진 캐릭터는 부활소에서 되살려야 합니다.")
        }
    }

    private fun manualAllyAction(
        actor: BattleUnit,
        alliesInput: List<BattleUnit>,
        enemiesInput: List<BattleUnit>,
        action: RpgBattleAction,
        targetId: String?,
    ): Triple<List<BattleUnit>, List<BattleUnit>, String>? {
        var allies = alliesInput
        var enemies = enemiesInput
        fun updateActor(updated: BattleUnit) { allies = allies.map { if (it.id == actor.id) updated else it } }
        val enemyTarget = enemies.firstOrNull { it.id == targetId && it.alive }
            ?: enemies.firstOrNull { it.alive }
        val allyTarget = allies.firstOrNull { it.id == targetId && it.alive } ?: actor
        if (action == RpgBattleAction.REST) {
            val recoveredHp = (actor.hp + 50).coerceAtMost(actor.maxHp)
            val recoveredMp = (actor.mp + 50).coerceAtMost(actor.maxMp)
            updateActor(actor.copy(hp = recoveredHp, mp = recoveredMp))
            return Triple(
                allies,
                enemies,
                "${actor.name}의 [휴식]! 한 턴을 쉬며 HP ${recoveredHp - actor.hp}, MP ${recoveredMp - actor.mp} 회복.",
            )
        }
        if (action == RpgBattleAction.BASIC_ATTACK) {
            val target = enemyTarget ?: return null
            val primary = when (actor.job) {
                RpgJob.CAT_TAOIST -> actor.int
                RpgJob.PARROT_ROGUE -> actor.luk * 2
                else -> actor.str
            }
            val damage = (actor.level + primary) * actor.attackMultiplier
            enemies = enemies.map { if (it.id == target.id) applyDamage(it, damage) else it }
            return Triple(allies, enemies, "${actor.name}의 평타! ${target.name}에게 $damage 피해.")
        }
        when (actor.job) {
            RpgJob.NOVICE -> return null
            RpgJob.DOG_KNIGHT -> if (action == RpgBattleAction.SKILL_ONE) {
                if (actor.mp < 50 || enemyTarget == null) return null
                enemies = enemies.map { if (it.id == enemyTarget.id) it.copy(marked = 2 + actor.str / 30) else it }
                updateActor(actor.copy(mp = actor.mp - 50))
                return Triple(allies, enemies, "${actor.name}의 [표식]! ${enemyTarget.name}이 받는 피해 2배.")
            } else {
                if (actor.mp < 50) return null
                val alive = enemies.filter { it.alive }
                val count = (1 + actor.str / 50).coerceAtMost(alive.size)
                val targets = alive.shuffled().take(count).map { it.id }.toSet()
                enemies = enemies.map { if (it.id in targets && Random.nextBoolean()) it.copy(paralyzed = 2) else it }
                updateActor(actor.copy(mp = actor.mp - 50))
                return Triple(allies, enemies, "${actor.name}의 [짖기]! 50% 확률로 적 행동을 막는다.")
            }
            RpgJob.SCORPION_SOLDIER -> if (action == RpgBattleAction.SKILL_ONE) {
                if (actor.mp < 50) return null
                updateActor(actor.copy(mp = actor.mp - 50, taunting = (2 + actor.dex / 50).coerceAtMost(5)))
                return Triple(allies, enemies, "${actor.name}의 [도발]! 모든 공격을 자신에게 유도한다.")
            } else {
                if (actor.mp < 50) return null
                val alive = enemies.filter { it.alive }
                val targets = alive.shuffled().take((1 + actor.dex / 30).coerceAtMost(alive.size)).map { it.id }.toSet()
                enemies = enemies.map { if (it.id in targets) it.copy(paralyzed = 2) else it }
                updateActor(actor.copy(mp = actor.mp - 50))
                return Triple(allies, enemies, "${actor.name}의 [마비]! 적의 행동을 봉쇄한다.")
            }
            RpgJob.CAT_TAOIST -> if (action == RpgBattleAction.SKILL_ONE) {
                if (actor.mp < 50) return null
                val healTarget = allies.firstOrNull { it.id == targetId && it.alive && it.hp < it.maxHp }
                    ?: allies.filter { it.alive && it.hp < it.maxHp }.minByOrNull { it.hp.toDouble() / it.maxHp }
                    ?: return null
                allies = allies.map { if (it.id == healTarget.id) it.copy(healing = 2, hp = (it.hp + 50 + actor.int).coerceAtMost(it.maxHp)) else it }
                val latestActor = allies.first { it.id == actor.id }
                updateActor(latestActor.copy(mp = latestActor.mp - 50))
                return Triple(allies, enemies, "${actor.name}의 [회복]! ${healTarget.name}의 체력을 회복한다.")
            } else {
                if (actor.mp < 100) return null
                val multiplier = (2 + actor.int / 50).coerceAtMost(5)
                allies = allies.map { if (it.id == allyTarget.id) it.copy(attackBuff = 2, attackMultiplier = multiplier) else it }
                val latestActor = allies.first { it.id == actor.id }
                updateActor(latestActor.copy(mp = latestActor.mp - 100))
                return Triple(allies, enemies, "${actor.name}의 [버프]! ${allyTarget.name} 공격력 ${multiplier}배.")
            }
            RpgJob.PARROT_ROGUE -> if (action == RpgBattleAction.SKILL_ONE) {
                if (actor.mp < 100) return null
                val damage = actor.level + actor.luk
                enemies = enemies.map { if (it.alive) applyDamage(it, damage) else it }
                updateActor(actor.copy(mp = actor.mp - 100))
                return Triple(allies, enemies, "${actor.name}의 [필격]! 모든 적에게 $damage 피해.")
            } else {
                if (actor.charging > 0) {
                    val multiplier = (2 + actor.luk / 50).coerceAtMost(6)
                    enemies = enemies.map { if (it.alive) applyDamage(it, (actor.level + actor.luk * 2) * multiplier) else it }
                    updateActor(actor.copy(charging = 0))
                    return Triple(allies, enemies, "${actor.name}의 [필살]! ${multiplier}배 광역 피해.")
                }
                if (actor.mp < 50) return null
                updateActor(actor.copy(mp = actor.mp - 50, charging = 2))
                return Triple(allies, enemies, "${actor.name}이 [필살]을 준비한다. 다음 차례에 발동하세요.")
            }
        }
    }

    private fun allyAction(actor: BattleUnit, alliesInput: List<BattleUnit>, enemiesInput: List<BattleUnit>): Triple<List<BattleUnit>, List<BattleUnit>, String> {
        var allies = alliesInput
        var enemies = enemiesInput
        fun updateAlly(unit: BattleUnit) { allies = allies.map { if (it.id == unit.id) unit else it } }
        val aliveEnemies = enemies.filter { it.alive }
        if (aliveEnemies.isEmpty()) return Triple(allies, enemies, "")
        fun basicAttack(attacker: BattleUnit, label: String = "공격"): Triple<List<BattleUnit>, List<BattleUnit>, String> {
            val target = aliveEnemies.random()
            val primary = when (attacker.job) {
                RpgJob.CAT_TAOIST -> attacker.int
                RpgJob.PARROT_ROGUE -> attacker.luk * 2
                else -> attacker.str
            }
            val damage = (attacker.level + primary) * attacker.attackMultiplier
            updateAlly(attacker)
            enemies = enemies.map { if (it.id == target.id) applyDamage(it, damage) else it }
            return Triple(allies, enemies, "${attacker.name}의 [$label]! ${target.name}에게 $damage 피해.")
        }
        if (actor.job != RpgJob.NOVICE && actor.mp == 0 && actor.charging <= 0) {
            val recoveredHp = (actor.hp + 50).coerceAtMost(actor.maxHp)
            val recoveredMp = (actor.mp + 50).coerceAtMost(actor.maxMp)
            val basicTurns = if (actor.job == RpgJob.CAT_TAOIST) 0 else 3
            updateAlly(actor.copy(hp = recoveredHp, mp = recoveredMp, autoBasicTurns = basicTurns))
            return Triple(
                allies,
                enemies,
                "${actor.name}의 [자동 휴식]! HP ${recoveredHp - actor.hp}, MP ${recoveredMp - actor.mp} 회복.",
            )
        }
        if (actor.autoBasicTurns > 0) {
            return basicAttack(actor.copy(autoBasicTurns = actor.autoBasicTurns - 1), "휴식 후 평타")
        }
        if (actor.job != RpgJob.CAT_TAOIST && actor.charging <= 0 && Random.nextInt(100) < 45) {
            return basicAttack(actor)
        }
        when (actor.job) {
            RpgJob.DOG_KNIGHT -> if (actor.mp >= 50 && aliveEnemies.none { it.marked > 0 }) {
                val target = aliveEnemies.maxBy { it.hp }
                enemies = enemies.map { if (it.id == target.id) it.copy(marked = 2 + actor.str / 30) else it }
                updateAlly(actor.copy(mp = actor.mp - 50))
                return Triple(allies, enemies, "${actor.name}의 [표식]! ${target.name}이 받는 피해가 증가한다.")
            } else if (actor.mp >= 50) {
                val count = (1 + actor.str / 50).coerceAtMost(aliveEnemies.size)
                val targets = aliveEnemies.shuffled().take(count).map { it.id }.toSet()
                enemies = enemies.map { if (it.id in targets && Random.nextBoolean()) it.copy(paralyzed = 2) else it }
                updateAlly(actor.copy(mp = actor.mp - 50))
                return Triple(allies, enemies, "${actor.name}의 [짖기]! 적의 행동을 방해한다.")
            }
            RpgJob.SCORPION_SOLDIER -> if (actor.mp >= 50 && actor.taunting <= 0) {
                updateAlly(actor.copy(mp = actor.mp - 50, taunting = (2 + actor.dex / 50).coerceAtMost(5)))
                return Triple(allies, enemies, "${actor.name}의 [도발]! 모든 공격을 끌어당긴다.")
            } else if (actor.mp >= 50) {
                val targets = aliveEnemies.shuffled().take((1 + actor.dex / 30).coerceAtMost(aliveEnemies.size)).map { it.id }.toSet()
                enemies = enemies.map { if (it.id in targets) it.copy(paralyzed = 2) else it }
                updateAlly(actor.copy(mp = actor.mp - 50))
                return Triple(allies, enemies, "${actor.name}의 [마비]!")
            }
            RpgJob.CAT_TAOIST -> {
                val wounded = allies.filter { it.alive && it.hp < it.maxHp }.minByOrNull { it.hp.toDouble() / it.maxHp }
                if (actor.mp >= 50 && wounded != null) {
                    allies = allies.map { if (it.id == wounded.id) it.copy(healing = 2, hp = (it.hp + 50 + actor.int).coerceAtMost(it.maxHp)) else it }
                    updateAlly(allies.first { it.id == actor.id }.copy(mp = actor.mp - 50))
                    return Triple(allies, enemies, "${actor.name}의 [회복]! ${wounded.name}의 상처가 낫는다.")
                }
                if (Random.nextInt(100) < 55) return basicAttack(actor)
                if (actor.mp >= 100) {
                    val target = allies.filter { it.alive && it.id != actor.id }.maxByOrNull { it.str + it.luk } ?: actor
                    val multiplier = (2 + actor.int / 50).coerceAtMost(5)
                    allies = allies.map { if (it.id == target.id) it.copy(attackBuff = 2, attackMultiplier = multiplier) else it }
                    updateAlly(allies.first { it.id == actor.id }.copy(mp = actor.mp - 100))
                    return Triple(allies, enemies, "${actor.name}의 [버프]! ${target.name}의 공격력 ${multiplier}배.")
                }
            }
            RpgJob.PARROT_ROGUE -> if (actor.charging > 0) {
                val multiplier = (2 + actor.luk / 50).coerceAtMost(6)
                enemies = enemies.map { if (it.alive) applyDamage(it, (actor.level + actor.luk * 2) * multiplier) else it }
                updateAlly(actor.copy(charging = 0))
                return Triple(allies, enemies, "${actor.name}의 [필살]! 모든 적에게 ${multiplier}배 피해!")
            } else if (actor.mp >= 100 && aliveEnemies.size > 1) {
                val damage = actor.level + actor.luk
                enemies = enemies.map { if (it.alive) applyDamage(it, damage) else it }
                updateAlly(actor.copy(mp = actor.mp - 100))
                return Triple(allies, enemies, "${actor.name}의 [필격]! 모든 적에게 LUK 기반 피해.")
            } else if (actor.mp >= 50) {
                updateAlly(actor.copy(mp = actor.mp - 50, charging = 2))
                return Triple(allies, enemies, "${actor.name}이 [필살]을 준비하며 기회를 노린다.")
            }
            else -> Unit
        }
        val target = aliveEnemies.random()
        val primary = when (actor.job) { RpgJob.CAT_TAOIST -> actor.int; RpgJob.PARROT_ROGUE -> actor.luk * 2; else -> actor.str }
        val damage = (actor.level + primary) * actor.attackMultiplier
        enemies = enemies.map { if (it.id == target.id) applyDamage(it, damage) else it }
        return Triple(allies, enemies, "${actor.name}의 평타! ${target.name}에게 $damage 피해.")
    }

    private fun enemyAction(actor: BattleUnit, alliesInput: List<BattleUnit>, enemiesInput: List<BattleUnit>): Triple<List<BattleUnit>, List<BattleUnit>, String> {
        var allies = alliesInput
        var enemies = enemiesInput
        val role = EnemyRole.entries[actor.avatarRoleIndex()]
        val wounded = enemies.filter { it.alive && it.hp < it.maxHp / 2 }.minByOrNull { it.hp }
        if (role == EnemyRole.HEALER && actor.mp >= 50 && wounded != null) {
            enemies = enemies.map { if (it.id == wounded.id) it.copy(hp = (it.hp + actor.int * 2 + 30).coerceAtMost(it.maxHp)) else it }
            enemies = enemies.map { if (it.id == actor.id) it.copy(mp = it.mp - 50) else it }
            return Triple(allies, enemies, "${actor.name}이 ${wounded.name}을 회복시킨다.")
        }
        val taunter = allies.firstOrNull { it.alive && it.taunting > 0 }
        val target = taunter ?: allies.filter { it.alive }.random()
        val skill = actor.mp >= 50 && Random.nextInt(100) < 65
        val damage = actor.level + if (role == EnemyRole.ROGUE) actor.luk * 2 else actor.str
        allies = allies.map { if (it.id == target.id) applyDamage(it, if (skill) damage * 2 else damage) else it }
        if (skill) enemies = enemies.map { if (it.id == actor.id) it.copy(mp = it.mp - 50) else it }
        return Triple(allies, enemies, "${actor.name}${if (skill) "의 스킬" else "의 공격"}! ${target.name}에게 ${if (skill) damage * 2 else damage} 피해.")
    }

    private fun applyDamage(target: BattleUnit, raw: Int): BattleUnit {
        var damage = raw * if (target.marked > 0) 2 else 1
        val defense = target.dex.coerceAtLeast(0)
        val reduction = defense.coerceAtMost((damage * 0.7).toInt())
        damage = (damage - reduction).coerceAtLeast(1)
        return target.copy(hp = (target.hp - damage).coerceAtLeast(0))
    }

    private fun tickEffects(unit: BattleUnit): BattleUnit {
        val healed = if (unit.healing > 0 && unit.alive) (unit.hp + 50 + unit.int).coerceAtMost(unit.maxHp) else unit.hp
        return unit.copy(
            hp = healed,
            marked = (unit.marked - 1).coerceAtLeast(0),
            taunting = (unit.taunting - 1).coerceAtLeast(0),
            paralyzed = (unit.paralyzed - 1).coerceAtLeast(0),
            attackBuff = (unit.attackBuff - 1).coerceAtLeast(0),
            attackMultiplier = if (unit.attackBuff <= 1) 1 else unit.attackMultiplier,
            charging = if (unit.charging > 0) unit.charging - 1 else 0,
            healing = (unit.healing - 1).coerceAtLeast(0),
        )
    }

    private fun BattleUnit.avatarRoleIndex(): Int = (id.hashCode().ushr(1) % EnemyRole.entries.size)
    private fun addExperience(source: RpgCharacterEntity, amount: Int): RpgCharacterEntity {
        var character = source
        var exp = character.experience + amount
        var level = character.level
        var points = character.statPoints
        while (level < Int.MAX_VALUE && exp >= expNeeded(level)) {
            exp -= expNeeded(level); level++; points++
        }
        return character.copy(level = level, experience = exp, statPoints = points)
    }

    private fun RpgCharacterEntity.toBattleUnit() = BattleUnit(
        id = "p$slot", name = name, friendly = true, job = jobType(), level = level,
        str = combatStr(), dex = dexterity, int = combatInt(), luk = luck,
        maxHp = combatMaxHp(), hp = combatMaxHp(), maxMp = combatMaxMp(), mp = combatMaxMp(),
        armorEnhancement = armorEnhancement,
        weaponEnhancement = weaponEnhancement,
        weaponId = weaponId,
        armorId = armorId,
        avatarIndex = avatarIndex,
    )

    private fun RpgEnemyTemplate.toBattleUnit(stage: Int): BattleUnit {
        val scale = level + stage * 2 + if (boss) stage * 8 else 0
        val difficulty = rpgEnemyDifficultyMultiplier(stage)
        val baseStr = 4 + scale * 2
        val baseInt = 4 + scale * 2
        val baseLuk = 4 + scale
        val baseHp = 100 + scale * 35
        val roleJob = when (role) {
            EnemyRole.KNIGHT -> RpgJob.DOG_KNIGHT
            EnemyRole.TANK -> RpgJob.SCORPION_SOLDIER
            EnemyRole.HEALER -> RpgJob.CAT_TAOIST
            EnemyRole.ROGUE -> RpgJob.PARROT_ROGUE
        }
        val weaponTier = when {
            level >= 100 -> if (role == EnemyRole.HEALER) "red_orb" else "fire_stick"
            level >= 50 -> if (role == EnemyRole.HEALER) "blue_orb" else "iron_stick"
            else -> if (role == EnemyRole.HEALER) "orb" else "stick"
        }
        val armorTier = when {
            level >= 100 -> "plate"
            level >= 50 -> "heavy"
            else -> "cloth"
        }
        val enhancement = (level / 5 + if (boss) 10 else 0).coerceAtLeast(0)
        return BattleUnit(
            id = "e$id", name = name, friendly = false, job = roleJob, level = level,
            str = baseStr * difficulty, dex = 4 + scale, int = baseInt * difficulty, luk = baseLuk * difficulty,
            maxHp = baseHp * difficulty, hp = baseHp * difficulty,
            maxMp = 100 + stage * 30, mp = 100 + stage * 30,
            avatarIndex = name.hashCode().ushr(1) % 4,
            monsterIndex = (id.substringAfterLast('_').toIntOrNull() ?: 1).minus(1).coerceIn(0, 9),
            weaponId = if (role == EnemyRole.TANK || role == EnemyRole.ROGUE) "" else weaponTier,
            weaponEnhancement = if (role == EnemyRole.TANK || role == EnemyRole.ROGUE) 0 else enhancement,
            armorId = if (role == EnemyRole.ROGUE) "" else armorTier,
            armorEnhancement = if (role == EnemyRole.ROGUE) 0 else enhancement,
        )
    }

    private fun enemiesForWave(stage: Int, wave: Int): List<BattleUnit> {
        val stageTemplates = rpgEnemies.filter { it.id.startsWith("s${stage}_") }
        val main = stageTemplates.firstOrNull { it.id == "s${stage}_$wave" } ?: return emptyList()
        val enemyCount = when (wave) {
            in 1..3 -> 1
            in 4..6 -> 2
            in 7..9 -> 3
            else -> 4
        }
        val companions = stageTemplates
            .filter { !it.boss && it.id != main.id }
            .shuffled()
            .take(enemyCount - 1)
        return (listOf(main) + companions).map { it.toBattleUnit(stage) }
    }

    private fun qualifiesForDemonWorld(party: List<RpgCharacterEntity>): Boolean =
        party.all { character -> character.level >= 100 }

    companion object {
        private const val PARTY_OWNER_ID = "__RPG_SHARED_PARTY__"
        private const val REVIVE_COST = 100_000_000L
        const val EXP_BUNDLE_COST = 50_000_000L
        const val EXP_BUNDLE_SIZE = 5
    }
}

enum class EnhancementResult { Success, Failed, FailedAndDowngraded, NotEnoughMoney, Unavailable }
