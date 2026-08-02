package es.kim.story

import es.kim.story.data.RpgCharacterEntity

enum class RpgJob(val label: String, val primary: String, val avatarIndex: Int) {
    NOVICE("초보자", "-", 0),
    DOG_KNIGHT("멍멍이기사", "STR", 0),
    SCORPION_SOLDIER("전갈보병", "DEX", 1),
    CAT_TAOIST("고양이도사", "INT", 2),
    PARROT_ROGUE("앵무도적", "LUK", 3),
}

enum class RpgStat(val label: String) { STR("STR"), DEX("DEX"), INT("INT"), LUK("LUK"), HP("체력"), MP("마력") }
enum class EquipmentSlot { WEAPON, ARMOR }

internal const val JOB_CHANGE_LEVEL = 30
internal const val JOB_CHANGE_STAT_POINTS = 29
private const val BASE_CHARACTER_STAT = 4

internal fun RpgCharacterEntity.resetForJobChange(job: RpgJob): RpgCharacterEntity {
    require(job != RpgJob.NOVICE)
    return copy(
        job = job.name,
        avatarIndex = job.avatarIndex,
        level = JOB_CHANGE_LEVEL,
        experience = 0,
        statPoints = JOB_CHANGE_STAT_POINTS,
        strength = BASE_CHARACTER_STAT,
        dexterity = BASE_CHARACTER_STAT,
        intelligence = BASE_CHARACTER_STAT,
        luck = BASE_CHARACTER_STAT,
        maxHp = 100,
        maxMp = 100,
        currentHp = 100,
        currentMp = 100,
        isDead = false,
        weaponId = "",
        weaponEnhancement = 0,
        armorId = "",
        armorEnhancement = 0,
    )
}

data class RpgEquipment(
    val id: String,
    val name: String,
    val slot: EquipmentSlot,
    val requiredLevel: Int,
    val price: Long,
    val enhancementCost: Long,
    val stat: RpgStat,
    val baseValue: Int,
    val valuePerEnhancement: Int,
)

val rpgEquipment = listOf(
    RpgEquipment("stick", "막대기", EquipmentSlot.WEAPON, 1, 50_000_000_000L, 10_000_000_000L, RpgStat.STR, 5, 1),
    RpgEquipment("iron_stick", "쇠막대기", EquipmentSlot.WEAPON, 50, 1_000_000_000_000L, 1_000_000_000_000L, RpgStat.STR, 10, 2),
    RpgEquipment("fire_stick", "불막대기", EquipmentSlot.WEAPON, 100, 10_000_000_000_000L, 10_000_000_000_000L, RpgStat.STR, 50, 3),
    RpgEquipment("orb", "오브", EquipmentSlot.WEAPON, 1, 50_000_000_000L, 10_000_000_000L, RpgStat.INT, 5, 1),
    RpgEquipment("blue_orb", "푸른오브", EquipmentSlot.WEAPON, 50, 1_000_000_000_000L, 1_000_000_000_000L, RpgStat.INT, 10, 2),
    RpgEquipment("red_orb", "붉은오브", EquipmentSlot.WEAPON, 100, 10_000_000_000_000L, 10_000_000_000_000L, RpgStat.INT, 50, 3),
    RpgEquipment("cloth", "천갑옷", EquipmentSlot.ARMOR, 1, 50_000_000_000L, 10_000_000_000L, RpgStat.HP, 50, 10),
    RpgEquipment("heavy", "중갑옷", EquipmentSlot.ARMOR, 50, 1_000_000_000_000L, 1_000_000_000_000L, RpgStat.HP, 100, 20),
    RpgEquipment("plate", "판금갑옷", EquipmentSlot.ARMOR, 100, 10_000_000_000_000L, 10_000_000_000_000L, RpgStat.HP, 500, 30),
).map { equipment ->
    val reducedPrice = equipment.price / 1_000L
    equipment.copy(
        price = reducedPrice,
        enhancementCost = reducedPrice / 10L,
    )
}

data class RpgStage(
    val number: Int,
    val place: String,
    val minLevel: Int,
    val maxLevel: Int,
    val description: String,
    val clearExperience: Int,
)

val rpgStages = listOf(
    RpgStage(1, "서울", 1, 10, "골목의 초보 모험", 5),
    RpgStage(2, "성남", 11, 20, "기계 야수의 도시", 10),
    RpgStage(3, "안산", 21, 30, "독안개 공단", 15),
    RpgStage(4, "천안", 31, 40, "결투가의 관문", 20),
    RpgStage(5, "광주", 41, 50, "빛과 그림자의 숲", 25),
    RpgStage(6, "부산", 51, 60, "폭풍 항구", 30),
    RpgStage(7, "제주", 61, 75, "화산 정령의 섬", 35),
    RpgStage(8, "북한", 76, 99, "빙결 요새", 40),
    RpgStage(9, "마계", 1000, 1000, "", 45),
)

internal fun rpgEnemyDifficultyMultiplier(stage: Int): Int = if (stage == 9) 10 else 1

