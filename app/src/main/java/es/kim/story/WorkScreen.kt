package es.kim.story

import android.content.Intent
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import java.util.Random as JavaRandom

internal enum class MoleTargetType(val scoreMultiplier: Int, val label: String) {
    Normal(1, "두더지"),
    Golden(10, "황금 두더지"),
    Chick(-1, "병아리"),
    Hamster(-5, "햄스터"),
}

private fun createMoleTargets(): Map<Int, MoleTargetType> =
    (0 until 9).shuffled().take(3).associateWith {
        when (Random.nextInt(100)) {
            in 0 until 50 -> MoleTargetType.Normal
            in 50 until 60 -> MoleTargetType.Golden
            in 60 until 85 -> MoleTargetType.Chick
            else -> MoleTargetType.Hamster
        }
    }
@Composable
internal fun WorkView(
    viewModel: MainViewModel,
    onBgmTrackChange: (Int) -> Unit,
) {
    val context = LocalContext.current
    val gameAudioVolume = LocalGameAudioVolume.current
    val ttsSettings = LocalTtsSettings.current
    val state by viewModel.workState.collectAsState()
    val user by viewModel.user.collectAsState()
    val currentClearCost = economyStageClearCost()
    val moleRewardPerHit = stageCostPercentReward(currentClearCost, 0.02)
    val mazeReward = stageCostPercentReward(currentClearCost, 100.0)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var cancelDialog by remember { mutableStateOf(false) }
    var claimDialogJob by remember { mutableStateOf<PartTimeJob?>(null) }
    var workTab by remember { mutableIntStateOf(0) }
    var moleRunning by remember { mutableStateOf(false) }
    var moleSecondsLeft by remember { mutableIntStateOf(60) }
    var moleTargets by remember { mutableStateOf(emptyMap<Int, MoleTargetType>()) }
    var moleScore by remember { mutableIntStateOf(0) }
    var moleResult by remember { mutableStateOf<String?>(null) }
    var moleCountdown by remember { mutableStateOf<Int?>(null) }
    var moleHitEffects by remember { mutableStateOf(emptySet<Int>()) }
    var moleHitTypes by remember { mutableStateOf(emptyMap<Int, MoleTargetType>()) }
    var moleMissEffects by remember { mutableStateOf(emptySet<Int>()) }
    var moleInputLocked by remember { mutableStateOf(false) }
    var iceGameResult by remember { mutableStateOf<String?>(null) }
    var mazeResult by remember { mutableStateOf<String?>(null) }
    val icePenguinReward = stageCostPercentReward(currentClearCost, 6.0)
    val iceToneGenerator = remember(gameAudioVolume) {
        ToneGenerator(AudioManager.STREAM_MUSIC, (gameAudioVolume * 100).roundToInt())
    }
    val mazeToneGenerator = remember(gameAudioVolume) {
        ToneGenerator(AudioManager.STREAM_MUSIC, (gameAudioVolume * 100).roundToInt())
    }
    val iceSoundPool = remember { SoundPool.Builder().setMaxStreams(3).build() }
    val iceCrackSound = remember(iceSoundPool) {
        iceSoundPool.load(context, R.raw.sfx_ice_crack, 1)
    }
    val mazeStepSound = remember(iceSoundPool) {
        iceSoundPool.load(context, R.raw.sfx_maze_step, 1)
    }
    val moleEffectScope = rememberCoroutineScope()
    var moleTts by remember { mutableStateOf<TextToSpeech?>(null) }
    var moleTtsReady by remember { mutableStateOf(false) }
    StopTtsOnBackground(moleTts)

    DisposableEffect(context) {
        val engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                moleTts?.language = Locale.KOREAN
                moleTts?.let { configureAppTts(it, ttsSettings, TtsRole.GameCharacter) }
                moleTtsReady = true
            }
        }
        moleTts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            moleTts = null
            moleTtsReady = false
        }
    }

    DisposableEffect(iceToneGenerator) {
        onDispose { iceToneGenerator.release() }
    }
    DisposableEffect(mazeToneGenerator) {
        onDispose { mazeToneGenerator.release() }
    }
    DisposableEffect(iceSoundPool) {
        onDispose { iceSoundPool.release() }
    }

    LaunchedEffect(workTab) {
        onBgmTrackChange(
            when (workTab) {
                2 -> R.raw.bgm_bells_of_winter
                3 -> R.raw.bgm_creed_of_course
                else -> R.raw.bgm_jaunt
            },
        )
    }

    LaunchedEffect(state.activeJob) {
        while (state.activeJob != null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    LaunchedEffect(moleRunning) {
        if (!moleRunning) return@LaunchedEffect
        repeat(60) { elapsed ->
            moleSecondsLeft = 60 - elapsed
            moleTargets = createMoleTargets()
            delay(1_000)
        }
        moleTargets = emptyMap()
        moleHitEffects = emptySet()
        moleHitTypes = emptyMap()
        moleMissEffects = emptySet()
        moleInputLocked = false
        moleSecondsLeft = 0
        moleRunning = false
        val rewardScore = moleScore.coerceAtLeast(0)
        val totalReward = Math.multiplyExact(rewardScore.toLong(), moleRewardPerHit)
        viewModel.claimMoleReward(rewardScore, moleRewardPerHit)
        if (moleTtsReady && gameAudioVolume > 0f && ttsSettings.enabled) {
            moleTts?.let { configureAppTts(it, ttsSettings, TtsRole.GameCharacter) }
            moleTts?.speak(
                "결과 공개",
                TextToSpeech.QUEUE_FLUSH,
                gameSpeechParams(gameAudioVolume),
                "mole_result_${System.currentTimeMillis()}",
            )
        }
        moleResult = "${moleScore}점 · ${compactWon(totalReward.toDouble())} 획득"
    }

    LaunchedEffect(moleCountdown) {
        val count = moleCountdown ?: return@LaunchedEffect
        if (moleTtsReady && gameAudioVolume > 0f && ttsSettings.enabled) {
            val countdownVoice = when (count) {
                3 -> "쓰리"
                2 -> "투"
                1 -> "원"
                else -> "스타트"
            }
            moleTts?.let { configureAppTts(it, ttsSettings, TtsRole.GameCharacter) }
            moleTts?.speak(
                countdownVoice,
                TextToSpeech.QUEUE_FLUSH,
                gameSpeechParams(gameAudioVolume),
                "mole_countdown_$count",
            )
        }
        if (count > 0) {
            delay(1_000)
            moleCountdown = count - 1
        } else {
            delay(700)
            moleCountdown = null
            moleRunning = true
        }
    }

    Page(backgroundRes = R.drawable.work_background, backgroundAlpha = 0.5f) {
        PrimaryTabRow(selectedTabIndex = workTab) {
            Tab(
                selected = workTab == 0,
                onClick = { if (!moleRunning && moleCountdown == null) workTab = 0 },
                text = { Text("알바 목록") },
            )
            Tab(
                selected = workTab == 1,
                onClick = { workTab = 1 },
                text = { Text("두더지잡기") },
            )
            Tab(
                selected = workTab == 2,
                onClick = { if (!moleRunning && moleCountdown == null) workTab = 2 },
                text = { Text("얼음 깨기") },
            )
            Tab(
                selected = workTab == 3,
                onClick = { if (!moleRunning && moleCountdown == null) workTab = 3 },
                text = { Text("미로") },
            )
        }
        Spacer(Modifier.height(12.dp))
        if (workTab == 0) {
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            ) {
                val visibleJobs = state.activeJob?.let { active ->
                    partTimeJobs.filter { it.id == active.jobId }
                } ?: partTimeJobs
                visibleJobs.forEach { job ->
                    val scaledReward = economyPercentReward(job.rewardPercent)
                    val active = state.activeJob
                    val isActive = active?.jobId == job.id
                    val finishAt = (active?.startedAt ?: now) + job.durationMillis
                    val finished = isActive && now >= finishAt
                    val walkedToday = job.oncePerDay && state.lastWalkRewardDate == LocalDate.now().toString()
                    val enabled = !walkedToday && (active == null || isActive)
                    val status = when {
                        walkedToday -> "오늘 완료"
                        isActive && finished -> "완료 · 보상 받기"
                        isActive -> "진행 중 · ${formatRemaining(finishAt - now)}"
                        active != null -> "다른 알바 진행 중"
                        else -> "시작하기"
                    }

                    Card(
                        Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Box {
                            Image(
                                painter = painterResource(
                                    if (job.oncePerDay) R.drawable.running_background
                                    else R.drawable.work_background,
                                ),
                                contentDescription = null,
                                modifier = Modifier.matchParentSize(),
                                contentScale = ContentScale.Crop,
                                alpha = 0.5f,
                            )
                            Column(Modifier.padding(18.dp)) {
                                Text(job.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "${job.durationLabel} · ${compactWon(scaledReward.toDouble())} " +
                                        "(${rewardMultiplierLabel(job.rewardPercent)}%)",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        when {
                                            finished -> claimDialogJob = job
                                            isActive -> cancelDialog = true
                                            else -> viewModel.startJob(job)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = enabled,
                                ) { Text(status) }
                            }
                        }
                    }
                }
            }
        } else if (workTab == 1) {
            MoleGameView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                running = moleRunning,
                preparing = moleCountdown != null,
                secondsLeft = moleSecondsLeft,
                targets = moleTargets,
                hitEffects = moleHitEffects,
                missEffects = moleMissEffects,
                score = moleScore,
                hitTypes = moleHitTypes,
                playsToday = state.molePlaysToday,
                rewardPerHit = moleRewardPerHit,
                onStart = {
                    if (viewModel.startMoleGame()) {
                        moleScore = 0
                        moleSecondsLeft = 60
                        moleTargets = emptyMap()
                        moleHitEffects = emptySet()
                        moleHitTypes = emptyMap()
                        moleMissEffects = emptySet()
                        moleInputLocked = false
                        moleResult = null
                        moleCountdown = 3
                    }
                },
                onHit = { index ->
                    val target = moleTargets[index]
                    if (moleRunning && !moleInputLocked && target != null) {
                        moleTargets = moleTargets - index
                        moleHitEffects = moleHitEffects + index
                        moleHitTypes = moleHitTypes + (index to target)
                        moleScore += target.scoreMultiplier
                        if (moleTtsReady && gameAudioVolume > 0f && ttsSettings.enabled) {
                            moleTts?.let { configureAppTts(it, ttsSettings, TtsRole.GameCharacter) }
            moleTts?.speak(
                                when (target) {
                                    MoleTargetType.Normal -> "뀨웅"
                                    MoleTargetType.Golden -> "황금 두더지!"
                                    MoleTargetType.Chick -> "병아리는 안 돼요"
                                    MoleTargetType.Hamster -> "햄스터 조심!"
                                },
                                TextToSpeech.QUEUE_FLUSH,
                                gameSpeechParams(gameAudioVolume),
                                "mole_hit_${System.currentTimeMillis()}",
                            )
                        }
                        moleEffectScope.launch {
                            delay(320)
                            moleHitEffects = moleHitEffects - index
                            moleHitTypes = moleHitTypes - index
                        }
                    }
                },
                onMiss = { index ->
                    if (moleRunning && !moleInputLocked && index !in moleTargets) {
                        moleInputLocked = true
                        moleMissEffects = moleMissEffects + index
                        moleEffectScope.launch {
                            delay(500)
                            moleMissEffects = moleMissEffects - index
                            moleInputLocked = false
                        }
                    }
                },
            )
        } else if (workTab == 2) {
            IcePenguinGameView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                running = state.iceGameRunning,
                attemptsLeft = state.iceAttemptsLeft,
                brokenCells = state.iceBrokenCells,
                penguinIndex = state.icePenguinIndex,
                penguinFound = state.icePenguinFound,
                playsToday = state.icePlaysToday,
                reward = icePenguinReward,
                onStart = {
                    if (viewModel.startIceGame()) iceGameResult = null
                },
                onBreakIce = { index ->
                    when (viewModel.breakIce(index)) {
                        IceBreakResult.Missed -> {
                            iceSoundPool.play(iceCrackSound, gameAudioVolume, gameAudioVolume, 1, 0, 1.08f)
                        }
                        IceBreakResult.Found -> {
                            iceSoundPool.play(iceCrackSound, gameAudioVolume, gameAudioVolume, 1, 0, 1.08f)
                            viewModel.claimIcePenguinReward(icePenguinReward)
                            iceGameResult =
                                "펭귄을 찾았습니다!\n${compactWon(icePenguinReward.toDouble())} 획득"
                            moleEffectScope.launch {
                                delay(180)
                                iceToneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 450)
                                if (moleTtsReady && gameAudioVolume > 0f && ttsSettings.enabled) {
                                    moleTts?.let { configureAppTts(it, ttsSettings, TtsRole.GameCharacter) }
            moleTts?.speak(
                                        "축하해요! 펭귄을 찾았어요!",
                                        TextToSpeech.QUEUE_FLUSH,
                                        gameSpeechParams(gameAudioVolume),
                                        "ice_penguin_success_${System.currentTimeMillis()}",
                                    )
                                }
                            }
                        }
                        IceBreakResult.OutOfAttempts -> {
                            iceSoundPool.play(iceCrackSound, gameAudioVolume, gameAudioVolume, 1, 0, 1.08f)
                            iceGameResult = if (state.icePlaysToday < 5) {
                                "펭귄을 찾지 못했습니다.\n같은 얼음판에서 이어서 찾아보세요!"
                            } else {
                                "펭귄을 찾지 못했습니다.\n오늘의 도전 횟수를 모두 사용했습니다."
                            }
                        }
                        null -> Unit
                    }
                },
            )
        } else {
            LaunchedEffect(user?.userId) { viewModel.ensureMaze() }
            MazeGameView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                state = state,
                money = user?.money ?: 0L,
                reward = mazeReward,
                onMove = { dx, dy ->
                    val targetX = state.mazeX + dx
                    val targetY = state.mazeY + dy
                    if (viewModel.moveMaze(targetX, targetY)) {
                        iceSoundPool.play(
                            mazeStepSound,
                            gameAudioVolume,
                            gameAudioVolume,
                            1,
                            0,
                            0.96f + Random.nextFloat() * 0.08f,
                        )
                        if (targetY * MAZE_SIZE + targetX in mazeExitCells(state.mazeSeed)) {
                            viewModel.completeMaze(mazeReward)
                            mazeToneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 650)
                            mazeResult = "미로 탈출 성공!\n${mazeReward.won()} 획득"
                        }
                    }
                },
                onNewMaze = viewModel::startNewMaze,
                onResetMoves = { cost ->
                    viewModel.resetMazeMoves(cost) { success ->
                        mazeResult = if (success) {
                            "미로 이동 횟수를 초기화했습니다."
                        } else {
                            "재화가 부족하거나 초기화할 수 없습니다."
                        }
                    }
                },
            )
        }
    }

    if (cancelDialog) {
        AlertDialog(
            onDismissRequest = { cancelDialog = false },
            title = { Text("알바 취소") },
            text = { Text("현재 진행 중인 알바를 정말 취소할까요?\n진행 시간은 모두 사라집니다.") },
            confirmButton = {
                TextButton(onClick = { viewModel.cancelJob(); cancelDialog = false }) { Text("취소하기") }
            },
            dismissButton = { TextButton(onClick = { cancelDialog = false }) { Text("계속 진행") } },
        )
    }

    moleCountdown?.let { count ->
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            Surface(
                modifier = Modifier.size(190.dp),
                shape = RoundedCornerShape(48.dp),
                color = Color(0xFFFDF3D7),
                border = BorderStroke(3.dp, Color(0xFF795548)),
                shadowElevation = 18.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (count == 0) "시작!" else "$count",
                        color = if (count == 0) Color(0xFF2E7D32) else Color(0xFF5D4037),
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    moleResult?.let { result ->
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
            containerColor = Color(0xFFFFF8E7),
            title = {
                Text(
                    "두더지잡기 결과",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF5D4037),
                )
            },
            text = {
                Text(
                    "🎯 $result",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32),
                )
            },
            confirmButton = {
                Button(onClick = { moleResult = null }) { Text("확인") }
            },
        )
    }

    iceGameResult?.let { result ->
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = {
                Text(
                    if (state.icePenguinFound) "🐧 펭귄 발견!" else "🧊 도전 실패",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.ExtraBold,
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state.icePenguinFound) {
                        Image(
                            painter = painterResource(R.drawable.penguin_found),
                            contentDescription = "찾은 펭귄",
                            modifier = Modifier.size(180.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Text(result, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                Button(onClick = { iceGameResult = null }) { Text("확인") }
            },
        )
    }

    mazeResult?.let { result ->
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    if (result.startsWith("미로 탈출 성공")) "🏆 미로 탈출!"
                    else "🧭 미로 안내",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.ExtraBold,
                )
            },
            text = { Text(result, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) },
            confirmButton = {
                Button(onClick = { mazeResult = null }) { Text("확인") }
            },
        )
    }

    claimDialogJob?.let { job ->
        AlertDialog(
            onDismissRequest = { claimDialogJob = null },
            title = { Text("알바 완료") },
            text = {
                Text(
                    "${job.title}을 완료했습니다.\n" +
                        "${compactWon(economyPercentReward(job.rewardPercent).toDouble())}을 받을까요?",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.claimJob(job); claimDialogJob = null }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { claimDialogJob = null }) { Text("나중에") } },
        )
    }
}

