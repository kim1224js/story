package es.kim.story

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
internal const val MAZE_GRID_SIZE = 30

internal fun mazeExitCells(seed: Long): Set<Int> {
    val last = MAZE_GRID_SIZE - 1
    val boundary = buildSet {
        for (index in 0 until MAZE_GRID_SIZE) {
            add(index)
            add(last * MAZE_GRID_SIZE + index)
            add(index * MAZE_GRID_SIZE)
            add(index * MAZE_GRID_SIZE + last)
        }
    }.filter { cell ->
        val x = cell % MAZE_GRID_SIZE
        val y = cell / MAZE_GRID_SIZE
        cell != 0 && x + y >= MAZE_GRID_SIZE / 2
    }.toMutableList()
    java.util.Collections.shuffle(boundary, java.util.Random(seed xor 0x5EED_2E17L))
    return boundary.take(2).toSet()
}

data class PartTimeJob(val id: String, val title: String, val durationMillis: Long, val durationLabel: String, val rewardPercent: Double, val oncePerDay: Boolean = false)
data class ActiveJob(val jobId: String, val startedAt: Long)
enum class IceBreakResult { Missed, Found, OutOfAttempts }
data class WorkState(
    val activeJob: ActiveJob? = null,
    val balance: Long = 0,
    val lastWalkRewardDate: String? = null,
    val molePlayDate: String? = null,
    val molePlaysToday: Int = 0,
    val icePlayDate: String? = null,
    val icePlaysToday: Int = 0,
    val icePenguinIndex: Int = -1,
    val iceBrokenCells: Set<Int> = emptySet(),
    val iceAttemptsLeft: Int = 5,
    val iceGameRunning: Boolean = false,
    val icePenguinFound: Boolean = false,
    val mazeSeed: Long = 0L,
    val mazeX: Int = 0,
    val mazeY: Int = 0,
    val mazeMoveDate: String? = null,
    val mazeMovesToday: Int = 0,
    val mazeBonusMovesToday: Int = 0,
    val mazeCollectedItems: Set<Int> = emptySet(),
    val mazeVisitedCells: Set<Int> = setOf(0),
    val mazeCompleted: Boolean = false,
    val puzzleBestScore: Int = 0,
)

