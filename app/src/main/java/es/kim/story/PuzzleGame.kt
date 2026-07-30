package es.kim.story

import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlin.random.Random
import java.util.Locale

private const val PUZZLE_SIZE = 8
private const val PUZZLE_TYPES = 6
private const val RAINBOW_FRUIT = PUZZLE_TYPES
private const val RAINBOW_FRUIT_CHANCE = 1_000

private enum class PuzzlePhase { Waiting, Countdown, Playing, Finished }

@Composable
internal fun PuzzleView(viewModel: MainViewModel) {
    val state by viewModel.workState.collectAsState()
    val user by viewModel.user.collectAsState()
    val clearReward = stageClearCost(user?.chapter ?: 1)
    var selectedGame by remember { mutableIntStateOf(0) }

    Page(backgroundRes = R.drawable.work_background, backgroundAlpha = 0.5f) {
        PrimaryTabRow(selectedTabIndex = selectedGame) {
            Tab(
                selected = selectedGame == 0,
                onClick = { selectedGame = 0 },
                text = { Text("과일 교체") },
            )
            Tab(
                selected = selectedGame == 1,
                onClick = { selectedGame = 1 },
                text = { Text("과일 팡") },
            )
        }
        Spacer(Modifier.height(10.dp))
        if (selectedGame == 0) {
            PuzzleGameView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                clearReward = clearReward,
                bestScore = state.puzzleBestScore,
                onReward = { score -> viewModel.claimPuzzleReward(score, clearReward) },
            )
        } else {
            TapFruitGameView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                clearReward = clearReward,
                bestScore = state.puzzleBestScore,
                onReward = { score -> viewModel.claimPuzzleReward(score, clearReward) },
            )
        }
    }
}
@Composable
fun PuzzleGameView(
    modifier: Modifier = Modifier,
    clearReward: Long,
    bestScore: Int,
    onReward: (score: Int) -> Unit,
) {
    var board by remember { mutableStateOf(createPuzzleBoard()) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var score by remember { mutableIntStateOf(0) }
    var secondsLeft by remember { mutableIntStateOf(60) }
    var gameRound by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(3) }
    var gamePhase by remember { mutableStateOf(PuzzlePhase.Waiting) }
    var timeExpired by remember { mutableStateOf(false) }
    var rewardClaimed by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<PuzzleResult?>(null) }
    var comboMessage by remember { mutableStateOf("인접한 블록 두 개를 눌러 바꿔보세요") }
    var comboCount by remember { mutableIntStateOf(0) }
    var maxCombo by remember { mutableIntStateOf(0) }
    var swapPair by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var exploding by remember { mutableStateOf(emptySet<Int>()) }
    var wrongTiles by remember { mutableStateOf(emptySet<Int>()) }
    var inputLocked by remember { mutableStateOf(false) }
    val spriteSheet = ImageBitmap.imageResource(R.drawable.puzzle_tiles)
    val context = LocalContext.current
    val gameAudioVolume = LocalGameAudioVolume.current
    val ttsSettings = LocalTtsSettings.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val puzzleScrollState = rememberScrollState()
    val tones = remember(gameAudioVolume) {
        ToneGenerator(AudioManager.STREAM_MUSIC, (gameAudioVolume * 100).roundToInt())
    }

    DisposableEffect(tones) { onDispose { tones.release() } }
    DisposableEffect(context) {
        val engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.KOREAN
                tts?.let { configureAppTts(it, ttsSettings, TtsRole.Celebration) }
                ttsReady = true
            }
        }
        tts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            tts = null
            ttsReady = false
        }
    }

    LaunchedEffect(gameRound) {
        if (gameRound == 0) return@LaunchedEffect
        puzzleScrollState.animateScrollTo(0)
        secondsLeft = 60
        countdown = 3
        gamePhase = PuzzlePhase.Countdown
        timeExpired = false
        rewardClaimed = false
        comboMessage = "잠시 후 퍼즐이 시작됩니다"
        while (countdown > 0) {
            tones.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            delay(1_000)
            countdown -= 1
        }
        gamePhase = PuzzlePhase.Playing
        comboMessage = "인접한 블록 두 개를 눌러 바꿔보세요"
        tones.startTone(ToneGenerator.TONE_PROP_ACK, 180)
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft -= 1
        }
        gamePhase = PuzzlePhase.Finished
        timeExpired = true
        selected = null
        comboCount = 0
        comboMessage = "시간 종료! 연쇄 처리가 끝나면 보상이 지급돼요"
        tones.startTone(ToneGenerator.TONE_PROP_NACK, 450)
    }

    LaunchedEffect(timeExpired, inputLocked, rewardClaimed, score) {
        if (timeExpired && !inputLocked && !rewardClaimed) {
            rewardClaimed = true
            val previousBest = bestScore
            val earnedMoney = calculatePuzzleReward(score, clearReward)
            val isNewBest = score > previousBest
            result = PuzzleResult(score, earnedMoney, previousBest, isNewBest)
            onReward(score)
            comboMessage = "최종 $score 점 · ${formatPuzzleWon(earnedMoney)} 획득"
            tones.startTone(ToneGenerator.TONE_PROP_ACK, 600)
            if (ttsReady && gameAudioVolume > 0f && ttsSettings.enabled) {
                val recordMessage = if (isNewBest) "새로운 최고 기록입니다!" else "수고하셨습니다!"
                tts?.let { configureAppTts(it, ttsSettings, TtsRole.Celebration) }
                tts?.speak(
                    "축하합니다! 최종 점수 ${score}점, ${formatPuzzleWon(earnedMoney)}을 획득했습니다. $recordMessage",
                    TextToSpeech.QUEUE_FLUSH,
                    gameSpeechParams(gameAudioVolume),
                    "puzzle_result_${System.currentTimeMillis()}",
                )
            }
        }
    }

    fun startRound() {
        if (inputLocked) return
        result = null
        board = createPuzzleBoard()
        selected = null
        score = 0
        comboCount = 0
        maxCombo = 0
        gameRound += 1
    }

    fun selectTile(index: Int) {
        if (gamePhase != PuzzlePhase.Playing || inputLocked) return
        if (board[index] == RAINBOW_FRUIT) {
            scope.launch {
                inputLocked = true
                selected = null
                comboCount = 1
                maxCombo = maxOf(maxCombo, comboCount)
                val cleared = board.indices.toSet()
                exploding = cleared
                comboMessage = "🌈 무지개 ! 전체 폭발 +${cleared.size}점"
                tones.startTone(ToneGenerator.TONE_PROP_ACK, 700)
                delay(650)
                score += cleared.size
                board = createPuzzleBoard()
                exploding = emptySet()
                inputLocked = false
            }
            return
        }
        val first = selected
        if (first == null) {
            selected = index
            tones.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
            return
        }
        if (first == index) {
            selected = null
            return
        }
        val adjacent = abs(first / PUZZLE_SIZE - index / PUZZLE_SIZE) +
            abs(first % PUZZLE_SIZE - index % PUZZLE_SIZE) == 1
        if (!adjacent) {
            selected = index
            tones.startTone(ToneGenerator.TONE_PROP_BEEP, 50)
            return
        }

        scope.launch {
            inputLocked = true
            selected = null
            comboCount = 0
            swapPair = first to index
            tones.startTone(ToneGenerator.TONE_PROP_BEEP2, 120)
            delay(210)

            val swapped = board.toMutableList().also {
                val temp = it[first]
                it[first] = it[index]
                it[index] = temp
            }
            val firstMatches = findPuzzleMatches(swapped)
            if (firstMatches.isEmpty()) {
                wrongTiles = setOf(first, index)
                comboMessage = "앗! 3개 이상 이어지지 않아요"
                tones.startTone(ToneGenerator.TONE_PROP_NACK, 320)
                delay(340)
                swapPair = null
                wrongTiles = emptySet()
                inputLocked = false
                return@launch
            }

            board = swapped
            swapPair = null
            var current = swapped
            var cascade = 0
            var earned = 0
            var matches = firstMatches
            while (matches.isNotEmpty()) {
                cascade += 1
                comboCount = cascade
                maxCombo = maxOf(maxCombo, cascade)
                exploding = matches
                val baseScore = matches.size
                val comboBonus = matches.size * (cascade - 1)
                val gained = baseScore + comboBonus
                earned += gained
                comboMessage = if (cascade > 1) {
                    "${cascade}콤보! 기본 +${baseScore} · 연쇄 보너스 +${comboBonus}"
                } else {
                    "팡! +${gained}점"
                }
                tones.startTone(
                    if (cascade > 1) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_PROMPT,
                    180 + cascade.coerceAtMost(4) * 50,
                )
                delay(280)
                current = collapsePuzzleMatches(current, matches)
                board = current
                exploding = emptySet()
                delay(300)
                matches = findPuzzleMatches(current)
            }
            score += earned
            if (cascade > 1) comboMessage = "${cascade}콤보 성공! 총 +${earned}점"
            inputLocked = false
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
            .verticalScroll(
                state = puzzleScrollState,
                enabled = gamePhase == PuzzlePhase.Waiting || gamePhase == PuzzlePhase.Finished,
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3D5)),
            shape = RoundedCornerShape(22.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFB74D)),
        ) {
            Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Text("점수 $score", fontWeight = FontWeight.Bold)
                    Text(
                        "콤보 ×$comboCount",
                        fontWeight = FontWeight.ExtraBold,
                        color = if (comboCount >= 2) Color(0xFFE65100) else Color(0xFF6D4C41),
                    )
                    Text("남은 시간 ${secondsLeft}초", fontWeight = FontWeight.Bold,
                        color = if (secondsLeft <= 5) Color(0xFFC62828) else Color.Unspecified)
                }
                Text(
                    "이번 게임 최고 콤보 ×$maxCombo",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF7B1FA2),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "🌈 무지개 과일 등장 확률 0.1% · 누르면 전체 폭발",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF7B1FA2),
                    fontWeight = FontWeight.Bold,
                )
                Text("1점당 ${formatPuzzleWon(stageCostPercentReward(clearReward, 0.02))} · 최대 ${formatPuzzleWon(stageCostPercentReward(clearReward, 10.0))}",
                    style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold)
                Text(comboMessage, style = MaterialTheme.typography.bodySmall,
                    color = if (wrongTiles.isNotEmpty()) Color(0xFFC62828) else Color(0xFF6D4C41),
                    fontWeight = if (wrongTiles.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))

                BoxWithConstraints(
                    Modifier.fillMaxWidth().aspectRatio(1f).background(
                        Color(0xFFFFE0B2), RoundedCornerShape(16.dp),
                    ).border(3.dp, Color(0xFFFFB74D), RoundedCornerShape(16.dp)).padding(5.dp),
                ) {
                    val tileStep = (maxWidth - 21.dp) / PUZZLE_SIZE
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(PUZZLE_SIZE) { row ->
                            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                repeat(PUZZLE_SIZE) { column ->
                                    val index = row * PUZZLE_SIZE + column
                                    val partner = when (index) {
                                        swapPair?.first -> swapPair?.second
                                        swapPair?.second -> swapPair?.first
                                        else -> null
                                    }
                                    val offsetX = partner?.let { tileStep * ((it % PUZZLE_SIZE) - column) } ?: 0.dp
                                    val offsetY = partner?.let { tileStep * ((it / PUZZLE_SIZE) - row) } ?: 0.dp
                                    PuzzleTile(
                                        type = board[index],
                                        selected = selected == index,
                                        exploding = index in exploding,
                                        wrong = index in wrongTiles,
                                        offsetX = offsetX,
                                        offsetY = offsetY,
                                        spriteSheet = spriteSheet,
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                    ) { selectTile(index) }
                                }
                            }
                        }
                    }
                    if (gamePhase != PuzzlePhase.Playing) {
                        Box(
                            Modifier.matchParentSize()
                                .background(Color(0xCCFFF8E8), RoundedCornerShape(13.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                when (gamePhase) {
                                    PuzzlePhase.Waiting -> "시작 버튼을 눌러주세요"
                                    PuzzlePhase.Countdown -> countdown.coerceAtLeast(1).toString()
                                    PuzzlePhase.Finished -> "게임 종료"
                                    PuzzlePhase.Playing -> ""
                                },
                                style = if (gamePhase == PuzzlePhase.Countdown) {
                                    MaterialTheme.typography.displayLarge
                                } else {
                                    MaterialTheme.typography.titleLarge
                                },
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF8D4A00),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (timeExpired && rewardClaimed) {
                    Text("최종 점수 $score", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = ::startRound,
                    enabled = !inputLocked &&
                        (gamePhase == PuzzlePhase.Waiting || gamePhase == PuzzlePhase.Finished),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when (gamePhase) {
                            PuzzlePhase.Waiting -> "퍼즐 시작"
                            PuzzlePhase.Countdown -> "${countdown.coerceAtLeast(1)}초 후 시작"
                            PuzzlePhase.Playing -> "게임 진행 중"
                            PuzzlePhase.Finished -> "60초 다시 시작"
                        },
                    )
                }
            }
        }
    }
    result?.let { gameResult ->
        AlertDialog(
            onDismissRequest = {},
            icon = { Text(if (gameResult.isNewBest) "🏆" else "🎉", style = MaterialTheme.typography.displaySmall) },
            title = {
                Text(if (gameResult.isNewBest) "신기록 달성!" else "퍼즐 결과",
                    fontWeight = FontWeight.ExtraBold)
            },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("최종 점수", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${gameResult.score}점", style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black, color = Color(0xFFFF8F00))
                    Spacer(Modifier.height(12.dp))
                    Text("획득 재화 ${formatPuzzleWon(gameResult.reward)}", fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32))
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text("이전 최고 기록 ${gameResult.previousBest}점")
                    Text("현재 최고 기록 ${maxOf(gameResult.previousBest, gameResult.score)}점",
                        fontWeight = FontWeight.ExtraBold)
                }
            },
            confirmButton = { Button(onClick = ::startRound) { Text("다시 도전") } },
            dismissButton = { TextButton(onClick = { result = null }) { Text("닫기") } },
        )
    }
}

