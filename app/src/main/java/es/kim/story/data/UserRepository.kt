package es.kim.story.data
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import es.kim.story.MAX_PLAYER_MONEY
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton
@Singleton class UserRepository @Inject constructor(
    private val dao: UserDao,
    @param:ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("active_account", Context.MODE_PRIVATE)
    private val activeUserId = MutableStateFlow(prefs.getString("user_id", "").orEmpty())
    val user = activeUserId.flatMapLatest { id -> if (id.isBlank()) flowOf(null) else dao.observeUser(id) }
    val accounts = dao.observeAllUsers()

    suspend fun saveUserId(id: String): Boolean {
        val userId = id.trim()
        if (userId.isBlank()) return false
        if (dao.getUser(userId) == null) {
            if (dao.userCount() >= MAX_ACCOUNTS) return false
            dao.save(UserEntity(userId = userId))
        }
        switchAccount(userId)
        return true
    }
    suspend fun switchAccount(userId: String) {
        dao.convertMoneyOverflow(userId)
        prefs.edit().putString("user_id", userId).apply()
        activeUserId.value = userId
    }
    suspend fun addMoney(amount: Long) {
        if (amount > 0) dao.addMoney(activeUserId.value, amount)
    }
    suspend fun addMoney(userId: String, amount: Long) {
        if (amount > 0) dao.addMoney(userId, amount)
    }
    suspend fun spendMoney(amount: Long): Boolean =
        amount > 0 && dao.spendMoney(activeUserId.value, amount) == 1
    suspend fun updateSeotdaNames(names: List<String>): Boolean {
        if (names.size != 3 || names.any { it.isBlank() }) return false
        dao.updateSeotdaNames(
            activeUserId.value,
            names[0].trim(),
            names[1].trim(),
            names[2].trim(),
        )
        return true
    }
    suspend fun deleteAccount(userId: String): Boolean {
        if (userId == activeUserId.value) return false
        return dao.deleteUser(userId) == 1
    }
    suspend fun updateGender(gender: String) = dao.updateGender(activeUserId.value, gender)
    suspend fun settleGamble(wager: Long, payout: Long) =
        dao.settleGamble(activeUserId.value, wager, payout)
    suspend fun settleBlueChipGamble(wager: Long, payout: Long) =
        dao.settleBlueChipGamble(activeUserId.value, wager, payout)
    suspend fun addBlueChips(amount: Long) = dao.addBlueChips(activeUserId.value, amount)
    suspend fun exchangeBlueChip(): Boolean =
        dao.exchangeBlueChip(activeUserId.value, BLUE_CHIP_EXCHANGE_COST) == 1
    suspend fun sellBlueChip(): Boolean =
        dao.sellBlueChip(
            activeUserId.value,
            BLUE_CHIP_SELL_VALUE,
            MAX_PLAYER_MONEY - BLUE_CHIP_SELL_VALUE,
        ) == 1
    suspend fun buyPremiumIdColor(): Boolean =
        dao.buyPremiumIdColor(activeUserId.value, PREMIUM_ID_COLOR_COST) == 1
    suspend fun clearStoryChapter(chapter: Int, cost: Long): Boolean =
        dao.clearStoryChapter(activeUserId.value, chapter, cost) == 1
    companion object {
        const val MAX_ACCOUNTS = 3
        const val BLUE_CHIP_EXCHANGE_COST = 100_000_000L
        const val BLUE_CHIP_SELL_VALUE = 95_000_000L
        const val PREMIUM_ID_COLOR_COST = 100L
    }
}