@Composable
internal fun MoleGameView(
    modifier: Modifier = Modifier,
    running: Boolean,
    preparing: Boolean,
    secondsLeft: Int,
    targets: Map<Int, MoleTargetType>,
    hitEffects: Set<Int>,
    hitTypes: Map<Int, MoleTargetType>,
    missEffects: Set<Int>,
    score: Int,
    playsToday: Int,
    rewardPerHit: Long,
    onStart: () -> Unit,
    onHit: (Int) -> Unit,
    onMiss: (Int) -> Unit,
) {
    val gameScrollState = rememberScrollState()

    LaunchedEffect(running, preparing) {
        if (running || preparing) {
            delay(80)
            gameScrollState.animateScrollTo(0)
        }
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF4E6C9).copy(alpha = 0.94f)),
        border = BorderStroke(1.dp, Color(0xFF795548)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(
                    state = gameScrollState,
                    enabled = !running && !preparing,
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "두더지잡기",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF4E342E),
            )
            Text(
                "60초 동안 1초마다 3마리 · 오늘 $playsToday / 10회",
                color = Color(0xFF6D4C41),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "일반 +1 · 황금 +10 · 병아리 -1 · 햄스터 -5",
                color = Color(0xFF2E7D32),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Text("⏱ ${secondsLeft}초", fontWeight = FontWeight.ExtraBold)
                Text("🎯 ${score}점", fontWeight = FontWeight.ExtraBold)
                Text(
                    "💰 ${compactWon(Math.multiplyExact(score.coerceAtLeast(0).toLong(), rewardPerHit).toDouble())}",
                    fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                userScrollEnabled = false,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(9) { index ->
                    val targetType = targets[index] ?: hitTypes[index]
                    val visible = index in targets
                    val hit = index in hitEffects
                    val miss = index in missEffects
                    val characterScale by animateFloatAsState(
                        targetValue = if (hit) 0.78f else 1f,
                        animationSpec = tween(110),
                        label = "moleHitScale",
                    )
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(50))
                            .background(
                                when {
                                    hit -> Color(0xFFE53935)
                                    miss -> Color(0xFFBCAAA4)
                                    targetType == MoleTargetType.Golden -> Color(0xFFFFD54F)
                                    targetType == MoleTargetType.Chick -> Color(0xFFFFF59D)
                                    targetType == MoleTargetType.Hamster -> Color(0xFFD7CCC8)
                                    visible -> Color(0xFF8BC34A)
                                    else -> Color(0xFF5D4037)
                                },
                            )
                            .border(
                                if (hit) 5.dp else 3.dp,
                                when {
                                    hit -> Color(0xFFB71C1C)
                                    miss -> Color.White
                                    else -> Color(0xFF3E2723)
                                },
                                RoundedCornerShape(50),
                            )
                            .clickable(enabled = running) {
                                if (visible) onHit(index) else onMiss(index)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (visible || hit) {
                            if (hit) {
                                Canvas(Modifier.matchParentSize()) {
                                    val centerX = size.width / 2f
                                    val centerY = size.height / 2f
                                    val rayInset = size.minDimension * 0.08f
                                    val rayEnd = size.minDimension * 0.28f
                                    drawCircle(
                                        color = Color(0xFFFFF176).copy(alpha = 0.9f),
                                        radius = size.minDimension * 0.43f,
                                        style = Stroke(width = 5.dp.toPx()),
                                    )
                                    drawLine(Color.White, Offset(centerX, rayInset),
                                        Offset(centerX, rayEnd), 5.dp.toPx())
                                    drawLine(Color.White, Offset(centerX, size.height - rayInset),
                                        Offset(centerX, size.height - rayEnd), 5.dp.toPx())
                                    drawLine(Color.White, Offset(rayInset, centerY),
                                        Offset(rayEnd, centerY), 5.dp.toPx())
                                    drawLine(Color.White, Offset(size.width - rayInset, centerY),
                                        Offset(size.width - rayEnd, centerY), 5.dp.toPx())
                                    drawCircle(
                                        color = Color.White,
                                        radius = 5.dp.toPx(),
                                        center = Offset(size.width * 0.22f, size.height * 0.22f),
                                    )
                                    drawCircle(
                                        color = Color(0xFFFFEB3B),
                                        radius = 6.dp.toPx(),
                                        center = Offset(size.width * 0.78f, size.height * 0.25f),
                                    )
                                }
                            }
                            when (targetType) {
                                MoleTargetType.Chick -> Text("🐥", style = MaterialTheme.typography.displayMedium)
                                MoleTargetType.Hamster -> Text("🐹", style = MaterialTheme.typography.displayMedium)
                                MoleTargetType.Golden, MoleTargetType.Normal, null -> Image(
                                    painter = painterResource(
                                        if (hit && targetType == MoleTargetType.Normal) R.drawable.mole_character_hit
                                        else R.drawable.mole_character,
                                    ),
                                    contentDescription = targetType?.label ?: "두더지",
                                    modifier = Modifier.fillMaxSize().padding(4.dp).scale(characterScale),
                                    contentScale = ContentScale.Fit,
                                    colorFilter = if (targetType == MoleTargetType.Golden) {
                                        androidx.compose.ui.graphics.ColorFilter.tint(
                                            Color(0xFFFFC107),
                                            androidx.compose.ui.graphics.BlendMode.Modulate,
                                        )
                                    } else null,
                                )
                            }
                        } else {
                            if (miss) {
                                Box(
                                    Modifier
                                        .size(42.dp)
                                        .border(4.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(50)),
                                )
                            } else {
                                Text(
                                    "●",
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = Color(0xFF21140F),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                enabled = !running && !preparing && playsToday < 10,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5D4037)),
            ) {
                Text(
                    when {
                        running -> "게임 진행 중"
                        preparing -> "게임 준비 중"
                        playsToday >= 10 -> "오늘 10회 완료"
                        else -> "게임 시작 (${10 - playsToday}회 남음)"
                    },
                )
            }
        }
    }
}