@Composable
private fun PuzzleTile(
    type: Int,
    selected: Boolean,
    exploding: Boolean,
    wrong: Boolean,
    offsetX: Dp,
    offsetY: Dp,
    spriteSheet: ImageBitmap,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val swapX by animateDpAsState(offsetX, tween(190), label = "puzzleSwapX")
    val swapY by animateDpAsState(offsetY, tween(190), label = "puzzleSwapY")
    val targetScale = when { exploding -> 0.08f; selected -> 0.82f; else -> 1f }
    val tileScale by animateFloatAsState(targetScale, tween(if (exploding) 240 else 160), label = "puzzleScale")
    val appearScale = remember { Animatable(0.45f) }
    val shake = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }

    LaunchedEffect(type) {
        appearScale.snapTo(0.45f)
        appearScale.animateTo(1f, tween(260))
    }
    LaunchedEffect(wrong) {
        if (wrong) {
            repeat(3) {
                shake.animateTo(-7f, tween(45))
                shake.animateTo(7f, tween(45))
            }
            shake.animateTo(0f, tween(45))
        }
    }
    LaunchedEffect(exploding) {
        if (exploding) {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(260))
        } else burst.snapTo(0f)
    }

    val sourceWidth = spriteSheet.width / 3
    val sourceHeight = spriteSheet.height / 2
    Box(
        modifier.offset(x = swapX, y = swapY)
            .zIndex(if (swapX != 0.dp || swapY != 0.dp) 2f else 0f)
            .clickable(enabled = !exploding, onClick = onClick)
            .rotate(shake.value).scale(tileScale * appearScale.value)
            .background(
                when {
                    selected -> Color.White
                    type == RAINBOW_FRUIT -> Color(0xFF455A64)
                    else -> Color(0xFFFFFAEE)
                },
                RoundedCornerShape(9.dp),
            )
            .border(if (selected || type == RAINBOW_FRUIT) 3.dp else 1.dp,
                when {
                    wrong -> Color(0xFFE53935)
                    selected -> Color(0xFFFF8F00)
                    type == RAINBOW_FRUIT -> Color(0xFFB0BEC5)
                    else -> Color(0x33A66A20)
                },
                RoundedCornerShape(9.dp)).padding(2.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (type == RAINBOW_FRUIT) {
                drawCircle(
                    color = Color(0xFF263238),
                    radius = size.minDimension * 0.47f,
                    center = center,
                )
                val colors = listOf(
                    Color(0xFFE53935), Color(0xFFFF9800), Color(0xFFFFEB3B),
                    Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA),
                )
                colors.forEachIndexed { index, color ->
                    drawCircle(
                        color = color,
                        radius = size.minDimension * (0.43f - index * 0.052f),
                        center = center,
                    )
                }
                drawLine(
                    color = Color(0xFF6D4C41),
                    start = center.copy(y = size.height * 0.18f),
                    end = center.copy(x = size.width * 0.58f, y = size.height * 0.06f),
                    strokeWidth = 3.dp.toPx(),
                )
                drawOval(
                    color = Color(0xFF43A047),
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.55f, size.height * 0.06f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.28f, size.height * 0.16f),
                )
                drawCircle(
                    Color.White.copy(alpha = 0.85f),
                    size.minDimension * 0.055f,
                    androidx.compose.ui.geometry.Offset(size.width * 0.34f, size.height * 0.3f),
                )
            } else {
                drawImage(
                    image = spriteSheet,
                    srcOffset = IntOffset((type % 3) * sourceWidth, (type / 3) * sourceHeight),
                    srcSize = IntSize(sourceWidth, sourceHeight),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                )
            }
            if (burst.value > 0f) {
                repeat(8) { particle ->
                    val angle = particle * (Math.PI * 2.0 / 8.0)
                    val distance = size.minDimension * 0.46f * burst.value
                    val x = center.x + cos(angle).toFloat() * distance
                    val y = center.y + sin(angle).toFloat() * distance
                    drawCircle(
                        color = if (particle % 2 == 0) Color(0xFFFFD54F) else Color.White,
                        radius = size.minDimension * 0.09f * (1f - burst.value * 0.6f),
                        center = androidx.compose.ui.geometry.Offset(x, y),
                    )
                }
                drawCircle(Color.White.copy(alpha = 1f - burst.value),
                    radius = size.minDimension * burst.value * 0.48f,
                    style = Stroke(width = 3.dp.toPx()))
            }
        }
    }
}

