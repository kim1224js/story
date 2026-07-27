package es.kim.story.data
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
@Dao interface UserDao {
    @Query("SELECT * FROM user WHERE userId = :userId") fun observeUser(userId: String): Flow<UserEntity?>
    @Query("SELECT * FROM user ORDER BY userId") fun observeAllUsers(): Flow<List<UserEntity>>
    @Query("SELECT * FROM user WHERE userId = :userId") suspend fun getUser(userId: String): UserEntity?
    @Query("SELECT COUNT(*) FROM user") suspend fun userCount(): Int
    @Upsert suspend fun save(user: UserEntity)
    @Query("DELETE FROM user WHERE userId = :userId")
    suspend fun deleteUser(userId: String): Int
    @Query("UPDATE user SET money = money + :amount WHERE userId = :userId")
    suspend fun addMoney(userId: String, amount: Long)
    @Query("UPDATE user SET gender = :gender WHERE userId = :userId")
    suspend fun updateGender(userId: String, gender: String)
    @Query("UPDATE user SET money = :money WHERE userId = :userId")
    suspend fun setMoney(userId: String, money: Long)
    @Query("UPDATE user SET chapter = :chapter WHERE userId = :userId")
    suspend fun setChapter(userId: String, chapter: Int)
    @Query(
        "UPDATE user SET seotdaName1 = :name1, seotdaName2 = :name2, seotdaName3 = :name3 " +
            "WHERE userId = :userId",
    )
    suspend fun updateSeotdaNames(userId: String, name1: String, name2: String, name3: String)
    @Query("UPDATE user SET money = money - :amount WHERE userId = :userId AND money >= :amount")
    suspend fun spendMoney(userId: String, amount: Long): Int
    @Query("UPDATE user SET blueChips = blueChips + :amount WHERE userId = :userId")
    suspend fun addBlueChips(userId: String, amount: Long)
    @Query("UPDATE user SET blueChips = blueChips - :amount WHERE userId = :userId AND blueChips >= :amount")
    suspend fun spendBlueChips(userId: String, amount: Long): Int
    @Query(
        "UPDATE user SET money = money - :moneyCost, blueChips = blueChips + 1 " +
            "WHERE userId = :userId AND money >= :moneyCost",
    )
    suspend fun exchangeBlueChip(userId: String, moneyCost: Long): Int
    @Query(
        "UPDATE user SET blueChips = blueChips - :chipCost, premiumIdColor = 1 " +
            "WHERE userId = :userId AND blueChips >= :chipCost AND premiumIdColor = 0",
    )
    suspend fun buyPremiumIdColor(userId: String, chipCost: Long): Int
    @Query(
        "UPDATE user SET money = money - :cost, chapter = chapter + 1 " +
            "WHERE userId = :userId AND chapter = :chapter AND money >= :cost",
    )
    suspend fun clearStoryChapter(userId: String, chapter: Int, cost: Long): Int

    @Transaction
    suspend fun settleGamble(userId: String, wager: Long, payout: Long): Boolean {
        if (spendMoney(userId, wager) != 1) return false
        if (payout > 0) addMoney(userId, payout)
        return true
    }
    @Transaction
    suspend fun settleBlueChipGamble(userId: String, wager: Long, payout: Long): Boolean {
        if (spendBlueChips(userId, wager) != 1) return false
        if (payout > 0) addBlueChips(userId, payout)
        return true
    }
}