enum class EnemyRole { KNIGHT, TANK, HEALER, ROGUE }
enum class RpgBattleAction { BASIC_ATTACK, SKILL_ONE, SKILL_TWO, REST }
data class RpgEnemyTemplate(val id: String, val name: String, val level: Int, val role: EnemyRole, val boss: Boolean)

private val monsterNames = listOf(
    "골목 쓰레기통 슬라임", "도적 쥐", "갑옷 투구벌레", "독버섯 정령", "바위 고블린",
    "망령 등불", "빙결 늑대", "용암 임프", "암흑 해골기사", "마룡 군주",
)

val rpgEnemies: List<RpgEnemyTemplate> = rpgStages.flatMap { stage ->
    (1..10).map { index ->
        val level = if (stage.number == 9) 100 else
            stage.minLevel + ((stage.maxLevel - stage.minLevel) * (index - 1) / 9)
        RpgEnemyTemplate(
            id = "s${stage.number}_$index",
            name = "${stage.place} ${monsterNames[index - 1]}",
            level = level,
            role = EnemyRole.entries[(stage.number + index) % EnemyRole.entries.size],
            boss = index == 10,
        )
    }
}

data class BattleUnit(
    val id: String,
    val name: String,
    val friendly: Boolean,
    val job: RpgJob,
    val level: Int,
    val str: Int,
    val dex: Int,
    val int: Int,
    val luk: Int,
    val maxHp: Int,
    val hp: Int,
    val maxMp: Int,
    val mp: Int,
    val marked: Int = 0,
    val taunting: Int = 0,
    val paralyzed: Int = 0,
    val attackBuff: Int = 0,
    val attackMultiplier: Int = 1,
    val charging: Int = 0,
    val healing: Int = 0,
    val armorEnhancement: Int = 0,
    val weaponEnhancement: Int = 0,
    val weaponId: String = "",
    val armorId: String = "",
    val avatarIndex: Int = 0,
    val monsterIndex: Int = -1,
    val autoBasicTurns: Int = 0,
) {
    val alive get() = hp > 0
}

data class RpgBattleState(
    val stage: Int,
    val wave: Int,
    val allies: List<BattleUnit>,
    val enemies: List<BattleUnit>,
    val order: List<String>,
    val orderIndex: Int = 0,
    val round: Int = 1,
    val log: List<String> = listOf("전투 시작!"),
    val motionUnitId: String? = null,
    val finished: Boolean = false,
    val victory: Boolean = false,
    val notice: String? = null,
)

data class RpgUiState(
    val userId: String = "",
    val party: List<RpgCharacterEntity> = emptyList(),
    val inventory: List<es.kim.story.data.RpgEquipmentInventoryEntity> = emptyList(),
    val unlockedStage: Int = 1,
    val highestClearedStage: Int = 0,
    val walletBalance: Long = 0L,
    val battle: RpgBattleState? = null,
    val message: String? = null,
)

fun expNeeded(level: Int): Int = level.coerceAtLeast(1).coerceAtMost(Int.MAX_VALUE / 5) * 5

fun RpgCharacterEntity.jobType(): RpgJob = runCatching { RpgJob.valueOf(job) }.getOrDefault(RpgJob.NOVICE)
fun RpgCharacterEntity.equipment(id: String): RpgEquipment? = rpgEquipment.firstOrNull { it.id == id }

fun RpgCharacterEntity.combatMaxHp(): Int {
    val armor = equipment(armorId)
    val armorHp = if (armor?.stat == RpgStat.HP) armor.baseValue + armorEnhancement * armor.valuePerEnhancement else 0
    val efficiency = if (jobType() == RpgJob.SCORPION_SOLDIER) 2 else 1
    return maxHp + armorHp * efficiency
}

fun RpgCharacterEntity.combatMaxMp(): Int {
    val armor = equipment(armorId)
    val armorMp = if (armor?.slot == EquipmentSlot.ARMOR) armor.baseValue + armorEnhancement * armor.valuePerEnhancement else 0
    val efficiency = if (jobType() == RpgJob.SCORPION_SOLDIER) 2 else 1
    return maxMp + armorMp * efficiency
}

fun RpgCharacterEntity.combatStr(): Int {
    val weapon = equipment(weaponId)
    return strength + if (weapon?.stat == RpgStat.STR) weapon.baseValue + weaponEnhancement * weapon.valuePerEnhancement else 0
}

fun RpgCharacterEntity.combatInt(): Int {
    val weapon = equipment(weaponId)
    return intelligence + if (weapon?.stat == RpgStat.INT) weapon.baseValue + weaponEnhancement * weapon.valuePerEnhancement else 0
}

fun RpgCharacterEntity.combatAttackPower(): Int {
    val primary = when (jobType()) {
        RpgJob.CAT_TAOIST -> combatInt()
        RpgJob.PARROT_ROGUE -> luck * 2
        else -> combatStr()
    }
    return level + primary
}

fun RpgCharacterEntity.combatDefense(): Int = dexterity.coerceAtLeast(0)
