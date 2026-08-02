package es.kim.story.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rpg_character", primaryKeys = ["userId", "slot"])
data class RpgCharacterEntity(
    val userId: String,
    val slot: Int,
    val name: String,
    val avatarIndex: Int,
    val job: String = "NOVICE",
    val level: Int = 1,
    val experience: Int = 0,
    val statPoints: Int = 0,
    val strength: Int = 4,
    val dexterity: Int = 4,
    val intelligence: Int = 4,
    val luck: Int = 4,
    val maxHp: Int = 100,
    val maxMp: Int = 100,
    val currentHp: Int = 100,
    val currentMp: Int = 100,
    val isDead: Boolean = false,
    val weaponId: String = "",
    val weaponEnhancement: Int = 0,
    val armorId: String = "",
    val armorEnhancement: Int = 0,
)

@Entity(tableName = "rpg_progress", primaryKeys = ["userId"])
data class RpgProgressEntity(
    val userId: String,
    val walletBalance: Long = 0L,
    val unlockedStage: Int = 1,
    val highestClearedStage: Int = 0,
    val battlesWon: Int = 0,
    val battlesLost: Int = 0,
)

@Entity(tableName = "rpg_equipment_inventory")
data class RpgEquipmentInventoryEntity(
    @PrimaryKey(autoGenerate = true) val instanceId: Long = 0,
    val ownerId: String,
    val equipmentId: String,
    val enhancement: Int = 0,
    val equippedCharacterSlot: Int? = null,
)