private data class PuzzleResult(
    val score: Int,
    val reward: Long,
    val previousBest: Int,
    val isNewBest: Boolean,
)

private fun createPuzzleBoard(): List<Int> {
    val board = MutableList(PUZZLE_SIZE * PUZZLE_SIZE) { 0 }
    for (row in 0 until PUZZLE_SIZE) {
        for (column in 0 until PUZZLE_SIZE) {
            val blocked = buildSet {
                if (column >= 2 && board[row * PUZZLE_SIZE + column - 1] == board[row * PUZZLE_SIZE + column - 2]) add(board[row * PUZZLE_SIZE + column - 1])
                if (row >= 2 && board[(row - 1) * PUZZLE_SIZE + column] == board[(row - 2) * PUZZLE_SIZE + column]) add(board[(row - 1) * PUZZLE_SIZE + column])
            }
            board[row * PUZZLE_SIZE + column] = randomPuzzleType(blocked)
        }
    }
    return board
}

private fun findPuzzleMatches(board: List<Int>): Set<Int> {
    val matches = mutableSetOf<Int>()
    for (row in 0 until PUZZLE_SIZE) {
        var start = 0
        while (start < PUZZLE_SIZE) {
            var end = start + 1
            while (end < PUZZLE_SIZE && board[row * PUZZLE_SIZE + end] == board[row * PUZZLE_SIZE + start]) end++
            if (end - start >= 3) for (column in start until end) matches += row * PUZZLE_SIZE + column
            start = end
        }
    }
    for (column in 0 until PUZZLE_SIZE) {
        var start = 0
        while (start < PUZZLE_SIZE) {
            var end = start + 1
            while (end < PUZZLE_SIZE && board[end * PUZZLE_SIZE + column] == board[start * PUZZLE_SIZE + column]) end++
            if (end - start >= 3) for (row in start until end) matches += row * PUZZLE_SIZE + column
            start = end
        }
    }
    return matches
}

