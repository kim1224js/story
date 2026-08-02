package es.kim.story.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RpgDao {
    @Query("SELECT * FROM rpg_equipment_inventory WHERE ownerId = :ownerId ORDER BY instanceId")
    fun observeInventory(ownerId: String): Flow<List<RpgEquipmentInventoryEntity>>

    @Query("SELECT * FROM rpg_equipment_inventory WHERE ownerId = :ownerId ORDER BY instanceId")
    suspend fun getInventory(ownerId: String): List<RpgEquipmentInventoryEntity>

    @Upsert suspend fun saveInventoryItem(item: RpgEquipmentInventoryEntity): Long

    @Query("SELECT * FROM rpg_character WHERE userId = :userId ORDER BY slot")
    fun observeParty(userId: String): Flow<List<RpgCharacterEntity>>

    @Query("SELECT * FROM rpg_character WHERE userId = :userId ORDER BY slot")
    suspend fun getParty(userId: String): List<RpgCharacterEntity>

    @Query("SELECT * FROM rpg_progress WHERE userId = :userId")
    fun observeProgress(userId: String): Flow<RpgProgressEntity?>

    @Query("SELECT * FROM rpg_progress WHERE userId = :userId")
    suspend fun getProgress(userId: String): RpgProgressEntity?

    @Upsert suspend fun saveCharacter(character: RpgCharacterEntity)
    @Upsert suspend fun saveCharacters(characters: List<RpgCharacterEntity>)
    @Upsert suspend fun saveProgress(progress: RpgProgressEntity)

    @Query("DELETE FROM rpg_character WHERE userId = :userId")
    suspend fun deleteParty(userId: String)

    @Query("DELETE FROM rpg_character WHERE userId != :ownerId")
    suspend fun deleteLegacyParties(ownerId: String)

    @Query("DELETE FROM rpg_character WHERE userId = :ownerId AND slot >= :partySize")
    suspend fun trimParty(ownerId: String, partySize: Int)

    @Query("DELETE FROM rpg_character WHERE userId = :ownerId AND name = :accountName")
    suspend fun deleteCharacterForAccount(ownerId: String, accountName: String)

    @Query("DELETE FROM rpg_progress WHERE userId = :userId")
    suspend fun deleteProgress(userId: String)
}