val partTimeJobs = listOf(
    PartTimeJob("walk_kkami", "강아지 산책시키기", 30 * 60 * 1_000L, "30분 · 하루 한 번", 50.0, true),
    PartTimeJob("cafe_4", "영자네 카페 알바", 4 * 60 * 60 * 1_000L, "4시간", 15.0),
    PartTimeJob("cafe_8", "영자네 카페 알바", 8 * 60 * 60 * 1_000L, "8시간", 30.0),
    PartTimeJob("cafe_12", "영자네 카페 알바", 12 * 60 * 60 * 1_000L, "12시간", 45.0),
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
        val sameDay = current.icePlayDate == today
        val playsToday = if (sameDay) current.icePlaysToday else 0
        if (playsToday >= 5 || current.iceGameRunning) return false
        val continuing = sameDay && current.iceBrokenCells.isNotEmpty() &&
            !current.icePenguinFound && current.iceAttemptsLeft == 0
        return update(
            current.copy(
                icePlayDate = today,
                icePlaysToday = playsToday + 1,
                icePenguinIndex = if (continuing) current.icePenguinIndex else kotlin.random.Random.nextInt(25),
                iceBrokenCells = if (continuing) current.iceBrokenCells else emptySet(),
                iceAttemptsLeft = 5,
                iceGameRunning = true,
                icePenguinFound = false,
            ),
        )
    }

    fun breakIce(index: Int): IceBreakResult? {
        val current = _state.value
        if (!current.iceGameRunning || index !in 0 until 25 || index in current.iceBrokenCells) return null
        val found = index == current.icePenguinIndex
        val attemptsLeft = (current.iceAttemptsLeft - 1).coerceAtLeast(0)
        val result = when {
            found -> IceBreakResult.Found
            attemptsLeft == 0 -> IceBreakResult.OutOfAttempts
            else -> IceBreakResult.Missed
        }
        update(
            current.copy(
                iceBrokenCells = current.iceBrokenCells + index,
                iceAttemptsLeft = attemptsLeft,
                iceGameRunning = result == IceBreakResult.Missed,
                icePenguinFound = found,
            ),
        )
        return result
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
                mazeVisitedCells = current.mazeVisitedCells + (targetY * MAZE_GRID_SIZE + targetX),
            ),
        )
    }

    fun completeMaze(): Boolean {
        val current = _state.value
        if (current.mazeCompleted || current.mazeY * MAZE_GRID_SIZE + current.mazeX !in mazeExitCells(current.mazeSeed)) return false
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
                mazeVisitedCells = setOf(0),
                mazeCompleted = false,
            ),
        )
    }


    fun recordPuzzleScore(score: Int): Boolean {
        if (score <= _state.value.puzzleBestScore) return false
        return update(_state.value.copy(puzzleBestScore = score))
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
            balance = current.balance,
            lastWalkRewardDate = if (job.oncePerDay) LocalDate.now().toString() else current.lastWalkRewardDate,
        ))
    }

    private fun load(): WorkState {
        val today = LocalDate.now().toString()
        val savedMoleDate = prefs.getString(key("mole_play_date"), null)
        val savedIceDate = prefs.getString(key("ice_play_date"), null)
        val savedMazeX = prefs.getInt(key("maze_x"), 0).coerceIn(0, MAZE_GRID_SIZE - 1)
        val savedMazeY = prefs.getInt(key("maze_y"), 0).coerceIn(0, MAZE_GRID_SIZE - 1)
        val savedVisited = prefs.getStringSet(key("maze_visited_cells"), setOf("0"))
            .orEmpty().mapNotNull(String::toIntOrNull).filter { it in 0 until MAZE_GRID_SIZE * MAZE_GRID_SIZE }.toSet()
        return WorkState(
            activeJob = prefs.getString(key("active_job_id"), null)?.let {
                ActiveJob(it, prefs.getLong(key("active_job_started_at"), 0L))
            },
            lastWalkRewardDate = prefs.getString(key("last_walk_reward_date"), null),
            molePlayDate = savedMoleDate,
            molePlaysToday = if (savedMoleDate == today) prefs.getInt(key("mole_plays_today"), 0) else 0,
            icePlayDate = savedIceDate,
            icePlaysToday = if (savedIceDate == today) prefs.getInt(key("ice_plays_today"), 0) else 0,
            icePenguinIndex = if (savedIceDate == today) prefs.getInt(key("ice_penguin_index"), -1) else -1,
            iceBrokenCells = if (savedIceDate == today) {
                prefs.getStringSet(key("ice_broken_cells"), emptySet()).orEmpty()
                    .mapNotNull(String::toIntOrNull).filter { it in 0 until 25 }.toSet()
            } else emptySet(),
            iceAttemptsLeft = if (savedIceDate == today) prefs.getInt(key("ice_attempts_left"), 5) else 5,
            iceGameRunning = savedIceDate == today && prefs.getBoolean(key("ice_game_running"), false),
            icePenguinFound = savedIceDate == today && prefs.getBoolean(key("ice_penguin_found"), false),
            mazeSeed = prefs.getLong(key("maze_seed"), 0L),
            mazeX = savedMazeX,
            mazeY = savedMazeY,
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
            mazeVisitedCells = savedVisited + 0 + (savedMazeY * MAZE_GRID_SIZE + savedMazeX),
            mazeCompleted = prefs.getBoolean(key("maze_completed"), false),
            puzzleBestScore = prefs.getInt(key("puzzle_best_score"), 0),
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
            .putInt(key("ice_penguin_index"), value.icePenguinIndex)
            .putStringSet(key("ice_broken_cells"), value.iceBrokenCells.map(Int::toString).toSet())
            .putInt(key("ice_attempts_left"), value.iceAttemptsLeft)
            .putBoolean(key("ice_game_running"), value.iceGameRunning)
            .putBoolean(key("ice_penguin_found"), value.icePenguinFound)
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
            .putStringSet(
                key("maze_visited_cells"),
                value.mazeVisitedCells.map(Int::toString).toSet(),
            )
            .putBoolean(key("maze_completed"), value.mazeCompleted)
            .putInt(key("puzzle_best_score"), value.puzzleBestScore)
            .apply()
        return true
    }
}
