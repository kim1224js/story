package es.kim.story

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import es.kim.story.data.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

enum class QuestPeriod { Daily, Weekly }

data class StepQuest(
    val id: String,
    val period: QuestPeriod,
    val target: Long,
    val rewardPercent: Double,
)

data class StepQuestState(
    val dailySteps: Long = 0,
    val weeklySteps: Long = 0,
    val claimedKeys: Set<String> = emptySet(),
    val loading: Boolean = false,
)

val stepQuests = listOf(
    StepQuest("daily_1000", QuestPeriod.Daily, 1_000, 3.0),
    StepQuest("daily_5000", QuestPeriod.Daily, 5_000, 9.0),
    StepQuest("daily_10000", QuestPeriod.Daily, 10_000, 18.0),
    StepQuest("weekly_10000", QuestPeriod.Weekly, 10_000, 15.0),
    StepQuest("weekly_30000", QuestPeriod.Weekly, 30_000, 30.0),
    StepQuest("weekly_50000", QuestPeriod.Weekly, 50_000, 45.0),
)

@Singleton
class StepQuestManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: UserRepository,
) {
    private val prefs = context.getSharedPreferences("step_quests", Context.MODE_PRIVATE)
    private var currentAccount = ""
    private val _state = MutableStateFlow(StepQuestState())
    val state = _state.asStateFlow()

    fun selectAccount(userId: String) {
        if (currentAccount == userId) return
        currentAccount = userId
        _state.value = StepQuestState(claimedKeys = prefs.all.keys.filter { it.startsWith("$userId|") }.toSet())
    }

    suspend fun refresh() {
        _state.value = _state.value.copy(loading = true)
        try {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val now = Instant.now()
            val client = HealthConnectClient.getOrCreate(context)
            val daily = aggregateSteps(client, today.atStartOfDay(zone).toInstant(), now)
            val weekly = aggregateSteps(client, weekStart.atStartOfDay(zone).toInstant(), now)
            _state.value = _state.value.copy(dailySteps = daily, weeklySteps = weekly)
        } finally {
            _state.value = _state.value.copy(loading = false)
        }
    }

    suspend fun claim(quest: StepQuest, rewardAmount: Long): Boolean {
        val current = _state.value
        val steps = if (quest.period == QuestPeriod.Daily) current.dailySteps else current.weeklySteps
        val key = claimKey(quest)
        if (steps < quest.target || key in current.claimedKeys) return false
        repository.addMoney(rewardAmount)
        prefs.edit().putBoolean(key, true).apply()
        _state.value = current.copy(claimedKeys = current.claimedKeys + key)
        return true
    }

    fun isClaimed(quest: StepQuest): Boolean = claimKey(quest) in _state.value.claimedKeys

    fun deleteAccountData(userId: String) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("$userId|") }.forEach(editor::remove)
        editor.apply()
    }

    private fun claimKey(quest: StepQuest): String {
        val today = LocalDate.now()
        val periodKey = if (quest.period == QuestPeriod.Daily) today.toString()
        else today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
        return "$currentAccount|${quest.id}_$periodKey"
    }

    private suspend fun aggregateSteps(client: HealthConnectClient, start: Instant, end: Instant): Long =
        client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end),
            ),
        )[StepsRecord.COUNT_TOTAL] ?: 0L
}
