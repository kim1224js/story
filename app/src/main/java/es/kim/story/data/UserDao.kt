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
    @Query("SELECT * FROM user ORDER BY userId") suspend fun getAllUsers(): List<UserEntity>
    @Query("SELECT COUNT(*) FROM user") suspend fun userCount(): Int
    @Upsert suspend fun save(user: UserEntity)
    @Query("DELETE FROM user WHERE userId = :userId")
    suspend fun deleteUser(userId: String): Int
    @Transaction
    suspend fun addMoney(userId: String, amount: Long) {
        if (amount <= 0L) return
        val current = getUser(userId) ?: return
        val total = if (amount > Long.MAX_VALUE - current.money) Long.MAX_VALUE else current.money + amount
        setMoney(userId, total)
    }
    @Query("UPDATE user SET gender = :gender WHERE userId = :userId")
    suspend fun updateGender(userId: String, gender: String)
    @Query("UPDATE user SET money = :money WHERE userId = :userId")
    suspend fun setMoney(userId: String, money: Long)
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
    @Query("UPDATE user SET ownedApartmentDistricts = :districts WHERE userId = :userId")
    suspend fun setOwnedApartmentDistricts(userId: String, districts: String)
    @Query("UPDATE user SET apartmentRentLastClaimAt = :claimedAt WHERE userId = :userId")
    suspend fun setApartmentRentLastClaimAt(userId: String, claimedAt: Long)
    @Query("UPDATE user SET selectedTitle = :title WHERE userId = :userId")
    suspend fun setSelectedTitle(userId: String, title: String)
    @Query(
        "UPDATE user SET heroTitleUnlocked = 1, " +
            "selectedTitle = CASE WHEN selectedTitle = '' THEN 'hero' ELSE selectedTitle END " +
            "WHERE userId = :userId",
    )
    suspend fun unlockHeroTitle(userId: String): Int

    @Transaction
    suspend fun purchaseApartment(
        userId: String,
        district: String,
        cost: Long,
        blueChipExchangeCost: Long,
        requiredOwnedCount: Int,
    ): Boolean {
        val current = getUser(userId) ?: return false
        val owned = current.ownedApartmentDistricts.split(',').filter(String::isNotBlank)
        val districtCount = owned.count { it == district }
        val distinctOwnedCount = owned.toSet().size
        if (districtCount >= 50 || distinctOwnedCount < requiredOwnedCount ||
            cost <= 0L || blueChipExchangeCost <= 0L
        ) return false
        val missingBlueChips = (cost - current.blueChips).coerceAtLeast(0L)
        if (missingBlueChips > 0L) {
            if (missingBlueChips > Long.MAX_VALUE / blueChipExchangeCost) return false
            val exchangeMoneyCost = missingBlueChips * blueChipExchangeCost
            if (spendMoney(userId, exchangeMoneyCost) != 1) return false
            addBlueChips(userId, missingBlueChips)
        }
        if (spendBlueChips(userId, cost) != 1) return false
        setOwnedApartmentDistricts(userId, (owned + district).joinToString(","))
        if ((owned + district).toSet().size >= 25 && current.selectedTitle.isBlank()) {
            setSelectedTitle(userId, "real_estate_master")
        }
        if (current.apartmentRentLastClaimAt <= 0L) {
            setApartmentRentLastClaimAt(userId, System.currentTimeMillis())
        }
        return true
    }

    @Transaction
    suspend fun claimApartmentRent(
        userId: String,
        hourlyRent: Long,
        now: Long,
    ): ApartmentRentPayment? {
        if (hourlyRent <= 0L || now <= 0L) return null
        val current = getUser(userId) ?: return null
        val lastClaimAt = current.apartmentRentLastClaimAt
        if (lastClaimAt <= 0L || now < lastClaimAt) {
            setApartmentRentLastClaimAt(userId, now)
            return null
        }
        val elapsedHours = (now - lastClaimAt) / APARTMENT_RENT_HOUR_MILLIS
        if (elapsedHours < 1L) return null
        val amount = if (elapsedHours > Long.MAX_VALUE / hourlyRent) {
            Long.MAX_VALUE
        } else {
            hourlyRent * elapsedHours
        }
        val claimedAt = lastClaimAt + elapsedHours * APARTMENT_RENT_HOUR_MILLIS
        setApartmentRentLastClaimAt(userId, claimedAt)
        addMoney(userId, amount)
        return ApartmentRentPayment(elapsedHours, hourlyRent, amount)
    }
    @Query(
        "UPDATE user SET money = money - :moneyCost, blueChips = blueChips + 1 " +
            "WHERE userId = :userId AND money >= :moneyCost",
    )
    suspend fun exchangeBlueChip(userId: String, moneyCost: Long): Int
    @Query(
        "UPDATE user SET blueChips = blueChips - 1, money = money + :moneyValue " +
            "WHERE userId = :userId AND blueChips >= 1 AND money <= :maxMoneyBeforeExchange",
    )
    suspend fun sellBlueChip(userId: String, moneyValue: Long, maxMoneyBeforeExchange: Long): Int
    @Query(
        "UPDATE user SET blueChips = blueChips - :chipCost, premiumIdColor = 1, " +
            "selectedTitle = CASE WHEN selectedTitle = '' THEN 'game_master' ELSE selectedTitle END " +
            "WHERE userId = :userId AND blueChips >= :chipCost AND premiumIdColor = 0",
    )
    suspend fun buyPremiumIdColor(userId: String, chipCost: Long): Int

    @Transaction
    suspend fun selectPlayerTitle(userId: String, title: String): Boolean {
        val current = getUser(userId) ?: return false
        val apartmentCount = current.ownedApartmentDistricts.split(',').filter(String::isNotBlank).toSet().size
        val allowed = title.isBlank() ||
            (title == "game_master" && current.premiumIdColor) ||
            (title == "real_estate_master" && apartmentCount >= 25) ||
            (title == "hero" && current.heroTitleUnlocked)
        if (!allowed) return false
        setSelectedTitle(userId, title)
        return true
    }
    @Query(
        "UPDATE user SET money = money - :cost, chapter = chapter + 1 " +
            "WHERE userId = :userId AND chapter = :chapter AND money >= :cost",
    )
    suspend fun clearStoryChapter(userId: String, chapter: Int, cost: Long): Int

    @Query("""
        SELECT (money + (blueChips * 10000) + (chapter * 1000000)) 
        FROM user 
        LIMIT 1
    """)
    suspend fun getTotalScore(): Long?

    @Query("SELECT * FROM user LIMIT 1")
    suspend fun getUser(): UserEntity?

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

    companion object {
        private const val APARTMENT_RENT_HOUR_MILLIS = 60L * 60L * 1_000L
    }
}

data class ApartmentRentPayment(
    val elapsedHours: Long,
    val hourlyRent: Long,
    val amount: Long,
)