private fun collapsePuzzleMatches(board: List<Int>, matches: Set<Int>): MutableList<Int> {
    val collapsed = board.toMutableList()
    for (column in 0 until PUZZLE_SIZE) {
        val remaining = (PUZZLE_SIZE - 1 downTo 0).map { it * PUZZLE_SIZE + column }
            .filterNot(matches::contains).map(board::get)
        var source = 0
        for (row in PUZZLE_SIZE - 1 downTo 0) {
            collapsed[row * PUZZLE_SIZE + column] =
                if (source < remaining.size) remaining[source++] else randomPuzzleType()
        }
    }
    return collapsed
}

private fun randomPuzzleType(blocked: Set<Int> = emptySet()): Int {
    if (Random.nextInt(RAINBOW_FRUIT_CHANCE) == 0) return RAINBOW_FRUIT
    return (0 until PUZZLE_TYPES).filterNot(blocked::contains).random()
}

internal fun calculatePuzzleReward(score: Int, clearReward: Long): Long {
    if (score <= 0 || clearReward <= 0L) return 0L
    val rewardPerPoint = stageCostPercentReward(clearReward, 0.02)
    val maximumReward = stageCostPercentReward(clearReward, 10.0)
    return runCatching { Math.multiplyExact(score.toLong(), rewardPerPoint) }
        .getOrDefault(maximumReward)
        .coerceAtMost(maximumReward)
}

private fun formatPuzzleWon(value: Long): String =
    formatGameCurrency(value)