internal val IceHexagonShape = GenericShape { size, _ ->
    moveTo(size.width * 0.25f, 0f)
    lineTo(size.width * 0.75f, 0f)
    lineTo(size.width, size.height * 0.5f)
    lineTo(size.width * 0.75f, size.height)
    lineTo(size.width * 0.25f, size.height)
    lineTo(0f, size.height * 0.5f)
    close()
}

@Composable
internal fun IcePenguinGameView(
    modifier: Modifier,
    running: Boolean,
    attemptsLeft: Int,
    brokenCells: Set<Int>,
    penguinIndex: Int,
    penguinFound: Boolean,
    playsToday: Int,
    reward: Long,
    onStart: () -> Unit,
    onBreakIce: (Int) -> Unit,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(2.dp, Color(0xFF81D4FA)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE1F5FE).copy(alpha = 0.96f)),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "🔨 얼음 속 펭귄 찾기",
                    color = Color(0xFF01579B),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "25개의 얼음 중 하나에 펭귄이 숨어 있어요! · 오늘 $playsToday / 5회",
                    color = Color(0xFF456A7D),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Text("🔨 남은 기회 $attemptsLeft / 5", fontWeight = FontWeight.ExtraBold)
                    Text("🎁 ${compactWon(reward.toDouble())}", color = Color(0xFF00695C),
                        fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(10.dp))

                Column(
                    Modifier.fillMaxWidth().background(
                        Brush.verticalGradient(listOf(Color(0xFFB3E5FC), Color(0xFFE8F8FF))),
                        RoundedCornerShape(18.dp),
                    ).padding(vertical = 10.dp, horizontal = 6.dp),
                    verticalArrangement = Arrangement.spacedBy((-5).dp),
                ) {
                    repeat(5) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(
                                start = if (row % 2 == 1) 17.dp else 0.dp,
                                end = if (row % 2 == 1) 0.dp else 17.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            repeat(5) { column ->
                                val index = row * 5 + column
                                val broken = index in brokenCells
                                val foundHere = broken && index == penguinIndex
                                Box(
                                    modifier = Modifier.weight(1f).aspectRatio(1f)
                                        .clip(IceHexagonShape)
                                        .background(
                                            when {
                                                foundHere -> Color(0xFFFFF8E1)
                                                broken -> Color(0xFF35586A)
                                                else -> Color(0xFF66D1F5)
                                            },
                                        )
                                        .border(
                                            2.dp,
                                            if (broken) Color(0xFF37474F) else Color.White,
                                            IceHexagonShape,
                                        )
                                        .clickable(enabled = running && !broken) { onBreakIce(index) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    when {
                                        foundHere -> Image(
                                            painter = painterResource(R.drawable.penguin_found),
                                            contentDescription = "펭귄",
                                            modifier = Modifier.fillMaxSize().padding(2.dp),
                                            contentScale = ContentScale.Fit,
                                        )
                                        else -> IceCellArtwork(broken = broken, seed = index)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !running && playsToday < 5,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0277BD)),
                ) {
                    Text(
                        when {
                            running -> "게임 진행 중 · 얼음을 눌러주세요"
                            brokenCells.isEmpty() || penguinFound -> "게임 시작"
                            attemptsLeft == 0 -> "같은 얼음판 이어서 찾기"
                            else -> "게임 시작"
                        },
                    )
                }
                if (running) {
                    Text("깨고 싶은 얼음을 눌러주세요.",
                        color = Color(0xFF01579B), fontWeight = FontWeight.Bold)
                } else if (penguinFound) {
                    Text("펭귄을 찾았어요!", color = Color(0xFF00897B),
                        fontWeight = FontWeight.ExtraBold)
                } else if (playsToday >= 5) {
                    Text("오늘의 5번을 모두 사용했습니다.",
                        color = Color(0xFFC62828), fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
internal fun IceCellArtwork(broken: Boolean, seed: Int) {
    Canvas(Modifier.fillMaxSize().padding(3.dp)) {
        val w = size.width
        val h = size.height
        val center = Offset(w * (0.46f + (seed % 3) * 0.02f), h * 0.52f)

        if (!broken) {
            val upperFacet = Path().apply {
                moveTo(w * 0.12f, h * 0.48f)
                lineTo(w * 0.3f, h * 0.12f)
                lineTo(w * 0.67f, h * 0.08f)
                lineTo(w * 0.5f, h * 0.54f)
                close()
            }
            drawPath(upperFacet, Color.White.copy(alpha = 0.32f))

            val lowerFacet = Path().apply {
                moveTo(w * 0.13f, h * 0.52f)
                lineTo(w * 0.5f, h * 0.54f)
                lineTo(w * 0.72f, h * 0.9f)
                lineTo(w * 0.3f, h * 0.87f)
                close()
            }
            drawPath(lowerFacet, Color(0xFF0288D1).copy(alpha = 0.17f))

            val rightFacet = Path().apply {
                moveTo(w * 0.5f, h * 0.54f)
                lineTo(w * 0.68f, h * 0.09f)
                lineTo(w * 0.91f, h * 0.5f)
                lineTo(w * 0.72f, h * 0.89f)
                close()
            }
            drawPath(rightFacet, Color(0xFFB3E5FC).copy(alpha = 0.3f))

            drawLine(
                Color.White.copy(alpha = 0.92f),
                Offset(w * 0.27f, h * 0.18f),
                Offset(w * 0.55f, h * 0.11f),
                strokeWidth = 2.2.dp.toPx(),
            )
            drawLine(
                Color.White.copy(alpha = 0.58f),
                Offset(w * 0.2f, h * 0.35f),
                Offset(w * 0.12f, h * 0.5f),
                strokeWidth = 1.2.dp.toPx(),
            )
            repeat(3) { sparkle ->
                val x = w * (0.28f + sparkle * 0.19f)
                val y = h * (0.31f + ((seed + sparkle) % 2) * 0.22f)
                drawCircle(Color.White.copy(alpha = 0.72f), w * 0.025f, Offset(x, y))
            }
        } else {
            drawCircle(
                color = Color(0xFF173744).copy(alpha = 0.9f),
                radius = w * 0.13f,
                center = center,
            )
            val crackEnds = listOf(
                Offset(w * 0.08f, h * 0.22f),
                Offset(w * 0.47f, h * 0.02f),
                Offset(w * 0.9f, h * 0.25f),
                Offset(w * 0.92f, h * 0.67f),
                Offset(w * 0.63f, h * 0.96f),
                Offset(w * 0.2f, h * 0.9f),
                Offset(w * 0.03f, h * 0.58f),
            )
            crackEnds.forEachIndexed { crackIndex, end ->
                val bend = Offset(
                    (center.x + end.x) / 2f + if ((seed + crackIndex) % 2 == 0) w * 0.06f else -w * 0.04f,
                    (center.y + end.y) / 2f,
                )
                val crack = Path().apply {
                    moveTo(center.x, center.y)
                    lineTo(bend.x, bend.y)
                    lineTo(end.x, end.y)
                }
                drawPath(
                    crack,
                    Color(0xFF102A35),
                    style = Stroke(width = 3.4.dp.toPx()),
                )
                drawPath(
                    crack,
                    Color(0xFFB3E5FC).copy(alpha = 0.72f),
                    style = Stroke(width = 1.1.dp.toPx()),
                )
            }
            repeat(5) { shard ->
                val shardX = w * (0.15f + shard * 0.17f)
                val shardY = if (shard % 2 == 0) h * 0.18f else h * 0.79f
                val iceShard = Path().apply {
                    moveTo(shardX, shardY)
                    lineTo(shardX + w * 0.09f, shardY + h * 0.03f)
                    lineTo(shardX + w * 0.035f, shardY + h * 0.13f)
                    close()
                }
                drawPath(iceShard, Color(0xFF81D4FA).copy(alpha = 0.8f))
                drawPath(
                    iceShard,
                    Color.White.copy(alpha = 0.65f),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }
    }
}

internal data class MazeLayout(
    val openings: IntArray,
    val exitCells: Set<Int>,
)

internal const val MAZE_SIZE = MAZE_GRID_SIZE
internal const val MAZE_NORTH = 1
internal const val MAZE_EAST = 2
internal const val MAZE_SOUTH = 4
internal const val MAZE_WEST = 8

internal fun generateMazeLayout(seed: Long): MazeLayout {
    val total = MAZE_SIZE * MAZE_SIZE
    val openings = IntArray(total)
    val visited = BooleanArray(total)
    val stack = ArrayDeque<Int>()
    val random = JavaRandom(seed)
    stack.addLast(0)
    visited[0] = true

    while (stack.isNotEmpty()) {
        val current = stack.last()
        val x = current % MAZE_SIZE
        val y = current / MAZE_SIZE
        val candidates = mutableListOf<Triple<Int, Int, Int>>()
        if (y > 0 && !visited[current - MAZE_SIZE]) {
            candidates += Triple(current - MAZE_SIZE, MAZE_NORTH, MAZE_SOUTH)
        }
        if (x < MAZE_SIZE - 1 && !visited[current + 1]) {
            candidates += Triple(current + 1, MAZE_EAST, MAZE_WEST)
        }
        if (y < MAZE_SIZE - 1 && !visited[current + MAZE_SIZE]) {
            candidates += Triple(current + MAZE_SIZE, MAZE_SOUTH, MAZE_NORTH)
        }
        if (x > 0 && !visited[current - 1]) {
            candidates += Triple(current - 1, MAZE_WEST, MAZE_EAST)
        }

        if (candidates.isEmpty()) {
            stack.removeLast()
        } else {
            val (next, direction, opposite) = candidates[random.nextInt(candidates.size)]
            openings[current] = openings[current] or direction
            openings[next] = openings[next] or opposite
            visited[next] = true
            stack.addLast(next)
        }
    }

    val exits = mazeExitCells(seed)
    return MazeLayout(openings, exits)
}

@Composable
internal fun MazeGameView(
    modifier: Modifier,
    state: WorkState,
    money: Long,
    reward: Long,
    onMove: (dx: Int, dy: Int) -> Unit,
    onNewMaze: () -> Unit,
    onResetMoves: (cost: Long) -> Unit,
) {
    if (state.mazeSeed == 0L) {
        Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val maze = remember(state.mazeSeed) { generateMazeLayout(state.mazeSeed) }
    val today = LocalDate.now().toString()
    val usedToday = if (state.mazeMoveDate == today) state.mazeMovesToday else 0
    val dailyLimit = MAZE_DAILY_MOVE_LIMIT
    val remaining = (dailyLimit - usedToday).coerceAtLeast(0)
    val currentCell = state.mazeY * MAZE_SIZE + state.mazeX
    val currentOpenings = maze.openings[currentCell]
    val explorerScale = remember { Animatable(1f) }
    var explorerFacing by remember(state.mazeSeed) { mutableIntStateOf(1) }
    var explorerShowingBack by remember(state.mazeSeed) { mutableStateOf(false) }

    LaunchedEffect(state.mazeX, state.mazeY) {
        explorerScale.snapTo(0.78f)
        explorerScale.animateTo(1f, tween(220))
    }

    fun move(dx: Int, dy: Int) {
        val targetX = state.mazeX + dx
        val targetY = state.mazeY + dy
        if (targetX !in 0 until MAZE_SIZE || targetY !in 0 until MAZE_SIZE) return
        if (dx < 0) explorerFacing = -1
        if (dx > 0) explorerFacing = 1
        if (dx != 0) explorerShowingBack = false
        if (dy < 0) explorerShowingBack = true
        if (dy > 0) explorerShowingBack = false
        onMove(dx, dy)
    }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(2.dp, Color(0xFF00897B)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F1).copy(alpha = 0.96f)),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {

                Box(
                    Modifier.fillMaxWidth().aspectRatio(1.18f)
                        .clip(RoundedCornerShape(18.dp))
                        .border(3.dp, Color(0xFF5D4037), RoundedCornerShape(18.dp)),
                ) {
                    Column(Modifier.matchParentSize()) {
                    repeat(4) { visibleRow ->
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            repeat(4) { visibleColumn ->
                                val mapX = state.mazeX + visibleColumn - 1
                                val mapY = state.mazeY + visibleRow - 1
                                val inMap = mapX in 0 until MAZE_SIZE && mapY in 0 until MAZE_SIZE
                                val cellIndex = if (inMap) mapY * MAZE_SIZE + mapX else -1
                                val openings = if (inMap) maze.openings[cellIndex] else 0
                                val isPlayer = visibleRow == 1 && visibleColumn == 1
                                val isOuterLine = visibleRow == 0 || visibleRow == 3 ||
                                    visibleColumn == 0 || visibleColumn == 3
                                val isVisited = cellIndex in state.mazeVisitedCells
                                val isVisible = inMap
                                val isGoal = cellIndex in maze.exitCells

                                Box(
                                    Modifier.weight(1f).fillMaxHeight()
                                        .background(if (isVisible) Color(0xFFD7CCC8) else Color(0xFF17252B)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isVisible) {
                                        Image(
                                            painter = painterResource(R.drawable.maze_floor),
                                            contentDescription = null,
                                            modifier = Modifier.matchParentSize(),
                                            contentScale = ContentScale.Crop,
                                            alpha = if (isPlayer) 1f else 0.78f,
                                        )
                                        Canvas(Modifier.matchParentSize()) {
                                            val wallColor = Color(0xFF101317)
                                            val wallLight = Color(0xFF42484F)
                                            val wallWidth = 11.dp.toPx()
                                            fun wall(start: Offset, end: Offset) {
                                                drawLine(wallColor, start, end, wallWidth)
                                                drawLine(
                                                    wallLight.copy(alpha = 0.82f),
                                                    start,
                                                    end,
                                                    3.dp.toPx(),
                                                )
                                            }
                                            if (openings and MAZE_NORTH == 0) {
                                                wall(Offset.Zero, Offset(size.width, 0f))
                                            }
                                            if (openings and MAZE_EAST == 0) {
                                                wall(Offset(size.width, 0f), Offset(size.width, size.height))
                                            }
                                            if (openings and MAZE_SOUTH == 0) {
                                                wall(Offset(0f, size.height), Offset(size.width, size.height))
                                            }
                                            if (openings and MAZE_WEST == 0) {
                                                wall(Offset.Zero, Offset(0f, size.height))
                                            }
                                        }
                                        if (isGoal) {
                                            Text("🏛️", style = MaterialTheme.typography.headlineLarge)
                                        }
                                        if (isPlayer) {
                                            Image(
                                                painter = painterResource(
                                                    if (explorerShowingBack) {
                                                        R.drawable.maze_explorer_back
                                                    } else {
                                                        R.drawable.maze_explorer
                                                    },
                                                ),
                                                contentDescription = "미로 탐험 캐릭터",
                                                modifier = Modifier.fillMaxSize().padding(5.dp)
                                                    .scale(explorerScale.value)
                                                    .graphicsLayer {
                                                        scaleX = explorerFacing.toFloat()
                                                    },
                                                contentScale = ContentScale.Fit,
                                            )
                                        }
                                        if (!isPlayer) {
                                            val fogAlpha = if (isOuterLine && !isVisited) 0.52f else 0.20f
                                            Box(
                                                Modifier.matchParentSize().background(
                                                    Color(0xFF17252B).copy(alpha = fogAlpha),
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                    MazeMiniMap(
                        currentCell = currentCell,
                        visitedCells = state.mazeVisitedCells,
                        modifier = Modifier.align(Alignment.TopEnd).padding(9.dp).size(112.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                val canMove = remaining > 0 && !state.mazeCompleted
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MazeDirectionButton(
                        icon = "▲",
                        enabled = canMove && currentOpenings and MAZE_NORTH != 0,
                    ) { move(0, -1) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MazeDirectionButton(
                            icon = "◀",
                            enabled = canMove && currentOpenings and MAZE_WEST != 0,
                        ) { move(-1, 0) }
                        Surface(
                            modifier = Modifier.size(58.dp),
                            color = Color(0xFFFFE0B2),
                            shape = RoundedCornerShape(13.dp),
                            border = BorderStroke(2.dp, Color(0xFF8D6E63)),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🧭", style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                        MazeDirectionButton(
                            icon = "▶",
                            enabled = canMove && currentOpenings and MAZE_EAST != 0,
                        ) { move(1, 0) }
                    }
                    MazeDirectionButton(
                        icon = "▼",
                        enabled = canMove && currentOpenings and MAZE_SOUTH != 0,
                    ) { move(0, 1) }
                }
                Spacer(Modifier.height(14.dp))
                Text("🧭 무릉도원 미로", color = Color(0xFF00695C),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(
                    "30×30 랜덤 미로 · 랜덤 출구 2개 · 4×4 시야",
                    color = Color(0xFF456A64),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Text("👣 $remaining / $dailyLimit", fontWeight = FontWeight.ExtraBold)
                    Text("🎁 ${compactWon(reward.toDouble())}",
                        color = Color(0xFF2E7D32), fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(10.dp))

                when {
                    state.mazeCompleted -> {
                        Text("미로를 완주했습니다!", color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.ExtraBold)
                        Button(onClick = onNewMaze, modifier = Modifier.fillMaxWidth()) {
                            Text("새로운 30×30 미로 시작")
                        }
                    }
                    remaining == 0 -> Text(
                        "오늘 이동 가능한 칸을 모두 사용했습니다. 내일 이어서 탐험하세요.",
                        color = Color(0xFFC62828),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (!state.mazeCompleted) {
                    val resetCost = (reward / 10L).coerceAtLeast(1L)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { onResetMoves(resetCost) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = usedToday > 0 && money >= resetCost,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(2.dp, Color(0xFF00796B)),
                    ) {
                        Text(
                            "${resetCost.won()} 내고 이동 횟수 초기화",
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MazeMiniMap(
    currentCell: Int,
    visitedCells: Set<Int>,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xE61B252B))
            .border(1.5.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(10.dp))
            .padding(5.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cellWidth = size.width / MAZE_SIZE
            val cellHeight = size.height / MAZE_SIZE

            visitedCells.forEach { cell ->
                if (cell in 0 until MAZE_SIZE * MAZE_SIZE) {
                    val x = cell % MAZE_SIZE
                    val y = cell / MAZE_SIZE
                    drawRect(
                        color = Color(0xFF4DD0E1),
                        topLeft = Offset(x * cellWidth, y * cellHeight),
                        size = Size(cellWidth, cellHeight),
                    )
                }
            }

            val playerX = currentCell % MAZE_SIZE
            val playerY = currentCell / MAZE_SIZE
            drawCircle(
                color = Color(0xFFFF5252),
                radius = cellWidth * 1.55f,
                center = Offset(
                    (playerX + 0.5f) * cellWidth,
                    (playerY + 0.5f) * cellHeight,
                ),
            )
            drawCircle(
                color = Color.White,
                radius = cellWidth * 0.55f,
                center = Offset(
                    (playerX + 0.5f) * cellWidth,
                    (playerY + 0.5f) * cellHeight,
                ),
            )
        }
    }
}

@Composable
internal fun MazeDirectionButton(icon: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(58.dp),
        shape = RoundedCornerShape(13.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B)),
    ) {
        Text(icon, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
    }
}

internal fun Long.won(): String = formatGameCurrency(this)

internal fun compactWon(amount: Double): String = formatGameCurrency(amount)

internal fun betAmountLabel(amount: Long): String = formatGameCurrency(amount)

internal fun seotdaBetLabel(amount: Long, currency: SeotdaBetCurrency): String =
    if (currency == SeotdaBetCurrency.BlueChip) "💎 ${amount.formattedNumber()}개" else amount.won()

internal fun formatRemaining(millis: Long): String {
    val seconds = (millis.coerceAtLeast(0) + 999) / 1_000
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val remainder = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainder)
    else "%02d:%02d".format(minutes, remainder)
}
