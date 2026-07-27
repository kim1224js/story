package es.kim.story

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class PartTimeJob(val id: String, val title: String, val durationMillis: Long, val durationLabel: String, val reward: Long, val oncePerDay: Boolean = false)
data class ActiveJob(val jobId: String, val startedAt: Long)
data class WorkState(
    val activeJob: ActiveJob? = null,
    val balance: Long = 0,
    val lastWalkRewardDate: String? = null,
    val molePlayDate: String? = null,
    val molePlaysToday: Int = 0,
    val icePlayDate: String? = null,
    val icePlaysToday: Int = 0,
    val mazeSeed: Long = 0L,
    val mazeX: Int = 0,
    val mazeY: Int = 0,
    val mazeMoveDate: String? = null,
    val mazeMovesToday: Int = 0,
    val mazeBonusMovesToday: Int = 0,
    val mazeCollectedItems: Set<Int> = emptySet(),
    val mazeCompleted: Boolean = false,
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
        if (playsToday >= 10) return false
        return update(
            current.copy(
                molePlayDate = today,
                molePlaysToday = playsToday + 1,
            ),
        )
    }

    fun startIceGame(): Boolean {
        val today = LocalDate.now().toString()
        val current = _state.value
        val playsToday = if (current.icePlayDate == today) current.icePlaysToday else 0
        if (playsToday >= 5) return false
        return update(
            current.copy(
                icePlayDate = today,
                icePlaysToday = playsToday + 1,
            ),
        )
    }

    fun ensureMaze() {
        if (_state.value.mazeSeed != 0L) return
        update(_state.value.copy(mazeSeed = System.currentTimeMillis()))
    }

    fun moveMaze(targetX: Int, targetY: Int, itemId: Int?): Boolean {
        val today = LocalDate.now().toString()
        val current = _state.value
        if (current.mazeSeed == 0L || current.mazeCompleted) return false
        val movesToday = if (current.mazeMoveDate == today) current.mazeMovesToday else 0
        val bonusToday = if (current.mazeMoveDate == today) current.mazeBonusMovesToday else 0
        val dailyLimit = 50 + bonusToday
        if (movesToday >= dailyLimit) return false
        val isNewItem = itemId != null && itemId !in current.mazeCollectedItems
        val collected = if (isNewItem) current.mazeCollectedItems + itemId!!
        else current.mazeCollectedItems
        return update(
            current.copy(
                mazeX = targetX,
                mazeY = targetY,
                mazeMoveDate = today,
                mazeMovesToday = movesToday + 1,
                mazeBonusMovesToday = bonusToday + if (isNewItem) 1 else 0,
                mazeCollectedItems = collected,
            ),
        )
    }

    fun completeMaze(): Boolean {
        val current = _state.value
        if (current.mazeCompleted || current.mazeX != 49 || current.mazeY != 49) return false
        return update(current.copy(mazeCompleted = true))
    }

    fun resetMazeMovesToday(): Boolean {
        val current = _state.value
        if (current.mazeSeed == 0L || current.mazeCompleted) return false
        return update(
            current.copy(
                mazeMoveDate = LocalDate.now().toString(),
                mazeMovesToday = 0,
                mazeBonusMovesToday = 0,
            ),
        )
    }

    fun startNewMaze(): Boolean {
        if (!_state.value.mazeCompleted) return false
        return update(
            _state.value.copy(
                mazeSeed = System.currentTimeMillis(),
                mazeX = 0,
                mazeY = 0,
                mazeMoveDate = null,
                mazeMovesToday = 0,
                mazeBonusMovesToday = 0,
                mazeCollectedItems = emptySet(),
                mazeCompleted = false,
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
        val savedIceDate = prefs.getString(key("ice_play_date"), null)
        return WorkState(
            activeJob = prefs.getString(key("active_job_id"), null)?.let {
                ActiveJob(it, prefs.getLong(key("active_job_started_at"), 0L))
            },
            lastWalkRewardDate = prefs.getString(key("last_walk_reward_date"), null),
            molePlayDate = savedMoleDate,
            molePlaysToday = if (savedMoleDate == today) prefs.getInt(key("mole_plays_today"), 0) else 0,
            icePlayDate = savedIceDate,
            icePlaysToday = if (savedIceDate == today) prefs.getInt(key("ice_plays_today"), 0) else 0,
            mazeSeed = prefs.getLong(key("maze_seed"), 0L),
            mazeX = prefs.getInt(key("maze_x"), 0),
            mazeY = prefs.getInt(key("maze_y"), 0),
            mazeMoveDate = prefs.getString(key("maze_move_date"), null),
            mazeMovesToday = if (prefs.getString(key("maze_move_date"), null) == today) {
                prefs.getInt(key("maze_moves_today"), 0)
            } else {
                0
            },
            mazeBonusMovesToday = if (prefs.getString(key("maze_move_date"), null) == today) {
                prefs.getInt(key("maze_bonus_moves_today"), 0)
            } else {
                0
            },
            mazeCollectedItems = prefs.getStringSet(key("maze_collected_items"), emptySet())
                .orEmpty().mapNotNull(String::toIntOrNull).toSet(),
            mazeCompleted = prefs.getBoolean(key("maze_completed"), false),
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
            .putString(key("ice_play_date"), value.icePlayDate)
            .putInt(key("ice_plays_today"), value.icePlaysToday)
            .putLong(key("maze_seed"), value.mazeSeed)
            .putInt(key("maze_x"), value.mazeX)
            .putInt(key("maze_y"), value.mazeY)
            .putString(key("maze_move_date"), value.mazeMoveDate)
            .putInt(key("maze_moves_today"), value.mazeMovesToday)
            .putInt(key("maze_bonus_moves_today"), value.mazeBonusMovesToday)
            .putStringSet(
                key("maze_collected_items"),
                value.mazeCollectedItems.map(Int::toString).toSet(),
            )
            .putBoolean(key("maze_completed"), value.mazeCompleted)
            .apply()
        return true
    }
}
