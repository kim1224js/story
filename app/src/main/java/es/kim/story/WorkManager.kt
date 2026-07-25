package es.kim.story

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

const val MOLE_GAME_RESET_COST = 1_000_000L

data class PartTimeJob(val id: String, val title: String, val durationMillis: Long, val durationLabel: String, val reward: Long, val oncePerDay: Boolean = false)
data class ActiveJob(val jobId: String, val startedAt: Long)
data class WorkState(
    val activeJob: ActiveJob? = null,
    val balance: Long = 0,
    val lastWalkRewardDate: String? = null,
    val molePlayDate: String? = null,
    val molePlaysToday: Int = 0,
)

val partTimeJobs = listOf(
    PartTimeJob("cafe_4", "영자네 카페 알바", 4 * 60 * 60 * 1_000L, "4시간", 100_000),
    PartTimeJob("cafe_8", "영자네 카페 알바", 8 * 60 * 60 * 1_000L, "8시간", 150_000),
    PartTimeJob("cafe_12", "영자네 카페 알바", 12 * 60 * 60 * 1_000L, "12시간", 200_000),
    PartTimeJob("walk_kkami", "까미 산책하기", 30 * 60 * 1_000L, "30분 · 하루 한 번", 200_000, true),
)

@Singleton
class WorkManager @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("part_time_jobs", Context.MODE_PRIVATE)
    private var currentAccount = ""
    private val _state = MutableStateFlow(WorkState())
    val state = _state.asStateFlow()

    fun selectAccount(userId: String) {
        if (currentAccount == userId) return
        currentAccount = userId
        _state.value = load()
    }

    fun start(job: PartTimeJob): Boolean {
        val current = _state.value
        if (current.activeJob != null) return false
        if (job.oncePerDay && current.lastWalkRewardDate == LocalDate.now().toString()) return false
        return update(current.copy(activeJob = ActiveJob(job.id, System.currentTimeMillis())))
    }

    fun cancel() = update(_state.value.copy(activeJob = null))

    fun startMoleGame(): Boolean {
        val today = LocalDate.now().toString()
        val current = _state.value
        val playsToday = if (current.molePlayDate == today) current.molePlaysToday else 0
        if (playsToday >= 5) return false
        return update(
            current.copy(
                molePlayDate = today,
                molePlaysToday = playsToday + 1,
            ),
        )
    }

    fun canResetMoleGame() = _state.value.molePlaysToday > 0

    fun resetMoleGame(): Boolean {
        if (!canResetMoleGame()) return false
        return update(
            _state.value.copy(
                molePlayDate = LocalDate.now().toString(),
                molePlaysToday = 0,
            ),
        )
    }

    fun deleteAccountData(userId: String) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("$userId|") }.forEach(editor::remove)
        editor.apply()
    }

    fun claim(job: PartTimeJob): Boolean {
        val current = _state.value
        val active = current.activeJob ?: return false
        if (active.jobId != job.id || System.currentTimeMillis() < active.startedAt + job.durationMillis) return false
        return update(current.copy(
            activeJob = null,
            balance = current.balance + job.reward,
            lastWalkRewardDate = if (job.oncePerDay) LocalDate.now().toString() else current.lastWalkRewardDate,
        ))
    }

    private fun load(): WorkState {
        val today = LocalDate.now().toString()
        val savedMoleDate = prefs.getString(key("mole_play_date"), null)
        return WorkState(
            activeJob = prefs.getString(key("active_job_id"), null)?.let {
                ActiveJob(it, prefs.getLong(key("active_job_started_at"), 0L))
            },
            lastWalkRewardDate = prefs.getString(key("last_walk_reward_date"), null),
            molePlayDate = savedMoleDate,
            molePlaysToday = if (savedMoleDate == today) prefs.getInt(key("mole_plays_today"), 0) else 0,
        )
    }

    private fun key(name: String) = "$currentAccount|$name"

    private fun update(value: WorkState): Boolean {
        _state.value = value
        prefs.edit().putString(key("active_job_id"), value.activeJob?.jobId)
            .putLong(key("active_job_started_at"), value.activeJob?.startedAt ?: 0L)
            .putString(key("last_walk_reward_date"), value.lastWalkRewardDate)
            .putString(key("mole_play_date"), value.molePlayDate)
            .putInt(key("mole_plays_today"), value.molePlaysToday)
            .apply()
        return true
    }
}
