package es.kim.story

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

private const val TAP_COLUMNS = 8
private const val TAP_ROWS = 9
private const val TAP_TYPES = 6
private const val TAP_RAINBOW_APPLE = TAP_TYPES
private const val TAP_EMPTY = -1
private const val TAP_RAINBOW_CHANCE = 1_000

private enum class TapFruitPhase { Waiting, Countdown, Playing, Finished }

@Composable
internal fun TapFruitGameView(
    modifier: Modifier = Modifier,
    clearReward: Long,
    bestScore: Int,
    onReward: (Int) -> Unit,
) {
    var board by remember { mutableStateOf(createTapFruitBoard()) }
    var score by remember { mutableIntStateOf(0) }
    var secondsLeft by remember { mutableIntStateOf(60) }
    var countdown by remember { mutableIntStateOf(3) }
    var phase by remember { mutableStateOf(TapFruitPhase.Waiting) }
    var round by remember { mutableIntStateOf(0) }
    var inputLocked by remember { mutableStateOf(false) }
    var exploding by remember { mutableStateOf(emptySet<Int>()) }
    var message by remember { mutableStateOf("붙어 있는 같은 과일 2개 이상을 눌러보세요") }
    var noMoves by remember { mutableStateOf(false) }
    var rewardClaimed by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<TapFruitResult?>(null) }
    val spriteSheet = ImageBitmap.imageResource(R.drawable.tap_fruit_tiles)
    val gameVolume = LocalGameAudioVolume.current
    val scope = rememberCoroutineScope()
    val tones = remember(gameVolume) {
        ToneGenerator(AudioManager.STREAM_MUSIC, (gameVolume * 100).roundToInt())
    }

    DisposableEffect(tones) { onDispose { tones.release() } }

    LaunchedEffect(round) {
        if (round == 0) return@LaunchedEffect
        secondsLeft = 60
        countdown = 3
        score = 0
        board = createTapFruitBoard()
        exploding = emptySet()
        inputLocked = false
        noMoves = false
        rewardClaimed = false
        result = null
        phase = TapFruitPhase.Countdown
        while (countdown > 0) {
            tones.startTone(ToneGenerator.TONE_PROP_BEEP, 110)
            delay(1_000)
            countdown--
        }
        phase = TapFruitPhase.Playing
        message = "붙어 있는 같은 과일 2개 이상을 눌러보세요"
        tones.startTone(ToneGenerator.TONE_PROP_ACK, 180)
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
        phase = TapFruitPhase.Finished
        inputLocked = true
        message = "시간 종료!"
        tones.startTone(ToneGenerator.TONE_PROP_NACK, 400)
    }

    LaunchedEffect(phase, score, rewardClaimed) {
        if (phase == TapFruitPhase.Finished && !rewardClaimed) {
            rewardClaimed = true
            val previousBest = bestScore
            result = TapFruitResult(
                score = score,
                reward = calculatePuzzleReward(score, clearReward),
                previousBest = previousBest,
                isNewBest = score > previousBest,
            )
            onReward(score)
        }
    }

    fun startRound() {
        if (!inputLocked || phase == TapFruitPhase.Waiting || phase == TapFruitPhase.Finished) {
            round++
        }
    }

    fun refreshIfStuck(nextBoard: List<Int>) {
        if (nextBoard.none { it != TAP_EMPTY } || !hasTapFruitMove(nextBoard)) {
            noMoves = true
            message = "터뜨릴 수 있는 과일이 없어요! 새 판으로 바꿀게요."
            scope.launch {
                delay(900)
                board = createTapFruitBoard()
                noMoves = false
                message = "새로운 과일판이 준비됐어요!"
                inputLocked = false
            }
        } else {
            inputLocked = false
        }
    }

    fun tapFruit(index: Int) {
        if (phase != TapFruitPhase.Playing || inputLocked || board[index] == TAP_EMPTY) return
        scope.launch {
            inputLocked = true
            if (board[index] == TAP_RAINBOW_APPLE) {
                val targets = board.indices.filter { board[it] != TAP_EMPTY }.toSet()
                exploding = targets
                val gained = targets.size
                score += gained
                message = "🌈 무지개 사과! 전체 폭발 +${gained}점"
                tones.startTone(ToneGenerator.TONE_PROP_ACK, 700)
                delay(650)
                board = createTapFruitBoard()
                exploding = emptySet()
                inputLocked = false
                return@launch
            }

            val group = findTapFruitGroup(board, index)
            if (group.size < 2) {
                message = "같은 과일이 2개 이상 붙어 있어야 해요"
                tones.startTone(ToneGenerator.TONE_PROP_NACK, 260)
                inputLocked = false
                return@launch
            }

            exploding = group
            val gained = group.size * group.size
            score += gained
            message = "${group.size}개 팡! +${gained}점"
            tones.startTone(ToneGenerator.TONE_PROP_ACK, 180 + group.size.coerceAtMost(8) * 35)
            delay(320)
            val collapsed = collapseTapFruitBoard(board, group)
            board = collapsed
            exploding = emptySet()
            delay(220)
            refreshIfStuck(collapsed)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F8E2)),
            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF66BB6A)),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Text("점수 $score", fontWeight = FontWeight.ExtraBold)
                    Text(
                        "남은 시간 ${secondsLeft}초",
                        fontWeight = FontWeight.ExtraBold,
                        color = if (secondsLeft <= 5) Color(0xFFC62828) else Color.Unspecified,
                    )
                }
                Text(
                    "큰 묶음일수록 점수 증가 · 🌈 무지개 사과 0.1%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = if (noMoves) Color(0xFFC62828) else Color(0xFF455A64),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth().aspectRatio(TAP_COLUMNS.toFloat() / TAP_ROWS)
                        .background(Color(0xFFDCEDC8), RoundedCornerShape(14.dp))
                        .border(3.dp, Color(0xFF66BB6A), RoundedCornerShape(14.dp))
                        .padding(5.dp),
                ) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(TAP_ROWS) { row ->
                            Row(
                                Modifier.fillMaxWidth().weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                repeat(TAP_COLUMNS) { column ->
                                    val index = row * TAP_COLUMNS + column
                                    Box(Modifier.weight(1f).fillMaxHeight()) {
                                        if (board[index] != TAP_EMPTY) {
                                            TapFruitTile(
                                                type = board[index],
                                                exploding = index in exploding,
                                                spriteSheet = spriteSheet,
                                                modifier = Modifier.fillMaxSize(),
                                            ) { tapFruit(index) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (phase != TapFruitPhase.Playing) {
                        Box(
                            Modifier.matchParentSize()
                                .background(Color(0xDDF1F8E9), RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                when (phase) {
                                    TapFruitPhase.Waiting -> "시작 버튼을 눌러주세요"
                                    TapFruitPhase.Countdown -> countdown.coerceAtLeast(1).toString()
                                    TapFruitPhase.Finished -> "게임 종료"
                                    TapFruitPhase.Playing -> ""
                                },
                                style = if (phase == TapFruitPhase.Countdown) {
                                    MaterialTheme.typography.displayLarge
                                } else MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF2E7D32),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = ::startRound,
                    enabled = phase == TapFruitPhase.Waiting || phase == TapFruitPhase.Finished,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (phase == TapFruitPhase.Finished) "60초 다시 시작" else "과일 팡 시작")
                }
            }
        }
    }

    result?.let { gameResult ->
        AlertDialog(
            onDismissRequest = {},
            icon = { Text(if (gameResult.isNewBest) "🏆" else "🍓") },
            title = { Text(if (gameResult.isNewBest) "신기록!" else "과일 팡 결과") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("최종 ${gameResult.score}점", style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black)
                    Text("획득 ${formatGameCurrency(gameResult.reward)}", color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold)
                    Text("이전 최고 ${gameResult.previousBest}점")
                }
            },
            confirmButton = { Button(onClick = ::startRound) { Text("다시 도전") } },
            dismissButton = { TextButton(onClick = { result = null }) { Text("닫기") } },
        )
    }
}

@Composable
private fun TapFruitTile(
    type: Int,
    exploding: Boolean,
    spriteSheet: ImageBitmap,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(if (exploding) 0.05f else 1f, tween(280), label = "tapFruitScale")
    Box(
        modifier.scale(scale).background(
            if (type == TAP_RAINBOW_APPLE) Color(0xFF37474F) else Color(0xFFFFFDF5),
            RoundedCornerShape(7.dp),
        ).border(
            if (type == TAP_RAINBOW_APPLE) 2.dp else 1.dp,
            if (type == TAP_RAINBOW_APPLE) Color(0xFFE0E0E0) else Color(0x3366AA55),
            RoundedCornerShape(7.dp),
        ).clickable(enabled = !exploding, onClick = onClick).padding(1.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (type == TAP_RAINBOW_APPLE) {
                val rainbow = listOf(
                    Color(0xFFE53935), Color(0xFFFF9800), Color(0xFFFFEB3B),
                    Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA),
                )
                rainbow.forEachIndexed { index, color ->
                    drawCircle(color, size.minDimension * (0.42f - index * 0.05f), center)
                }
                drawLine(Color(0xFF5D4037), center.copy(y = size.height * 0.2f),
                    center.copy(x = size.width * 0.58f, y = size.height * 0.06f), 3.dp.toPx())
                drawCircle(Color.White.copy(alpha = 0.85f), size.minDimension * 0.05f,
                    center.copy(x = size.width * 0.36f, y = size.height * 0.32f))
                drawCircle(Color.White.copy(alpha = 0.5f), size.minDimension * 0.45f,
                    center = center, style = Stroke(2.dp.toPx()))
            } else {
                val sourceWidth = spriteSheet.width / 3
                val sourceHeight = spriteSheet.height / 2
                drawImage(
                    spriteSheet,
                    IntOffset((type % 3) * sourceWidth, (type / 3) * sourceHeight),
                    IntSize(sourceWidth, sourceHeight),
                    IntOffset.Zero,
                    IntSize(size.width.toInt(), size.height.toInt()),
                )
            }
        }
    }
}

private data class TapFruitResult(
    val score: Int,
    val reward: Long,
    val previousBest: Int,
    val isNewBest: Boolean,
)

private fun createTapFruitBoard(): List<Int> {
    var board: List<Int>
    do {
        board = List(TAP_COLUMNS * TAP_ROWS) {
            if (Random.nextInt(TAP_RAINBOW_CHANCE) == 0) TAP_RAINBOW_APPLE
            else Random.nextInt(TAP_TYPES)
        }
    } while (!hasTapFruitMove(board))
    return board
}

private fun findTapFruitGroup(board: List<Int>, start: Int): Set<Int> {
    val type = board[start]
    if (type == TAP_EMPTY || type == TAP_RAINBOW_APPLE) return emptySet()
    val found = mutableSetOf(start)
    val queue = ArrayDeque<Int>().apply { add(start) }
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        val row = current / TAP_COLUMNS
        val column = current % TAP_COLUMNS
        val neighbors = buildList {
            if (row > 0) add(current - TAP_COLUMNS)
            if (row < TAP_ROWS - 1) add(current + TAP_COLUMNS)
            if (column > 0) add(current - 1)
            if (column < TAP_COLUMNS - 1) add(current + 1)
        }
        neighbors.forEach { next ->
            if (board[next] == type && found.add(next)) queue.add(next)
        }
    }
    return found
}

private fun hasTapFruitMove(board: List<Int>): Boolean {
    if (board.any { it == TAP_RAINBOW_APPLE }) return true
    return board.indices.any { index ->
        board[index] != TAP_EMPTY && findTapFruitGroup(board, index).size >= 2
    }
}

private fun collapseTapFruitBoard(board: List<Int>, removed: Set<Int>): List<Int> {
    val next = MutableList(board.size) { TAP_EMPTY }
    for (column in 0 until TAP_COLUMNS) {
        val remaining = (TAP_ROWS - 1 downTo 0)
            .map { it * TAP_COLUMNS + column }
            .filterNot(removed::contains)
            .map(board::get)
            .filter { it != TAP_EMPTY }
        remaining.forEachIndexed { offset, type ->
            val row = TAP_ROWS - 1 - offset
            next[row * TAP_COLUMNS + column] = type
        }
    }
    return next
}
