package es.kim.story

import android.content.Intent
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.net.Uri
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToLong
import kotlin.random.Random
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import java.util.Random as JavaRandom

private enum class MainMenu(val label: String, val icon: String, val title: String, val description: String) {
    Work("알바", "💼", "알바", "한 번에 한 개의 알바만 진행할 수 있어요"),
    Settlement("런닝", "👟", "런닝하기", "걸음 수를 채우고 보상을 받는 공간이에요"),
    Gamble("게임", "🎲", "미니게임", "게임 머니로 즐기는 카드와 승부 게임"),
    Items("아이템", "🎒", "아이템창", "장비와 보유 아이템을 확인하세요"),
    Story("스토리", "📖", "스토리", "나의 이야기를 진행해 보세요"),
    Settings("설정", "⚙", "설정", "계정과 캐릭터를 관리하세요"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuScreen(
    userId: String,
    viewModel: MainViewModel,
    bgmEnabled: Boolean,
    bgmVolume: Float,
    onBgmEnabledChange: (Boolean) -> Unit,
    onBgmVolumeChange: (Float) -> Unit,
    onBgmTrackChange: (Int) -> Unit,
    onLogout: () -> Unit,
    onSwitchAccount: (String) -> Unit,
) {
    var selected by remember { mutableStateOf(MainMenu.Story) }
    var openDbEditorRequested by remember { mutableStateOf(false) }
    val user by viewModel.user.collectAsState()
    LaunchedEffect(selected, user?.chapter) {
        onBgmTrackChange(
            when (selected) {
                MainMenu.Work -> R.raw.bgm_jaunt
                MainMenu.Gamble -> R.raw.bgm_bells_of_winter
                MainMenu.Story -> if ((user?.chapter ?: 1) >= 21) {
                    R.raw.bgm_creed_of_course
                } else {
                    R.raw.bgm_wandering_woodlands
                }
                MainMenu.Settlement, MainMenu.Items, MainMenu.Settings -> R.raw.bgm_fairy_lights
            },
        )
    }
    Scaffold(
        topBar = {
            Surface(color = Color(0xFFFFFDF5), shadowElevation = 5.dp) {
                Box(Modifier.fillMaxWidth()) {
                    NotebookPaperBackground(Modifier.matchParentSize())
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NotebookHeaderCard(
                                modifier = Modifier.weight(1f),
                                containerColor = Color(0xFFE3F2FD),
                                accentColor = Color(0xFF1976D2),
                            ) {
                                IdentityHeader(
                                    userId = user?.userId ?: userId,
                                    gender = user?.gender ?: "남성",
                                    premiumIdColor = user?.premiumIdColor == true,
                                    modifier = Modifier.fillMaxWidth(),
                                    onChangeGender = { viewModel.changeGender(user?.gender ?: "남성") },
                                )
                            }
                            NotebookHeaderCard(
                                modifier = Modifier.weight(1f).then(
                                    if (BuildConfig.DEBUG) Modifier.clickable {
                                        openDbEditorRequested = true
                                        selected = MainMenu.Settings
                                    } else Modifier
                                ),
                                containerColor = Color(0xFFFCE4EC),
                                accentColor = Color(0xFFD81B60),
                            ) {
                                HeaderValue(
                                    "스토리 챕터",
                                    if ((user?.chapter ?: 1) > storyChapters.size) {
                                        "STORY ${(storyChapters.size - 1) / 10 + 1} 완료"
                                    } else {
                                        val progress = user?.chapter ?: 1
                                        "STORY ${(progress - 1) / 10 + 1} · CH.${(progress - 1) % 10 + 1}"
                                    },
                                    Modifier.fillMaxWidth(),
                                    TextAlign.Center,
                                )
                            }
                            NotebookHeaderCard(
                                modifier = Modifier.weight(1f),
                                containerColor = Color(0xFFFFF3CD),
                                accentColor = Color(0xFFF9A825),
                            ) {
                                AnimatedMoneyHeader(
                                    money = user?.money ?: 0L,
                                    blueChips = user?.blueChips ?: 0L,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = Color.White.copy(alpha = 0.88f),
                            shape = RoundedCornerShape(9.dp),
                            border = BorderStroke(1.dp, Color(0xFF90CAF9)),
                        ) {
                            Text(
                                if (selected == MainMenu.Story) "스토리"
                                else "${selected.title} - ${selected.description}",
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF263238),
                                textAlign = if (selected == MainMenu.Story) TextAlign.Center else TextAlign.Start,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        },
        bottomBar = { BottomMenuBar(selected) { selected = it } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selected) {
                MainMenu.Work -> WorkView(viewModel, onBgmTrackChange)
                MainMenu.Settlement -> SettlementView(viewModel)
                MainMenu.Gamble -> GambleView(viewModel)
                MainMenu.Items -> ItemsView(viewModel)
                MainMenu.Story -> StoryView(viewModel)
                MainMenu.Settings -> SettingsView(
                    userId = userId,
                    viewModel = viewModel,
                    bgmEnabled = bgmEnabled,
                    bgmVolume = bgmVolume,
                    onBgmEnabledChange = onBgmEnabledChange,
                    onBgmVolumeChange = onBgmVolumeChange,
                    openDbEditor = openDbEditorRequested,
                    onDbEditorClosed = { openDbEditorRequested = false },
                    onLogout = onLogout,
                    onSwitchAccount = onSwitchAccount,
                )
            }
        }
    }
}

@Composable
private fun NotebookPaperBackground(modifier: Modifier = Modifier) {
    Canvas(modifier.background(Color(0xFFFFFDF5))) {
        val lineGap = 22.dp.toPx()
        var y = lineGap
        while (y < size.height) {
            drawLine(
                color = Color(0xFFBBDEFB).copy(alpha = 0.55f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
            y += lineGap
        }
        drawLine(
            color = Color(0xFFEF9A9A).copy(alpha = 0.72f),
            start = Offset(9.dp.toPx(), 0f),
            end = Offset(9.dp.toPx(), size.height),
            strokeWidth = 1.5.dp.toPx(),
        )
    }
}

@Composable
private fun NotebookHeaderCard(
    modifier: Modifier,
    containerColor: Color,
    accentColor: Color,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.height(64.dp),
        color = containerColor.copy(alpha = 0.94f),
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.42f)),
        shadowElevation = 2.dp,
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxHeight().width(5.dp).background(accentColor))
            Box(
                Modifier.weight(1f).fillMaxHeight().padding(horizontal = 8.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun HeaderValue(label: String, value: String, modifier: Modifier, alignment: TextAlign = TextAlign.Start) {
    Column(modifier) {
        Text(label, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = alignment)
        Text(value, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold, textAlign = alignment, maxLines = 1)
    }
}

@Composable
private fun IdentityHeader(
    userId: String,
    gender: String,
    premiumIdColor: Boolean,
    modifier: Modifier,
    onChangeGender: () -> Unit,
) {
    Column(modifier) {
        if (premiumIdColor) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Color(0xFF251207),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFC107)),
                    shadowElevation = 2.dp,
                ) {
                    Text(
                        "♠ 게임 마스터 ♠",
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        color = Color(0xFFFFD54F),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                userId,
                style = if (premiumIdColor) {
                    MaterialTheme.typography.bodyMedium.copy(
                        brush = Brush.linearGradient(
                            listOf(
                                Color(0xFF7A3E00),
                                Color(0xFFFFB300),
                                Color(0xFFFFE082),
                                Color(0xFFD06B00),
                            ),
                        ),
                    )
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                gender,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .clickable(onClick = onChangeGender)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun AnimatedMoneyHeader(
    money: Long,
    blueChips: Long,
    modifier: Modifier = Modifier,
) {
    val animatedMoney = remember { Animatable(money.toFloat()) }
    val pulseScale = remember { Animatable(1f) }
    var previousMoney by remember { mutableLongStateOf(money) }
    var increasedBy by remember { mutableLongStateOf(0L) }
    var showIncrease by remember { mutableStateOf(false) }

    LaunchedEffect(money) {
        if (money == previousMoney) return@LaunchedEffect
        val difference = money - previousMoney
        previousMoney = money
        increasedBy = difference
        showIncrease = difference > 0
        coroutineScope {
            launch { animatedMoney.animateTo(money.toFloat(), tween(durationMillis = 900)) }
            launch {
                pulseScale.animateTo(1.16f, tween(180))
                pulseScale.animateTo(1f, tween(420))
            }
        }
        delay(900)
        showIncrease = false
    }

    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(
            "보유 재화",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
        Box(contentAlignment = Alignment.TopEnd) {
            Text(
                compactWon(animatedMoney.value.toDouble()),
                modifier = Modifier.scale(pulseScale.value)
                    .background(
                        if (showIncrease) Color(0xFFFFE082) else Color.Transparent,
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = if (showIncrease) 5.dp else 0.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (showIncrease) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (showIncrease) {
                Text(
                    "+${compactWon(increasedBy.toDouble())}",
                    modifier = Modifier.offset(y = 22.dp),
                    color = Color(0xFF2E7D32),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
}
        Spacer(Modifier.height(3.dp))
        Surface(
            color = Color(0xFFE3F2FD),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF64B5F6)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("🔷", style = MaterialTheme.typography.labelMedium)
                Text(
                    blueChips.formattedNumber(),
                    color = Color(0xFF0277BD),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun BottomMenuBar(selected: MainMenu, onSelected: (MainMenu) -> Unit) {
    val barColor = Color(0xFFFFF4C2)
    val selectedColor = Color(0xFFFFD95A)
    val dividerColor = Color(0xFFD7C777)
    val visibleMenus = MainMenu.entries.filterNot { it == MainMenu.Items }
    Surface(color = barColor, shadowElevation = 10.dp, tonalElevation = 0.dp) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            visibleMenus.forEachIndexed { index, menu ->
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable { onSelected(menu) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.size(36.dp).background(
                            if (selected == menu) selectedColor else Color.Transparent,
                            RoundedCornerShape(12.dp),
                        ), contentAlignment = Alignment.Center,
                    ) { Text(menu.icon, style = MaterialTheme.typography.titleMedium) }
                    Text(menu.label, style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected == menu) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                }
                if (index < visibleMenus.lastIndex) {
                    VerticalDivider(Modifier.height(34.dp), thickness = 1.dp, color = dividerColor)
                }
            }
        }
    }
}

@Composable
private fun Page(
    backgroundRes: Int? = null,
    backgroundAlpha: Float = 0.5f,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        if (backgroundRes != null) {
            Image(
                painter = painterResource(backgroundRes),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alpha = backgroundAlpha,
            )
        }
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            content()
        }
    }
}

@Composable
private fun WorkView(
    viewModel: MainViewModel,
    onBgmTrackChange: (Int) -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.workState.collectAsState()
    val user by viewModel.user.collectAsState()
    val currentChapter = user?.chapter ?: 1
    val currentClearCost = stageClearCost(currentChapter)
    val moleRewardPerHit = stageCostPercentReward(currentClearCost, 0.02)
    val mazeReward = stageCostPercentReward(currentClearCost, 100.0)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var cancelDialog by remember { mutableStateOf(false) }
    var claimDialogJob by remember { mutableStateOf<PartTimeJob?>(null) }
    var workTab by remember { mutableIntStateOf(0) }
    var moleRunning by remember { mutableStateOf(false) }
    var moleSecondsLeft by remember { mutableIntStateOf(60) }
    var moleTargets by remember { mutableStateOf(emptySet<Int>()) }
    var moleHits by remember { mutableIntStateOf(0) }
    var moleResult by remember { mutableStateOf<String?>(null) }
    var moleCountdown by remember { mutableStateOf<Int?>(null) }
    var moleHitEffects by remember { mutableStateOf(emptySet<Int>()) }
    var moleMissEffects by remember { mutableStateOf(emptySet<Int>()) }
    var moleInputLocked by remember { mutableStateOf(false) }
    var iceGameRunning by remember { mutableStateOf(false) }
    var icePenguinIndex by remember { mutableIntStateOf(-1) }
    var brokenIceCells by remember { mutableStateOf(emptySet<Int>()) }
    var iceAttemptsLeft by remember { mutableIntStateOf(5) }
    var iceGameResult by remember { mutableStateOf<String?>(null) }
    var icePenguinFound by remember { mutableStateOf(false) }
    var mazeResult by remember { mutableStateOf<String?>(null) }
    val icePenguinReward = stageCostPercentReward(currentClearCost, 6.0)
    val iceToneGenerator = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 85) }
    val mazeToneGenerator = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 72) }
    val iceSoundPool = remember { SoundPool.Builder().setMaxStreams(3).build() }
    val iceCrackSound = remember(iceSoundPool) {
        iceSoundPool.load(context, R.raw.sfx_ice_crack, 1)
    }
    val moleEffectScope = rememberCoroutineScope()
    var moleTts by remember { mutableStateOf<TextToSpeech?>(null) }
    var moleTtsReady by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                moleTts?.language = Locale.KOREAN
                moleTts?.setPitch(1.65f)
                moleTts?.setSpeechRate(1.35f)
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
                4 -> R.raw.bgm_fairy_lights
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
        repeat(120) { elapsed ->
            moleSecondsLeft = 60 - (elapsed / 2)
            moleTargets = (0 until 9).shuffled().take(2).toSet()
            delay(500)
        }
        moleTargets = emptySet()
        moleHitEffects = emptySet()
        moleMissEffects = emptySet()
        moleInputLocked = false
        moleSecondsLeft = 0
        moleRunning = false
        val totalReward = Math.multiplyExact(moleHits.toLong(), moleRewardPerHit)
        viewModel.claimMoleReward(moleHits, moleRewardPerHit)
        if (moleTtsReady) {
            moleTts?.speak(
                "결과 공개",
                TextToSpeech.QUEUE_FLUSH,
                null,
                "mole_result_${System.currentTimeMillis()}",
            )
        }
        moleResult = "${moleHits}마리 성공 · ${compactWon(totalReward.toDouble())} 획득"
    }

    LaunchedEffect(moleCountdown) {
        val count = moleCountdown ?: return@LaunchedEffect
        if (moleTtsReady) {
            val countdownVoice = when (count) {
                3 -> "쓰리"
                2 -> "투"
                1 -> "원"
                else -> "스타트"
            }
            moleTts?.speak(
                countdownVoice,
                TextToSpeech.QUEUE_FLUSH,
                null,
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
                text = { Text("름명보 잡기") },
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
            Tab(
                selected = workTab == 4,
                onClick = { if (!moleRunning && moleCountdown == null) workTab = 4 },
                text = { Text("퍼즐") },
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
                    val scaledReward = stagePercentReward(currentChapter, job.rewardPercent)
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
                                    if (job.oncePerDay) R.drawable.kkami_walk_background
                                    else R.drawable.cafe_job_background,
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
                hits = moleHits,
                playsToday = state.molePlaysToday,
                rewardPerHit = moleRewardPerHit,
                onStart = {
                    if (viewModel.startMoleGame()) {
                        moleHits = 0
                        moleSecondsLeft = 60
                        moleTargets = emptySet()
                        moleHitEffects = emptySet()
                        moleMissEffects = emptySet()
                        moleInputLocked = false
                        moleResult = null
                        moleCountdown = 3
                    }
                },
                onHit = { index ->
                    if (moleRunning && !moleInputLocked && index in moleTargets) {
                        moleTargets = moleTargets - index
                        moleHitEffects = moleHitEffects + index
                        moleHits += 1
                        if (moleTtsReady) {
                            moleTts?.speak(
                                "뀨웅",
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                "mole_hit_${System.currentTimeMillis()}",
                            )
                        }
                        moleEffectScope.launch {
                            delay(320)
                            moleHitEffects = moleHitEffects - index
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
                running = iceGameRunning,
                attemptsLeft = iceAttemptsLeft,
                brokenCells = brokenIceCells,
                penguinIndex = icePenguinIndex,
                penguinFound = icePenguinFound,
                playsToday = state.icePlaysToday,
                reward = icePenguinReward,
                onStart = {
                    if (viewModel.startIceGame()) {
                        val continuing = brokenIceCells.isNotEmpty() &&
                            !icePenguinFound &&
                            iceAttemptsLeft == 0
                        if (!continuing) {
                            icePenguinIndex = Random.nextInt(25)
                            brokenIceCells = emptySet()
                        }
                        iceAttemptsLeft = 5
                        icePenguinFound = false
                        iceGameResult = null
                        iceGameRunning = true
                    }
                },
                onBreakIce = { index ->
                    if (iceGameRunning && index !in brokenIceCells) {
                        iceSoundPool.play(iceCrackSound, 1f, 1f, 1, 0, 1.08f)
                        brokenIceCells = brokenIceCells + index
                        iceAttemptsLeft -= 1
                        if (index == icePenguinIndex) {
                            icePenguinFound = true
                            iceGameRunning = false
                            viewModel.claimIcePenguinReward(icePenguinReward)
                            iceGameResult =
                                "펭귄을 찾았습니다!\n${compactWon(icePenguinReward.toDouble())} 획득"
                            moleEffectScope.launch {
                                delay(180)
                                iceToneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 450)
                                if (moleTtsReady) {
                                    moleTts?.speak(
                                        "축하해요! 펭귄을 찾았어요!",
                                        TextToSpeech.QUEUE_FLUSH,
                                        null,
                                        "ice_penguin_success_${System.currentTimeMillis()}",
                                    )
                                }
                            }
                        } else if (iceAttemptsLeft == 0) {
                            iceGameRunning = false
                            iceGameResult = if (state.icePlaysToday < 5) {
                                "펭귄을 찾지 못했습니다.\n같은 얼음판에서 이어서 찾아보세요!"
                            } else {
                                "펭귄을 찾지 못했습니다.\n오늘의 도전 횟수를 모두 사용했습니다."
                            }
                        }
                    }
                },
            )
        } else if (workTab == 3) {
            LaunchedEffect(user?.userId) { viewModel.ensureMaze() }
            MazeGameView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                state = state,
                money = user?.money ?: 0L,
                reward = mazeReward,
                onMove = { dx, dy, itemId ->
                    val targetX = state.mazeX + dx
                    val targetY = state.mazeY + dy
                    if (viewModel.moveMaze(targetX, targetY, itemId)) {
                        mazeToneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 65)
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
        } else {
            PuzzleGameView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                clearReward = currentClearCost,
                bestScore = state.puzzleBestScore,
                onReward = { score -> viewModel.claimPuzzleReward(score, currentClearCost) },
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
                    "름명보 잡기 결과",
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
                    if (icePenguinFound) "🐧 펭귄 발견!" else "🧊 도전 실패",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.ExtraBold,
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (icePenguinFound) {
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
                        "${compactWon(stagePercentReward(user?.chapter ?: 1, job.rewardPercent).toDouble())}을 받을까요?",
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
private fun MoleGameView(
    modifier: Modifier = Modifier,
    running: Boolean,
    preparing: Boolean,
    secondsLeft: Int,
    targets: Set<Int>,
    hitEffects: Set<Int>,
    missEffects: Set<Int>,
    hits: Int,
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
            gameScrollState.animateScrollTo(gameScrollState.maxValue)
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
                "름명보 잡기",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF4E342E),
            )
            Text(
                "60초 동안 0.5초마다 2마리 · 오늘 $playsToday / 10회",
                color = Color(0xFF6D4C41),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "한 마리당 ${compactWon(rewardPerHit.toDouble())}",
                color = Color(0xFF2E7D32),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Text("⏱ ${secondsLeft}초", fontWeight = FontWeight.ExtraBold)
                Text("🎯 ${hits}마리", fontWeight = FontWeight.ExtraBold)
                Text(
                    "💰 ${compactWon(Math.multiplyExact(hits.toLong(), rewardPerHit).toDouble())}",
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
                            Image(
                                painter = painterResource(
                                    if (hit) R.drawable.mole_character_hit
                                    else R.drawable.mole_character,
                                ),
                                contentDescription = "름명보",
                                modifier = Modifier.fillMaxSize().padding(4.dp).scale(characterScale),
                                contentScale = ContentScale.Fit,
                            )
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

private val IceHexagonShape = GenericShape { size, _ ->
    moveTo(size.width * 0.25f, 0f)
    lineTo(size.width * 0.75f, 0f)
    lineTo(size.width, size.height * 0.5f)
    lineTo(size.width * 0.75f, size.height)
    lineTo(size.width * 0.25f, size.height)
    lineTo(0f, size.height * 0.5f)
    close()
}

@Composable
private fun IcePenguinGameView(
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
private fun IceCellArtwork(broken: Boolean, seed: Int) {
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

private data class MazeLayout(
    val openings: IntArray,
    val itemCells: Set<Int>,
    val exitCells: Set<Int>,
)

private const val MAZE_SIZE = MAZE_GRID_SIZE
private const val MAZE_NORTH = 1
private const val MAZE_EAST = 2
private const val MAZE_SOUTH = 4
private const val MAZE_WEST = 8

private fun generateMazeLayout(seed: Long): MazeLayout {
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
    val goal = exits.first()
    val parent = IntArray(total) { -1 }
    val queue = ArrayDeque<Int>()
    queue.addLast(0)
    parent[0] = 0
    while (queue.isNotEmpty() && parent[goal] == -1) {
        val cell = queue.removeFirst()
        val x = cell % MAZE_SIZE
        val y = cell / MAZE_SIZE
        fun visit(next: Int, allowed: Boolean) {
            if (allowed && parent[next] == -1) {
                parent[next] = cell
                queue.addLast(next)
            }
        }
        if (y > 0) visit(cell - MAZE_SIZE, openings[cell] and MAZE_NORTH != 0)
        if (x < MAZE_SIZE - 1) visit(cell + 1, openings[cell] and MAZE_EAST != 0)
        if (y < MAZE_SIZE - 1) visit(cell + MAZE_SIZE, openings[cell] and MAZE_SOUTH != 0)
        if (x > 0) visit(cell - 1, openings[cell] and MAZE_WEST != 0)
    }

    val solution = mutableListOf<Int>()
    var cursor = goal
    while (cursor != 0 && cursor >= 0) {
        solution += cursor
        cursor = parent[cursor]
    }
    solution += 0
    solution.reverse()
    val items = (1..10).mapNotNull { number ->
        if (solution.size <= 2) null
        else solution[(number * (solution.lastIndex.toDouble() / 11.0)).roundToLong().toInt()
            .coerceIn(1, solution.lastIndex - 1)]
    }.toSet()
    return MazeLayout(openings, items, exits)
}

@Composable
private fun MazeGameView(
    modifier: Modifier,
    state: WorkState,
    money: Long,
    reward: Long,
    onMove: (dx: Int, dy: Int, itemId: Int?) -> Unit,
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
    val bonusToday = if (state.mazeMoveDate == today) state.mazeBonusMovesToday else 0
    val dailyLimit = 50 + bonusToday
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
        val targetCell = targetY * MAZE_SIZE + targetX
        val itemId = targetCell.takeIf {
            it in maze.itemCells && it !in state.mazeCollectedItems
        }
        if (dx < 0) explorerFacing = -1
        if (dx > 0) explorerFacing = 1
        if (dx != 0) explorerShowingBack = false
        if (dy < 0) explorerShowingBack = true
        if (dy > 0) explorerShowingBack = false
        onMove(dx, dy, itemId)
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
                    Text("🔷 ${state.mazeCollectedItems.size} / 10",
                        color = Color(0xFF0277BD), fontWeight = FontWeight.ExtraBold)
                    Text("🎁 ${compactWon(reward.toDouble())}",
                        color = Color(0xFF2E7D32), fontWeight = FontWeight.ExtraBold)
                }
                Text(
                    "수정 1개를 먹으면 오늘 바로 1칸 더 이동할 수 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF546E7A),
                )
                Spacer(Modifier.height(10.dp))

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
                                val hasItem = cellIndex in maze.itemCells &&
                                    cellIndex !in state.mazeCollectedItems

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
                                        if (hasItem) {
                                            Text("🔷", style = MaterialTheme.typography.headlineMedium)
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
                Spacer(Modifier.height(12.dp))

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
private fun MazeMiniMap(
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
private fun MazeDirectionButton(icon: String, enabled: Boolean, onClick: () -> Unit) {
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

private fun Long.won(): String = NumberFormat.getNumberInstance(Locale.KOREA).format(this) + "원"

private fun compactWon(amount: Double): String {
    val absolute = kotlin.math.abs(amount)
    val (divisor, unit) = when {
        absolute >= 10_000_000_000_000_000.0 -> 10_000_000_000_000_000.0 to "경원"
        absolute >= 1_000_000_000_000.0 -> 1_000_000_000_000.0 to "조원"
        absolute >= 100_000_000.0 -> 100_000_000.0 to "억원"
        absolute >= 10_000_000.0 -> 10_000_000.0 to "천만원"
        else -> return NumberFormat.getNumberInstance(Locale.KOREA).format(amount.roundToLong()) + "원"
    }
    return String.format(Locale.KOREA, "%.1f%s", amount / divisor, unit)
}

private fun betAmountLabel(amount: Long): String =
    if (amount < 100_000_000L && amount % 10_000L == 0L) {
        "${amount / 10_000L}만원"
    } else {
        compactWon(amount.toDouble())
    }

private fun seotdaBetLabel(amount: Long, currency: SeotdaBetCurrency): String =
    if (currency == SeotdaBetCurrency.BlueChip) "💎 ${amount.formattedNumber()}개" else amount.won()

private fun formatRemaining(millis: Long): String {
    val seconds = (millis.coerceAtLeast(0) + 999) / 1_000
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val remainder = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainder)
    else "%02d:%02d".format(minutes, remainder)
}

@Composable
private fun SettlementView(viewModel: MainViewModel) {
    val context = LocalContext.current
    val state by viewModel.stepQuestState.collectAsState()
    val user by viewModel.user.collectAsState()
    val currentChapter = user?.chapter ?: 1
    var settlementQuest by remember { mutableStateOf<StepQuest?>(null) }
    var showMoreMap by remember { mutableStateOf(false) }
    var moreRewardApplied by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.refreshStepQuests() }

    Page(backgroundRes = R.drawable.running_background, backgroundAlpha = 0.5f) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
            }
            Text(
                "일일 퀘스트 · 오늘 ${state.dailySteps.formattedNumber()}걸음",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF00796B),
            )
            Spacer(Modifier.height(10.dp))
            stepQuests.filter { it.period == QuestPeriod.Daily }.forEach { quest ->
                StepQuestCard(quest, state.dailySteps, state, stagePercentReward(currentChapter, quest.rewardPercent)) {
                    moreRewardApplied = false
                    settlementQuest = quest
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "✦ 주간 퀘스트 · 이번 주 ${state.weeklySteps.formattedNumber()}걸음 ✦",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF6A1B9A),
            )
            Spacer(Modifier.height(10.dp))
            stepQuests.filter { it.period == QuestPeriod.Weekly }.forEach { quest ->
                StepQuestCard(quest, state.weeklySteps, state, stagePercentReward(currentChapter, quest.rewardPercent)) {
                    moreRewardApplied = false
                    settlementQuest = quest
                }
            }
        }
    }

    settlementQuest?.let { quest ->
        val chapterReward = stagePercentReward(user?.chapter ?: 1, quest.rewardPercent)
        val settlementReward = if (moreRewardApplied) chapterReward + chapterReward / 2 else chapterReward
        Dialog(onDismissRequest = { settlementQuest = null }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            ) {
                Box {
                    Image(
                        painter = painterResource(R.drawable.quest_settlement_dialog_background),
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(Modifier.matchParentSize().background(Color.White.copy(alpha = 0.62f)))
                    Column(Modifier.fillMaxWidth().padding(24.dp)) {
                        Text(
                            "퀘스트 정산",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF4A148C),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "${if (quest.period == QuestPeriod.Daily) "일일" else "주간"} 퀘스트 " +
                                "${quest.target.formattedNumber()}걸음을 달성했습니다.\n" +
                                "${compactWon(settlementReward.toDouble())}을 정산받을까요?" +
                                    if (moreRewardApplied) "\n더 받기 보너스 50%가 적용되었습니다." else "",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF212121),
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { settlementQuest = null }) { Text("취소") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { showMoreMap = true },
                                enabled = !moreRewardApplied,
                            ) { Text(if (moreRewardApplied) "50% 적용" else "더 받기") }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                viewModel.claimStepQuest(quest, moreRewardApplied)
                                settlementQuest = null
                            }) { Text("정산받기") }
                        }
                    }
                }
            }
        }
    }

    if (showMoreMap) {
        Dialog(
            onDismissRequest = { showMoreMap = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Card(
                Modifier.fillMaxWidth().fillMaxHeight(0.86f).padding(12.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("영자카페 위치", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            moreRewardApplied = true
                            showMoreMap = false
                        }) { Text("닫기") }
                    }
                    HorizontalDivider()
                    Image(
                        painter = painterResource(R.drawable.youngja_location_map),
                        contentDescription = "영자클럽 보령점 위치",
                        modifier = Modifier.fillMaxWidth().weight(1f).background(Color.White).clickable {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(
                                    "nmap://place?id=1531121558&name=%EC%98%81%EC%9E%90%ED%81%B4%EB%9F%BD%20%EB%B3%B4%EB%A0%B9%EC%A0%90&appname=es.kim.story",
                                ),
                            ).setPackage(NAVER_MAP_PACKAGE)
                            if (intent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(intent)
                            }
                        },
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

private const val NAVER_MAP_PACKAGE = "com.nhn.android.nmap"

@Composable
private fun StepQuestCard(
    quest: StepQuest,
    steps: Long,
    state: StepQuestState,
    rewardAmount: Long,
    onClaim: () -> Unit,
) {
    val today = LocalDate.now()
    val periodKey = if (quest.period == QuestPeriod.Daily) today.toString()
    else today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toString()
    val claimed = state.claimedKeys.any { it.endsWith("|${quest.id}_$periodKey") }
    val achieved = steps >= quest.target
    val periodLabel = if (quest.period == QuestPeriod.Daily) "일퀘" else "주간퀘"
    val isWeekly = quest.period == QuestPeriod.Weekly
    val accentColor = Color(0xFF6A1B9A)
    val borderColor = if (isWeekly) Color(0xFFFFB300) else Color(0xFF00A152)

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        border = BorderStroke(2.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Box {
            Image(
                painter = painterResource(R.drawable.running_quest_background),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.5f,
            )
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "★ $periodLabel ${quest.target.formattedNumber()}걸음",
                        color = accentColor,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${compactWon(rewardAmount.toDouble())} · " +
                            "${rewardMultiplierLabel(quest.rewardPercent)}%",
                        color = Color(0xFFFF8F00),
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (steps.toFloat() / quest.target).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = accentColor,
                )
                Spacer(Modifier.height(10.dp))
                RewardClaimButton(
                    achieved = achieved,
                    claimed = claimed,
                    progressText = "${steps.coerceAtMost(quest.target).formattedNumber()} / ${quest.target.formattedNumber()}",
                    onClick = onClaim,
                )
            }
        }
    }
}

@Composable
private fun RewardClaimButton(
    achieved: Boolean,
    claimed: Boolean,
    progressText: String,
    onClick: () -> Unit,
) {
    val enabled = achieved && !claimed
    val shape = RoundedCornerShape(16.dp)
    val background = when {
        claimed -> Brush.horizontalGradient(listOf(Color(0xFF78909C), Color(0xFF546E7A)))
        achieved -> Brush.horizontalGradient(listOf(Color(0xFF7B1FA2), Color(0xFFE65100), Color(0xFFFFB300)))
        else -> Brush.horizontalGradient(listOf(Color(0xFFE0E0E0), Color(0xFFBDBDBD)))
    }
    val borderColor = when {
        claimed -> Color(0xFF90A4AE)
        achieved -> Color(0xFFFFD54F)
        else -> Color(0xFFB0BEC5)
    }
    val textColor = if (enabled || claimed) Color.White else Color(0xFF616161)

    Box(
        modifier = Modifier.fillMaxWidth().height(52.dp)
            .clip(shape)
            .background(background)
            .border(1.5.dp, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(
                when {
                    claimed -> "✓"
                    achieved -> "🎁"
                    else -> "🏃"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    claimed -> "보상 지급 완료"
                    achieved -> "달성! 보상 받기"
                    else -> progressText
                },
                color = textColor,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

private fun Long.formattedNumber(): String = NumberFormat.getNumberInstance(Locale.KOREA).format(this)

@Composable
private fun ItemsView(viewModel: MainViewModel) {
    val slots = remember { List(120) { it } }
    val user by viewModel.user.collectAsState()
    var showBagSize by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF1F0F7)).padding(horizontal = 12.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(2.dp, Color(0xFFFFC857)),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Box {
                Image(
                    painter = painterResource(R.drawable.equipment_panel_background),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(Modifier.matchParentSize().background(Color.White.copy(alpha = 0.18f)))
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚔ 장비", color = Color(0xFF5D315F), fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Text("EQUIPMENT", color = Color(0xFF5D315F).copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    EquipmentArea(user?.gender ?: "남성")
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            border = BorderStroke(2.dp, Color(0xFF7E57C2)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFCFAFF)),
            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("🎒 보유 아이템", color = Color(0xFF4527A0),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showBagSize = true }) { Text("가방", fontWeight = FontWeight.Bold) }
                }
                HorizontalDivider(color = Color(0xFFD1C4E9))
                Spacer(Modifier.height(7.dp))
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        contentPadding = PaddingValues(bottom = 5.dp),
                    ) {
                        items(slots) { index -> InventorySlot(index) }
                    }
                }
            }
        }
    }

    if (showBagSize) {
        AlertDialog(
            onDismissRequest = { showBagSize = false },
            title = { Text("가방 크기") },
            text = { Text("6 × 20") },
            confirmButton = {
                TextButton(onClick = { showBagSize = false }) { Text("확인") }
            },
        )
    }
}

@Composable
private fun EquipmentArea(gender: String) {
    val left = listOf(EquipmentSlot.Hat, EquipmentSlot.Top, EquipmentSlot.Bottom)
    val right = listOf(EquipmentSlot.Shoes, EquipmentSlot.LeftHand, EquipmentSlot.RightHand)
    Row(Modifier.fillMaxWidth().height(248.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        EquipmentColumn(left, Modifier.weight(1f))
        Image(
            painter = painterResource(
                if (gender == "여성") R.drawable.inventory_character_female
                else R.drawable.inventory_character,
            ),
            contentDescription = "장착 장비 미리보기 캐릭터",
            modifier = Modifier.weight(1.9f).fillMaxHeight(),
            contentScale = ContentScale.Fit,
        )
        EquipmentColumn(right, Modifier.weight(1f))
    }
}

@Composable
private fun EquipmentColumn(slots: List<EquipmentSlot>, modifier: Modifier) {
    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        slots.forEach { slot ->
            Box(
                Modifier.fillMaxWidth().weight(1f).border(1.5.dp, Color(0xFFFFD66B), RoundedCornerShape(10.dp))
                    .background(Color(0xFF34446F), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(slot.title, color = Color.White, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun InventorySlot(index: Int) {
    Box(
        Modifier.aspectRatio(1f).border(1.dp, Color(0xFFB39DDB), RoundedCornerShape(7.dp))
            .background(Color(0xFFF3EFFB), RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Text("${index + 1}", Modifier.padding(3.dp), style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF7E6D9F))
    }
}

@Composable
private fun GambleView(viewModel: MainViewModel) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    val gameLabels = listOf("가위바위보", "1대1 카드", "3장 카드", "교환소")
    var gambleTts by remember { mutableStateOf<TextToSpeech?>(null) }
    var gambleTtsReady by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                gambleTts?.language = Locale.KOREAN
                gambleTts?.setPitch(1.55f)
                gambleTts?.setSpeechRate(1.2f)
                gambleTtsReady = true
            }
        }
        gambleTts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            gambleTts = null
            gambleTtsReady = false
        }
    }
    val speakResult: (String) -> Unit = { message ->
        if (gambleTtsReady) {
            gambleTts?.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "gamble_result_${System.currentTimeMillis()}",
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.gamble_background),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.5f,
        )
        Column(Modifier.fillMaxSize()) {
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.White.copy(alpha = 0.9f),
            ) {
                gameLabels.forEachIndexed { index, label ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(label, maxLines = 1) },
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { page ->
                when (page) {
                    0 -> RpsGambleView(viewModel, speakResult)
                    1 -> SeotdaView(viewModel, speakResult)
                    2 -> ThreeCardSeotdaView(viewModel, speakResult)
                    else -> GambleShopView(viewModel)
                }
            }
        }
    }
}

@Composable
private fun GambleShopView(viewModel: MainViewModel) {
    val context = LocalContext.current
    val user by viewModel.user.collectAsState()
    val money = user?.money ?: 0L
    val blueChips = user?.blueChips ?: 0L
    val premiumOwned = user?.premiumIdColor == true
    val exchangeCost = es.kim.story.data.UserRepository.BLUE_CHIP_EXCHANGE_COST
    val sellValue = es.kim.story.data.UserRepository.BLUE_CHIP_SELL_VALUE
    val shopSoundPool = remember { SoundPool.Builder().setMaxStreams(2).build() }
    val exchangeSound = remember(shopSoundPool) {
        shopSoundPool.load(context, R.raw.sfx_blue_chip_exchange, 1)
    }
    DisposableEffect(shopSoundPool) {
        onDispose { shopSoundPool.release() }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(2.dp, Color(0xFF42A5F5)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF102A43)),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("💎 게임 교환소", color = Color(0xFF80D8FF),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text(
                    "게임 머니와 블루칩은 현금 가치가 없으며 현금으로 교환할 수 없습니다.",
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text("보유 게임 머니 ${money.won()}", color = Color.White)
                Text("보유 블루칩 ${blueChips.formattedNumber()}개",
                    color = Color(0xFF82B1FF), fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(18.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("💠 블루칩 바꾸기", color = Color.White, fontWeight = FontWeight.ExtraBold)
                        Text("게임 머니 ${exchangeCost.won()}으로 블루칩 1개를 바꿉니다.",
                            color = Color.White.copy(alpha = 0.75f))
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                viewModel.exchangeBlueChip {
                                    shopSoundPool.play(exchangeSound, 1f, 1f, 1, 0, 1f)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = money >= exchangeCost,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                        ) { Text("${exchangeCost.won()} → 블루칩 1개") }
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 14.dp),
                            color = Color.White.copy(alpha = 0.18f),
                        )
                        Text("🔷 블루칩 되돌리기", color = Color.White, fontWeight = FontWeight.ExtraBold)
                        Text(
                            "블루칩 1개를 게임 머니 ${sellValue.won()}으로 되돌립니다.",
                            color = Color.White.copy(alpha = 0.75f),
                        )
                        if (money > MAX_PLAYER_MONEY - sellValue) {
                            Text(
                                "보유 게임 머니가 500만원 이하일 때 바꿀 수 있어요.",
                                color = Color(0xFFFFCC80),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                viewModel.sellBlueChip {
                                    shopSoundPool.play(exchangeSound, 1f, 1f, 1, 0, 0.92f)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = blueChips >= 1L && money <= MAX_PLAYER_MONEY - sellValue,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00838F)),
                        ) { Text("블루칩 1개 → ${sellValue.won()}") }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("✨ 프리미엄 아이디 · 게임 마스터", color = Color(0xFFFFD54F),
                            fontWeight = FontWeight.ExtraBold)
                        Text("아이디를 프리미엄 골드 색상으로 바꾸고 전용 '게임 마스터' 배지를 영구 적용합니다.",
                            color = Color.White.copy(alpha = 0.75f))
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = viewModel::buyPremiumIdColor,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !premiumOwned && blueChips >= 100L,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF9A825)),
                        ) {
                            Text(if (premiumOwned) "구매 완료" else "블루칩 100개로 구매")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RpsGambleView(viewModel: MainViewModel, speakResult: (String) -> Unit) {
    val user by viewModel.user.collectAsState()
    val state by viewModel.gambleState.collectAsState()
    val wagerOptions = GambleManager.baseWagersForChapter(user?.chapter ?: 1)
    var wager by remember { mutableLongStateOf(wagerOptions[1]) }
    val money = user?.money ?: 0L
    val canPlay = wager in 1..money &&
        wager <= Long.MAX_VALUE / 2 &&
        (state.replayWager == 0L || wager == state.replayWager)
    LaunchedEffect(state.replayWager) {
        if (state.replayWager > 0) wager = state.replayWager
    }
    LaunchedEffect(wagerOptions) {
        if (state.replayWager == 0L && wager !in wagerOptions) wager = wagerOptions[1]
    }
    LaunchedEffect(state.result) {
        state.result?.let { result ->
            speakResult(
                when (result.outcome) {
                    GambleOutcome.Win -> "이겼다"
                    GambleOutcome.Lose -> "졌다"
                    GambleOutcome.Draw -> "무승부"
                },
            )
        }
    }

    Page {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(2.dp, Color(0xFFFFB300)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF221B3A)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🎲 오늘의 승부", color = Color(0xFFFFD54F),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "일일 제한 없이 무제한 플레이",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(20.dp))
                Text("보유 게임 머니 ${money.won()}", color = Color(0xFFB9F6CA), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text("배팅 금액 선택", color = Color.White, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    wagerOptions.forEach { amount ->
                        FilterChip(
                            selected = wager == amount,
                            onClick = { wager = amount },
                            modifier = Modifier.weight(1f),
                            enabled = state.replayWager == 0L,
                            label = {
                                Text(
                                    betAmountLabel(amount),
                                    Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFFD54F),
                                selectedLabelColor = Color(0xFF221B3A),
                                containerColor = Color.White,
                                labelColor = Color(0xFF221B3A),
                            ),
                        )
                    }
                }
                if (state.replayWager > 0) {
                    Text(
                        "무승부 재경기 · 같은 금액 ${state.replayWager.won()}으로 다시 선택하세요.",
                        color = Color(0xFFFFD54F),
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                    )
                }
                if (wager > money) {
                    Text("보유 재화보다 많이 배팅할 수 없습니다.", color = Color(0xFFFF8A80),
                        style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(22.dp))
                Text(
                    "패를 선택하세요",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RpsChoice.entries.forEach { choice ->
                        Button(
                            onClick = { viewModel.playRps(choice, wager) },
                            modifier = Modifier.weight(1f).height(70.dp),
                            enabled = canPlay,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A)),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(choice.icon, style = MaterialTheme.typography.headlineSmall)
                                Text(choice.label, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "승리: 배팅금의 2배 지급  ·  무승부: 원금 반환  ·  패배: 전액 차감",
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    state.result?.let { result ->
        val resultTitle = when (result.outcome) {
            GambleOutcome.Win -> "승리!"
            GambleOutcome.Draw -> "무승부"
            GambleOutcome.Lose -> "패배"
        }
        val resultColor = when (result.outcome) {
            GambleOutcome.Win -> Color(0xFF2E7D32)
            GambleOutcome.Draw -> Color(0xFF1565C0)
            GambleOutcome.Lose -> Color(0xFFC62828)
        }
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            containerColor = Color(0xFFFFFCF4),
            title = {
                Text(resultTitle, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    color = resultColor, fontWeight = FontWeight.ExtraBold)
            },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("나 ${result.player.icon}  VS  ${result.computer.icon} 상대",
                        style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        when (result.outcome) {
                            GambleOutcome.Win -> "+${result.wager.won()}"
                            GambleOutcome.Draw -> "원금 반환 · 같은 금액 재경기"
                            GambleOutcome.Lose -> "-${result.wager.won()}"
                        },
                        color = resultColor,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when (result.outcome) {
                            GambleOutcome.Win -> "축하합니다! 오늘 운이 제대로 터졌네요! 🎉"
                            GambleOutcome.Draw -> "판수 차감 없이 같은 금액으로 다시 승부합니다!"
                            GambleOutcome.Lose -> "이걸 지네? 다음 판엔 손보다 운부터 챙겨오세요 😏"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        color = resultColor,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("결과는 이미 보유 재화에 적용되었습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.acknowledgeGambleResult()
                }) { Text("확인") }
            },
        )
    }
}

@Composable
private fun SeotdaView(viewModel: MainViewModel, speakResult: (String) -> Unit) {
    val user by viewModel.user.collectAsState()
    val seotdaOpponentNames = user?.let {
        listOf(it.seotdaName1, it.seotdaName2, it.seotdaName3)
    } ?: GambleManager.DEFAULT_SEOTDA_OPPONENT_NAMES
    val state by viewModel.seotdaState.collectAsState()
    val money = user?.money ?: 0L
    val blueChips = user?.blueChips ?: 0L
    val nextBet = state.wager
    val wagerOptions = GambleManager.seotdaBaseWagersForChapter(user?.chapter ?: 1)
    var selectedBaseWager by remember { mutableLongStateOf(wagerOptions[1]) }
    var selectedBetCurrency by remember { mutableStateOf(SeotdaBetCurrency.Money) }
    var selectedPlayerCount by remember { mutableIntStateOf(2) }
    LaunchedEffect(wagerOptions) {
        if (selectedBetCurrency == SeotdaBetCurrency.Money &&
            !state.isPlaying && state.result == null && selectedBaseWager !in wagerOptions
        ) {
            selectedBaseWager = wagerOptions[1]
        }
    }
    LaunchedEffect(state.result, state.replayReason) {
        when {
            state.replayReason != null -> speakResult("다시하기")
            state.result?.outcome == SeotdaOutcome.Win -> speakResult("이겼다")
            state.result?.outcome == SeotdaOutcome.Lose -> speakResult("졌다")
            state.result?.outcome == SeotdaOutcome.Draw -> speakResult("다시하기")
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(2.dp, Color(0xFFD6A62C)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF173D2A)),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🎴 1대1 섯다", color = Color(0xFFFFD54F),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text("무제한 플레이 · ${money.won()} · 💎 ${blueChips.formattedNumber()}개",
                    color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                state.replayReason?.let {
                    Text(it, color = Color(0xFFFFD54F), fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(14.dp))

                if (!state.isPlaying && state.result == null) {
                    Text("기본 판돈 선택", color = Color.White,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        wagerOptions.forEach { wager ->
                            FilterChip(
                                selected = selectedBetCurrency == SeotdaBetCurrency.Money &&
                                    selectedBaseWager == wager,
                                onClick = {
                                    selectedBetCurrency = SeotdaBetCurrency.Money
                                    selectedBaseWager = wager
                                },
                                modifier = Modifier.weight(1f),
                                label = {
                                    Text(
                                        betAmountLabel(wager),
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center,
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFFD54F),
                                    selectedLabelColor = Color(0xFF221B3A),
                                    containerColor = Color.White,
                                    labelColor = Color(0xFF221B3A),
                                ),
                            )
                        }
                    }
                    FilterChip(
                        selected = selectedBetCurrency == SeotdaBetCurrency.BlueChip,
                        onClick = {
                            selectedBetCurrency = SeotdaBetCurrency.BlueChip
                            selectedBaseWager = GambleManager.BLUE_CHIP_BASE_WAGER
                        },
                        label = {
                            Text("💎 블루칩 1개", Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF42A5F5),
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            labelColor = Color(0xFF1565C0),
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    PlayerCountSelector(selectedPlayerCount) { selectedPlayerCount = it }
                    Spacer(Modifier.height(8.dp))
                    Text("패를 받은 뒤 최대 3번까지 현재 판돈만큼 추가 배팅합니다.",
                        color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = {
                            viewModel.startSeotda(
                                selectedBaseWager, selectedPlayerCount, selectedBetCurrency,
                            )
                        },
                        enabled =
                            if (selectedBetCurrency == SeotdaBetCurrency.Money) {
                                money >= selectedBaseWager
                            } else {
                                blueChips >= selectedBaseWager
                            },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    ) { Text("${seotdaBetLabel(selectedBaseWager, selectedBetCurrency)} 걸고 시작") }
                    if (
                        (selectedBetCurrency == SeotdaBetCurrency.Money && money < selectedBaseWager) ||
                        (selectedBetCurrency == SeotdaBetCurrency.BlueChip && blueChips < selectedBaseWager)
                    ) {
                        Text("선택한 판돈이 부족합니다.", color = Color(0xFFFF8A80))
                    }
                } else {
                    state.computerHands.forEachIndexed { opponentIndex, hand ->
                        Text(seotdaOpponentNames[opponentIndex], color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            hand.forEachIndexed { index, card ->
                                SeotdaCardView(
                                    card = card,
                                    hidden = state.result == null && index == 1,
                                    compact = state.playerCount > 2,
                                )
                            }
                        }
                        Spacer(Modifier.height(7.dp))
                    }
                    Spacer(Modifier.height(18.dp))
                    Surface(color = Color(0xFF0D281B), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("내 판돈 ${seotdaBetLabel(state.wager, state.betCurrency)}",
                                color = Color(0xFFFFD54F),
                                fontWeight = FontWeight.ExtraBold)
                            Text("추가 배팅 ${state.raises} / 3", color = Color.White.copy(alpha = 0.75f))
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("나", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.playerCards.forEach { SeotdaCardView(it) }
                    }
                    if (state.isPlaying) {
                        Spacer(Modifier.height(18.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = viewModel::showDownSeotda,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF1565C0),
                                ),
                                border = BorderStroke(1.dp, Color(0xFF1565C0)),
                            ) { Text("승부 보기") }
                            Button(
                                onClick = viewModel::raiseSeotda,
                                modifier = Modifier.weight(1f),
                                enabled = state.raises < 3 &&
                                    if (state.betCurrency == SeotdaBetCurrency.Money) {
                                        money >= nextBet
                                    } else {
                                        blueChips >= nextBet
                                    },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                            ) { Text("${seotdaBetLabel(nextBet, state.betCurrency)} 더 걸기") }
                        }
                        if (state.raises < 3 &&
                            ((state.betCurrency == SeotdaBetCurrency.Money && money < nextBet) ||
                                (state.betCurrency == SeotdaBetCurrency.BlueChip && blueChips < nextBet))
                        ) {
                            Text("추가 배팅에 필요한 판돈이 부족합니다.", color = Color(0xFFFF8A80))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        SeotdaRankGuide()
    }

    state.replayReason?.let { reason ->
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(if (reason.contains("9·4")) "구사 재경기" else "무승부 재경기") },
            text = {
                Column(
                    Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(reason, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text("나", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        state.replayPlayerCards.forEach { SeotdaCardView(it, compact = true) }
                    }
                    state.replayComputerHands.forEachIndexed { index, hand ->
                        Spacer(Modifier.height(7.dp))
                        Text(seotdaOpponentNames[index], fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            hand.forEach { SeotdaCardView(it, compact = true) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("판돈과 배팅 단계는 유지되며 플레이 횟수는 추가 차감되지 않습니다.",
                        textAlign = TextAlign.Center)
                }
            },
            confirmButton = {
                Button(onClick = viewModel::acknowledgeSeotdaReplay) { Text("새 패 확인") }
            },
        )
    }

    state.result?.let { result ->
        val title = when (result.outcome) {
            SeotdaOutcome.Win -> "승리!"
            SeotdaOutcome.Draw -> "무승부"
            SeotdaOutcome.Lose -> "패배"
        }
        val color = when (result.outcome) {
            SeotdaOutcome.Win -> Color(0xFF2E7D32)
            SeotdaOutcome.Draw -> Color(0xFF1565C0)
            SeotdaOutcome.Lose -> Color(0xFFC62828)
        }
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(title, color = color, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(
                    Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val firstPlaceIndex = result.firstPlaceComputerIndex
                    val firstPlaceName = firstPlaceIndex?.let { seotdaOpponentNames[it] } ?: "나"
                    val firstPlaceRank = firstPlaceIndex?.let { result.computerRanks[it] }
                        ?: result.playerRank
                    Text(
                        "🏆 1등: $firstPlaceName (${firstPlaceRank.name})",
                        color = Color(0xFFF57F17),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    state.computerHands.forEachIndexed { index, hand ->
                        Text("${seotdaOpponentNames[index]}: ${result.computerRanks[index].name}",
                            fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            hand.forEach { SeotdaCardView(it, compact = true) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("나 ${result.playerRank.name}",
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    if (result.outcome == SeotdaOutcome.Lose) {
                        Text(
                            "${result.playerPlacement}위 / ${state.playerCount}명",
                            color = color,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when (result.outcome) {
                            SeotdaOutcome.Win ->
                                "+${seotdaBetLabel(result.wager, result.betCurrency)}" +
                                if (result.premium > 0) "\n족보 보너스 +${result.premium.won()}" else ""
                            SeotdaOutcome.Draw -> "판돈 반환"
                            SeotdaOutcome.Lose -> "-${seotdaBetLabel(result.wager, result.betCurrency)}"
                        },
                        color = color, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            },
            confirmButton = {
                Button(onClick = viewModel::acknowledgeSeotdaResult) { Text("확인") }
            },
        )
    }
}

@Composable
private fun ThreeCardSeotdaView(viewModel: MainViewModel, speakResult: (String) -> Unit) {
    val user by viewModel.user.collectAsState()
    val seotdaOpponentNames = user?.let {
        listOf(it.seotdaName1, it.seotdaName2, it.seotdaName3)
    } ?: GambleManager.DEFAULT_SEOTDA_OPPONENT_NAMES
    val state by viewModel.threeCardSeotdaState.collectAsState()
    val money = user?.money ?: 0L
    val blueChips = user?.blueChips ?: 0L
    val wagerOptions = GambleManager.seotdaBaseWagersForChapter(user?.chapter ?: 1)
    var selectedBaseWager by remember { mutableLongStateOf(wagerOptions[1]) }
    var selectedBetCurrency by remember { mutableStateOf(SeotdaBetCurrency.Money) }
    var selectedPlayerCount by remember { mutableIntStateOf(2) }
    LaunchedEffect(wagerOptions) {
        if (selectedBetCurrency == SeotdaBetCurrency.Money &&
            !state.isPlaying && state.result == null && selectedBaseWager !in wagerOptions
        ) {
            selectedBaseWager = wagerOptions[1]
        }
    }
    LaunchedEffect(state.result, state.replayReason) {
        when {
            state.replayReason != null -> speakResult("다시하기")
            state.result?.outcome == SeotdaOutcome.Win -> speakResult("이겼다")
            state.result?.outcome == SeotdaOutcome.Lose -> speakResult("졌다")
            state.result?.outcome == SeotdaOutcome.Draw -> speakResult("다시하기")
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(2.dp, Color(0xFFD6A62C)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF173D2A)),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🎴 3장 섯다", color = Color(0xFFFFD54F),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text("무제한 플레이 · ${money.won()} · 💎 ${blueChips.formattedNumber()}개",
                    color = Color.White.copy(alpha = 0.85f))
                state.replayReason?.let {
                    Text(it, color = Color(0xFFFFD54F), fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(12.dp))

                if (!state.isPlaying && state.result == null) {
                    Text("기본 판돈 선택", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        wagerOptions.forEach { wager ->
                            FilterChip(
                                selected = selectedBetCurrency == SeotdaBetCurrency.Money &&
                                    selectedBaseWager == wager,
                                onClick = {
                                    selectedBetCurrency = SeotdaBetCurrency.Money
                                    selectedBaseWager = wager
                                },
                                modifier = Modifier.weight(1f),
                                label = {
                                    Text(
                                        betAmountLabel(wager),
                                        Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center,
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFFD54F),
                                    selectedLabelColor = Color(0xFF221B3A),
                                    containerColor = Color.White,
                                    labelColor = Color(0xFF221B3A),
                                ),
                            )
                        }
                    }
                    FilterChip(
                        selected = selectedBetCurrency == SeotdaBetCurrency.BlueChip,
                        onClick = {
                            selectedBetCurrency = SeotdaBetCurrency.BlueChip
                            selectedBaseWager = GambleManager.BLUE_CHIP_BASE_WAGER
                        },
                        label = {
                            Text("💎 블루칩 1개", Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF42A5F5),
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            labelColor = Color(0xFF1565C0),
                        ),
                    )
                    PlayerCountSelector(selectedPlayerCount) { selectedPlayerCount = it }
                    Spacer(Modifier.height(6.dp))
                    Text("3장 중 가장 높은 2장 조합으로 승부합니다.",
                        color = Color.White.copy(alpha = 0.72f), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            viewModel.startThreeCardSeotda(
                                selectedBaseWager, selectedPlayerCount, selectedBetCurrency,
                            )
                        },
                        enabled =
                            if (selectedBetCurrency == SeotdaBetCurrency.Money) {
                                money >= selectedBaseWager
                            } else {
                                blueChips >= selectedBaseWager
                            },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    ) { Text("${seotdaBetLabel(selectedBaseWager, selectedBetCurrency)} 걸고 시작") }
                    if (
                        (selectedBetCurrency == SeotdaBetCurrency.Money && money < selectedBaseWager) ||
                        (selectedBetCurrency == SeotdaBetCurrency.BlueChip && blueChips < selectedBaseWager)
                    ) {
                        Text("선택한 판돈이 부족합니다.", color = Color(0xFFFF8A80))
                    }
                } else {
                    state.computerHands.forEachIndexed { opponentIndex, hand ->
                        Text(seotdaOpponentNames[opponentIndex], color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            hand.forEachIndexed { index, card ->
                                SeotdaCardView(
                                    card,
                                    hidden = state.result == null && index > 0,
                                    compact = true,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Surface(color = Color(0xFF0D281B), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("내 판돈 ${seotdaBetLabel(state.wager, state.betCurrency)}",
                                color = Color(0xFFFFD54F),
                                fontWeight = FontWeight.ExtraBold)
                            Text("추가 배팅 ${state.raises} / 3", color = Color.White.copy(alpha = 0.75f))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("나", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        state.playerCards.forEachIndexed { index, card ->
                            val selected = index in state.selectedCardIndices
                            Box(
                                Modifier
                                    .border(
                                        if (selected) 3.dp else 1.dp,
                                        if (selected) Color(0xFFFFD54F) else Color.Transparent,
                                        RoundedCornerShape(9.dp),
                                    )
                                    .clickable(enabled = state.isPlaying) {
                                        viewModel.toggleThreeCardSelection(index)
                                    }
                                    .padding(2.dp),
                            ) {
                                SeotdaCardView(card, compact = true)
                            }
                        }
                    }
                    Text(
                        "제출할 패 2장을 선택하세요 (${state.selectedCardIndices.size}/2)",
                        color = if (state.selectedCardIndices.size == 2) Color(0xFFB9F6CA) else Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    if (state.isPlaying) {
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = viewModel::showDownThreeCardSeotda,
                                modifier = Modifier.weight(1f),
                                enabled = state.selectedCardIndices.size == 2,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF1565C0),
                                ),
                                border = BorderStroke(1.dp, Color(0xFF1565C0)),
                            ) { Text("승부 보기") }
                            Button(
                                onClick = viewModel::raiseThreeCardSeotda,
                                modifier = Modifier.weight(1f),
                                enabled = state.raises < 3 &&
                                    (if (state.betCurrency == SeotdaBetCurrency.Money) {
                                        money >= state.wager
                                    } else {
                                        blueChips >= state.wager
                                    }) &&
                                    (state.raises < 2 || state.selectedCardIndices.size == 2),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                            ) { Text("${seotdaBetLabel(state.wager, state.betCurrency)} 더 걸기") }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SeotdaRankGuide()
    }

    state.replayReason?.let { reason ->
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(if (reason.contains("9·4")) "구사 재경기" else "무승부 재경기") },
            text = {
                Column(
                    Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(reason, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text("나", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.replayPlayerCards.forEach { SeotdaCardView(it, compact = true) }
                    }
                    state.replayComputerHands.forEachIndexed { index, hand ->
                        Spacer(Modifier.height(6.dp))
                        Text(seotdaOpponentNames[index], fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            hand.forEach { SeotdaCardView(it, compact = true) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("판돈과 배팅 단계는 유지되며 플레이 횟수는 추가 차감되지 않습니다.",
                        textAlign = TextAlign.Center)
                }
            },
            confirmButton = {
                Button(onClick = viewModel::acknowledgeThreeCardSeotdaReplay) { Text("새 패 확인") }
            },
        )
    }

    state.result?.let { result ->
        val title = when (result.outcome) {
            SeotdaOutcome.Win -> "승리!"
            SeotdaOutcome.Draw -> "무승부"
            SeotdaOutcome.Lose -> "패배"
        }
        val color = when (result.outcome) {
            SeotdaOutcome.Win -> Color(0xFF2E7D32)
            SeotdaOutcome.Draw -> Color(0xFF1565C0)
            SeotdaOutcome.Lose -> Color(0xFFC62828)
        }
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(title, color = color, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val firstPlaceIndex = result.firstPlaceComputerIndex
                    val firstPlaceName = firstPlaceIndex?.let { seotdaOpponentNames[it] } ?: "나"
                    val firstPlaceRank = firstPlaceIndex?.let { result.computerRanks[it] }
                        ?: result.playerRank
                    Text(
                        "🏆 1등: $firstPlaceName (${firstPlaceRank.name})",
                        color = Color(0xFFF57F17),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    state.computerHands.forEachIndexed { index, hand ->
                        Text("${seotdaOpponentNames[index]}: ${result.computerRanks[index].name}",
                            fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            hand.forEach { SeotdaCardView(it, compact = true) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("나 ${result.playerRank.name}",
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    if (result.outcome == SeotdaOutcome.Lose) {
                        Text(
                            "${result.playerPlacement}위 / ${state.playerCount}명",
                            color = color,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    Text("각 3장 중 가장 높은 2장 족보", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when (result.outcome) {
                            SeotdaOutcome.Win ->
                                "+${seotdaBetLabel(result.wager, result.betCurrency)}" +
                                if (result.premium > 0) "\n족보 보너스 +${result.premium.won()}" else ""
                            SeotdaOutcome.Draw -> "판돈 반환"
                            SeotdaOutcome.Lose -> "-${seotdaBetLabel(result.wager, result.betCurrency)}"
                        },
                        color = color, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            },
            confirmButton = {
                Button(onClick = viewModel::acknowledgeThreeCardSeotdaResult) { Text("확인") }
            },
        )
    }
}

@Composable
private fun PlayerCountSelector(selected: Int, onSelected: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("참가 인원", color = Color.White, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            (2..4).forEach { count ->
                FilterChip(
                    selected = selected == count,
                    onClick = { onSelected(count) },
                    modifier = Modifier.weight(1f),
                    label = {
                        Text("$count 명", Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF80CBC4),
                        selectedLabelColor = Color(0xFF102A24),
                        containerColor = Color.White,
                        labelColor = Color(0xFF102A24),
                    ),
                )
            }
        }
    }
}

@Composable
private fun SeotdaCardView(card: SeotdaCard, hidden: Boolean = false, compact: Boolean = false) {
    val width = if (compact) 58.dp else 76.dp
    val height = if (compact) 88.dp else 116.dp
    Card(
        modifier = Modifier.size(width, height),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(2.dp, if (hidden) Color(0xFFFFD54F) else Color(0xFF29170C)),
        colors = CardDefaults.cardColors(containerColor = if (hidden) Color(0xFF8E1020) else Color(0xFFFFF8E7)),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
    ) {
        if (hidden) {
            Box(
                Modifier.fillMaxSize().padding(5.dp)
                    .border(1.dp, Color(0xFFFFD54F), RoundedCornerShape(5.dp))
                    .background(Color(0xFF6D0D19), RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                    val gold = Color(0xFFFFD54F)
                    drawCircle(gold, size.minDimension * 0.34f, center, style = Stroke(size.minDimension * 0.035f))
                    drawCircle(gold, size.minDimension * 0.16f, center, style = Stroke(size.minDimension * 0.025f))
                    repeat(8) { index ->
                        val angle = Math.toRadians(index * 45.0)
                        val dx = kotlin.math.cos(angle).toFloat()
                        val dy = kotlin.math.sin(angle).toFloat()
                        drawLine(
                            gold,
                            center + Offset(dx, dy) * (size.minDimension * 0.2f),
                            center + Offset(dx, dy) * (size.minDimension * 0.38f),
                            strokeWidth = size.minDimension * 0.04f,
                        )
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                SeotdaCardArtwork(card, Modifier.fillMaxSize().padding(3.dp))
                Text("${card.month}월", Modifier.align(Alignment.TopStart).padding(4.dp),
                    color = Color(0xFF8B0000), fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelSmall)
                Text(if (card.bright) "광" else if (card.variant == 1) "띠" else "피",
                    Modifier.align(Alignment.BottomEnd).padding(5.dp),
                    color = if (card.bright) Color(0xFFE65100) else Color(0xFF4E342E),
                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SeotdaCardArtwork(card: SeotdaCard, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val green = Color(0xFF245B2A)
        val darkGreen = Color(0xFF173D20)
        val red = Color(0xFFC62828)
        val pink = Color(0xFFF48FB1)
        val purple = Color(0xFF7B1FA2)
        val yellow = Color(0xFFF9A825)
        val branch = Color(0xFF4E342E)

        fun stem(start: Offset, end: Offset, color: Color = branch, width: Float = w * 0.045f) {
            drawLine(color, start, end, strokeWidth = width)
        }
        fun flower(center: Offset, color: Color, radius: Float = w * 0.075f) {
            repeat(5) { index ->
                val angle = Math.toRadians(index * 72.0 - 90)
                drawCircle(
                    color,
                    radius,
                    center + Offset(
                        kotlin.math.cos(angle).toFloat() * radius,
                        kotlin.math.sin(angle).toFloat() * radius,
                    ),
                )
            }
            drawCircle(yellow, radius * 0.55f, center)
        }
        fun ribbon(y: Float, color: Color = red) {
            val path = Path().apply {
                moveTo(w * 0.12f, y)
                lineTo(w * 0.86f, y - h * 0.06f)
                lineTo(w * 0.78f, y + h * 0.08f)
                lineTo(w * 0.18f, y + h * 0.1f)
                close()
            }
            drawPath(path, color)
        }

        when (card.month) {
            1 -> {
                stem(Offset(w * .22f, h * .92f), Offset(w * .42f, h * .16f), darkGreen, w * .07f)
                repeat(6) { i ->
                    val y = h * (.22f + i * .1f)
                    stem(Offset(w * .38f, y), Offset(w * (.12f + (i % 2) * .48f), y - h * .08f), green, w * .035f)
                }
                if (card.bright) {
                    drawCircle(Color.White, w * .09f, Offset(w * .72f, h * .3f))
                    stem(Offset(w * .62f, h * .34f), Offset(w * .82f, h * .34f), Color.Black, w * .025f)
                    stem(Offset(w * .7f, h * .34f), Offset(w * .65f, h * .5f), Color.Black, w * .018f)
                    stem(Offset(w * .75f, h * .34f), Offset(w * .8f, h * .5f), Color.Black, w * .018f)
                }
            }
            2 -> {
                stem(Offset(w * .12f, h * .9f), Offset(w * .72f, h * .18f))
                repeat(6) { i -> flower(Offset(w * (.2f + (i % 3) * .24f), h * (.72f - i * .09f)), if (i % 2 == 0) red else pink) }
                if (card.variant == 1) ribbon(h * .48f)
            }
            3 -> {
                repeat(9) { i -> flower(Offset(w * (.14f + (i % 3) * .3f), h * (.22f + (i / 3) * .22f)), pink, w * .065f) }
                if (card.bright) ribbon(h * .58f, Color(0xFF5D4037))
            }
            4 -> {
                stem(Offset(w * .18f, h * .08f), Offset(w * .72f, h * .9f), darkGreen)
                repeat(8) { i -> drawCircle(purple, w * .055f, Offset(w * (.25f + (i % 3) * .2f), h * (.2f + i * .075f))) }
                if (card.variant == 1) ribbon(h * .62f)
            }
            5 -> {
                repeat(5) { i ->
                    stem(Offset(w * .12f + i * w * .16f, h * .9f), Offset(w * .22f + i * w * .12f, h * .2f), green)
                    val p = Path().apply {
                        moveTo(w * (.16f + i * .13f), h * .3f)
                        lineTo(w * (.25f + i * .13f), h * .14f)
                        lineTo(w * (.31f + i * .13f), h * .34f)
                        close()
                    }
                    drawPath(p, if (i % 2 == 0) purple else Color(0xFF3949AB))
                }
                if (card.variant == 1) ribbon(h * .56f)
            }
            6 -> {
                repeat(5) { i -> flower(Offset(w * (.2f + (i % 2) * .42f), h * (.24f + i * .14f)), red, w * .09f) }
                repeat(4) { i -> drawOval(green, Offset(w * (.12f + i * .2f), h * .7f), Size(w * .28f, h * .15f)) }
                if (card.variant == 1) ribbon(h * .5f, Color(0xFF1565C0))
            }
            7 -> {
                repeat(7) { i -> stem(Offset(w * (.08f + i * .13f), h * .9f), Offset(w * (.18f + i * .1f), h * .28f), green, w * .025f) }
                repeat(12) { i -> drawCircle(red, w * .035f, Offset(w * (.12f + (i % 4) * .22f), h * (.28f + (i / 4) * .16f))) }
                if (card.variant == 1) drawOval(Color(0xFF5D4037), Offset(w * .35f, h * .58f), Size(w * .48f, h * .23f))
            }
            8 -> {
                repeat(7) { i -> stem(Offset(w * (.1f + i * .13f), h * .92f), Offset(w * (.18f + i * .11f), h * .3f), Color(0xFF8D6E63), w * .022f) }
                if (card.bright) drawCircle(Color(0xFFFFE082), w * .22f, Offset(w * .68f, h * .27f))
            }
            9 -> {
                repeat(7) { i -> flower(Offset(w * (.16f + (i % 3) * .3f), h * (.24f + (i / 3) * .22f)), yellow, w * .065f) }
                if (card.variant == 1) {
                    drawOval(Color(0xFF263238), Offset(w * .28f, h * .58f), Size(w * .45f, h * .25f))
                    drawOval(Color(0xFFF5F5F5), Offset(w * .34f, h * .61f), Size(w * .33f, h * .14f))
                }
            }
            else -> {
                stem(Offset(w * .18f, h * .08f), Offset(w * .7f, h * .92f))
                repeat(9) { i ->
                    val leaf = if (i % 2 == 0) red else Color(0xFFFF6F00)
                    drawOval(leaf, Offset(w * (.08f + (i % 3) * .28f), h * (.18f + (i / 3) * .22f)), Size(w * .24f, h * .12f))
                }
                if (card.variant == 1) drawOval(Color(0xFF6D4C41), Offset(w * .44f, h * .55f), Size(w * .34f, h * .28f))
            }
        }
    }
}

@Composable
private fun SeotdaRankGuide() {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))) {
        Column(Modifier.padding(14.dp)) {
            Text("섯다 족보", fontWeight = FontWeight.ExtraBold)
            Text(
                "38광땡 > 18광땡 > 13광땡 > 장땡 > 9땡 … 1땡\n" +
                    "> 알리 > 독사 > 구삥 > 장삥 > 장사 > 세륙\n" +
                    "> 갑오(9끗) > 8끗 … 1끗 > 망통(0끗)\n" +
                    "땡잡이(3월+7월): 1땡~9땡을 잡으며 장땡·광땡은 잡지 못합니다.\n" +
                    "암행어사(4월+7월): 13·18광땡을 잡지만 38광땡은 잡지 못합니다.\n" +
                    "구사: 알리 이하 · 멍텅구리 구사: 장땡 이하일 때 재경기합니다.\n" +
                    "땡 승리: 스테이지 비용의 1.0~2.8% · 13·18광땡: 2% · 38광땡: 3% 보너스\n" +
                    "구사(9월+4월) 또는 같은 족보 무승부는 판수 차감 없이 재경기합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StoryView(viewModel: MainViewModel) {
    val context = LocalContext.current
    val user by viewModel.user.collectAsState()
    val currentChapter = user?.chapter ?: 1
    val completed = currentChapter > storyChapters.size
    val chapter = storyChapters.getOrNull(currentChapter - 1)
    val season = (currentChapter - 1).coerceAtLeast(0) / 10 + 1
    val episode = (currentChapter - 1).coerceAtLeast(0) % 10 + 1
    var showClearDialog by remember { mutableStateOf(false) }
    var storyTts by remember { mutableStateOf<TextToSpeech?>(null) }
    var storyTtsReady by remember { mutableStateOf(false) }
    var storyReading by remember { mutableStateOf(false) }
    val storyScrollState = rememberScrollState()

    DisposableEffect(context) {
        val mainHandler = Handler(Looper.getMainLooper())
        val engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                storyTts?.language = Locale.KOREAN
                storyTts?.setPitch(1.0f)
                storyTts?.setSpeechRate(0.92f)
                storyTtsReady = true
            }
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                mainHandler.post { storyReading = true }
            }

            override fun onDone(utteranceId: String?) {
                mainHandler.post { storyReading = false }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post { storyReading = false }
            }
        })
        storyTts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            storyTts = null
            storyTtsReady = false
            storyReading = false
        }
    }

    LaunchedEffect(currentChapter) {
        storyTts?.stop()
        storyReading = false
        storyScrollState.scrollTo(0)
    }

    Page(backgroundRes = R.drawable.story_background, backgroundAlpha = 0.5f) {
        Column(Modifier.fillMaxSize().verticalScroll(storyScrollState)) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.94f)),
            ) {
                Column(Modifier.padding(20.dp)) {
                    if (completed) {
                        Text("STORY 5 COMPLETE", color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(8.dp))
                        Text("우리가 이기는 방식", style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(12.dp))
                        Text("다섯 개의 스토리와 50개의 챕터를 모두 완료했습니다.\n한 번 패배했던 친구들은 결국 함께 이기는 방법을 찾아냈습니다.")
                    } else if (chapter != null) {
                        Text(
                            "STORY $season · CHAPTER $episode / 10",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(chapter.title, style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold)
                        Text(chapter.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                if (storyReading) {
                                    storyTts?.stop()
                                    storyReading = false
                                } else {
                                    val storyText = buildString {
                                        append(chapter.title)
                                        append(". ")
                                        append(chapter.subtitle)
                                        append(". ")
                                        append(renderStoryForUser(chapter.story, user?.userId.orEmpty()))
                                    }
                                    storyTts?.speak(
                                        storyText,
                                        TextToSpeech.QUEUE_FLUSH,
                                        null,
                                        "story_chapter_${chapter.number}",
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = storyTtsReady,
                        ) {
                            Text(if (storyReading) "■ 읽기 중지" else "▶ 스토리 읽어주기")
                        }
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(14.dp))
                        Text(
                            renderStoryForUser(chapter.story, user?.userId.orEmpty()),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(18.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                Text("클리어 조건", fontWeight = FontWeight.ExtraBold)
                                Text("클리어 비용 ${chapter.clearCost.won()} · 상한의 ${String.format(Locale.KOREA, "%.1f", stageClearPercent(chapter.number))}%")
                                Text("현재 재화 ${(user?.money ?: 0L).won()}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = (user?.money ?: 0L) >= chapter.clearCost,
                        ) {
                            Text(
                                if (chapter.number == storyChapters.size) "스토리 $season 최종 챕터 클리어"
                                else if (episode == 10) "스토리 $season 완료하고 다음 스토리 보기"
                                else "클리어하고 다음 스토리 보기",
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog && chapter != null) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("STORY $season · CHAPTER $episode 클리어") },
            text = {
                Text(
                    "${chapter.clearCost.won()}을 사용해 이 챕터를 클리어할까요?\n" +
                        if (chapter.number < storyChapters.size) "다음 이야기가 바로 열립니다."
                        else "플레이어와 무릉도원 친구들의 반격 이야기가 완결됩니다.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.clearStoryChapter(chapter.number, chapter.clearCost)
                    showClearDialog = false
                }) { Text("클리어") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun SettingsView(
    userId: String,
    viewModel: MainViewModel,
    bgmEnabled: Boolean,
    bgmVolume: Float,
    onBgmEnabledChange: (Boolean) -> Unit,
    onBgmVolumeChange: (Float) -> Unit,
    openDbEditor: Boolean,
    onDbEditorClosed: () -> Unit,
    onLogout: () -> Unit,
    onSwitchAccount: (String) -> Unit,
) {
    val accounts by viewModel.accounts.collectAsState()
    val user by viewModel.user.collectAsState()
    var showAccounts by remember { mutableStateOf(false) }
    var showDbEditor by remember { mutableStateOf(false) }
    var showSeotdaNames by remember { mutableStateOf(false) }
    var showCompletedStories by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var replayChapter by remember { mutableStateOf<StoryChapter?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var seotdaName1 by remember { mutableStateOf("") }
    var seotdaName2 by remember { mutableStateOf("") }
    var seotdaName3 by remember { mutableStateOf("") }
    var dbColumn by remember { mutableStateOf("") }
    var dbValue by remember { mutableStateOf("") }
    LaunchedEffect(openDbEditor) {
        if (openDbEditor) showDbEditor = true
    }
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.settings_background),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.5f,
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        ) {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) {
                Text(
                    "현재 계정",
                    modifier = Modifier.padding(vertical = 3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(userId, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("배경 음악", fontWeight = FontWeight.Bold)
                        Text(
                            if (bgmEnabled) "화면에 어울리는 음악을 재생합니다." else "배경 음악이 꺼져 있습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = bgmEnabled, onCheckedChange = onBgmEnabledChange)
                }
                Text("음량 ${(bgmVolume * 100).roundToLong()}%", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = bgmVolume,
                    onValueChange = onBgmVolumeChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = bgmEnabled,
                    valueRange = 0f..1f,
                )
                HorizontalDivider(); Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showCompletedStories = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = (user?.chapter ?: 1) > 1,
                ) {
                    Text(
                        if ((user?.chapter ?: 1) > 1) "클리어한 스토리 다시보기"
                        else "클리어한 스토리가 없습니다",
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button({ showAccounts = true }, Modifier.fillMaxWidth()) { Text("부캐로 이동") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        seotdaName1 = user?.seotdaName1 ?: "졸린"
                        seotdaName2 = user?.seotdaName2 ?: "토끼"
                        seotdaName3 = user?.seotdaName3 ?: "콜라"
                        showSeotdaNames = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("섯다 상대 이름 설정") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showPrivacyPolicy = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("개인정보 처리 안내") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onLogout, Modifier.fillMaxWidth()) { Text("로그아웃") }
            }}
        }
    }

    if (showPrivacyPolicy) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicy = false },
            title = { Text("개인정보 처리 안내") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("처리하는 정보", fontWeight = FontWeight.Bold)
                    Text("사용자가 입력한 캐릭터 아이디와 게임 진행 정보는 기기 내부에 저장됩니다. 걸음 퀘스트를 사용할 때 Health Connect에서 일별 걸음 수를 읽습니다.")
                    Spacer(Modifier.height(10.dp))
                    Text("이용 목적", fontWeight = FontWeight.Bold)
                    Text("캐릭터와 게임 진행 상태 저장, 걸음 퀘스트 달성 여부 및 보상 계산에만 사용합니다.")
                    Spacer(Modifier.height(10.dp))
                    Text("보관과 제공", fontWeight = FontWeight.Bold)
                    Text("정보는 외부 서버로 전송하거나 제3자에게 제공하지 않으며 앱 데이터 삭제 또는 캐릭터 삭제 시 기기에서 삭제됩니다. Health Connect 권한은 기기 설정에서 언제든 철회할 수 있습니다.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyPolicy = false }) { Text("확인") }
            },
        )
    }

    if (showCompletedStories) {
        val completedChapters = storyChapters.take(
            ((user?.chapter ?: 1) - 1).coerceIn(0, storyChapters.size),
        )
        AlertDialog(
            onDismissRequest = { showCompletedStories = false },
            title = { Text("클리어한 스토리") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    completedChapters.forEach { completedChapter ->
                        val storyNumber = (completedChapter.number - 1) / 10 + 1
                        val chapterNumber = (completedChapter.number - 1) % 10 + 1
                        OutlinedButton(
                            onClick = {
                                replayChapter = completedChapter
                                showCompletedStories = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(
                                    "STORY $storyNumber · CHAPTER $chapterNumber",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    completedChapter.title,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCompletedStories = false }) { Text("닫기") }
            },
        )
    }

    replayChapter?.let { selectedChapter ->
        val storyNumber = (selectedChapter.number - 1) / 10 + 1
        val chapterNumber = (selectedChapter.number - 1) % 10 + 1
        Dialog(
            onDismissRequest = { replayChapter = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF7)),
            ) {
                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    Text(
                        "STORY $storyNumber · CHAPTER $chapterNumber",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        selectedChapter.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        selectedChapter.subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Text(
                        renderStoryForUser(selectedChapter.story, user?.userId.orEmpty()),
                        modifier = Modifier.weight(1f).fillMaxWidth()
                            .verticalScroll(rememberScrollState()).padding(vertical = 14.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(
                        onClick = { replayChapter = null },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("닫기") }
                }
            }
        }
    }

    if (showAccounts) {
        AlertDialog(
            onDismissRequest = { showAccounts = false },
            containerColor = Color(0xFFFFFCFF),
            title = {
                Text(
                    "계정 변경",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.ExtraBold,
                )
            },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    accounts.forEach { account ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (account.userId != userId) {
                                    showAccounts = false
                                    onSwitchAccount(account.userId)
                                }
                            },
                            border = BorderStroke(
                                1.dp,
                                if (account.userId == userId) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (account.userId == userId) Color(0xFFF3EDFF)
                                else Color.White,
                            ),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("계정", modifier = Modifier.fillMaxWidth(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center)
                                        Text(account.userId, modifier = Modifier.fillMaxWidth(),
                                            fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                                    }
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("챕터", modifier = Modifier.fillMaxWidth(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center)
                                        Text("${account.chapter}", modifier = Modifier.fillMaxWidth(),
                                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                    }
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("보유재화", modifier = Modifier.fillMaxWidth(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center)
                                        Text(account.money.won(), modifier = Modifier.fillMaxWidth(),
                                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                                            maxLines = 1)
                                    }
                                }
                                if (account.userId != userId) {
                                    TextButton(
                                        onClick = { deleteTarget = account.userId },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error,
                                        ),
                                    ) { Text("캐릭터 삭제") }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAccounts = false }) { Text("닫기") }
            },
        )
    }

    if (showSeotdaNames) {
        val names = listOf(seotdaName1.trim(), seotdaName2.trim(), seotdaName3.trim())
        val valid = names.all { it.isNotBlank() && it.length <= 10 } && names.distinct().size == 3
        AlertDialog(
            onDismissRequest = { showSeotdaNames = false },
            title = { Text("섯다 상대 이름 설정") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("이름은 현재 계정에만 저장됩니다.", style = MaterialTheme.typography.bodySmall)
                    listOf(
                        seotdaName1 to { value: String -> seotdaName1 = value },
                        seotdaName2 to { value: String -> seotdaName2 = value },
                        seotdaName3 to { value: String -> seotdaName3 = value },
                    ).forEachIndexed { index, (value, update) ->
                        OutlinedTextField(
                            value = value,
                            onValueChange = { update(it.take(10)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("상대 ${index + 1} 이름") },
                            singleLine = true,
                        )
                    }
                    if (!valid) {
                        Text("서로 다른 이름을 1~10자로 입력하세요.",
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateSeotdaNames(names)
                        showSeotdaNames = false
                    },
                    enabled = valid,
                ) { Text("저장") }
            },
            dismissButton = {
                TextButton(onClick = { showSeotdaNames = false }) { Text("취소") }
            },
        )
    }

    deleteTarget?.let { accountId ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("캐릭터 삭제") },
            text = { Text("'$accountId' 캐릭터를 DB에서 완전히 삭제할까요?\n삭제한 데이터는 복구할 수 없습니다.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCharacter(accountId)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("취소") }
            },
        )
    }

    if (BuildConfig.DEBUG && showDbEditor) {
        val normalizedColumn = dbColumn.trim().lowercase()
        val dbFields = listOf(
            "money" to (user?.money ?: 0L).toString(),
            "chapter" to (user?.chapter ?: 1).toString(),
            "gender" to (user?.gender ?: "남성"),
        )
        val validValue = when (normalizedColumn) {
            "money" -> dbValue.toLongOrNull()?.let { it >= 0 } == true
            "chapter" -> dbValue.toIntOrNull()?.let { it >= 1 } == true
            "gender" -> dbValue == "남성" || dbValue == "여성"
            else -> false
        }
        AlertDialog(
            onDismissRequest = {
                showDbEditor = false
                onDbEditorClosed()
            },
            containerColor = Color.White,
            title = { Text("Room DB 수정") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "현재 계정: $userId\n변경할 컬럼을 눌러 선택하세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        dbFields.forEach { (column, currentValue) ->
                            val selected = normalizedColumn == column
                            OutlinedButton(
                                onClick = {
                                    dbColumn = column
                                    dbValue = currentValue
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Transparent
                                    },
                                    contentColor = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        column,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                    )
                                    Text(
                                        currentValue,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = dbValue,
                        onValueChange = { dbValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = dbColumn.isNotBlank(),
                        label = {
                            Text(
                                if (dbColumn.isBlank()) "먼저 컬럼을 선택하세요"
                                else "$dbColumn 데이터 값",
                            )
                        },
                        singleLine = true,
                    )
                    if (dbColumn.isNotBlank() && !validValue) {
                        Text(
                            "money는 0 이상, chapter는 1 이상, gender는 남성/여성만 입력할 수 있습니다.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateRoomField(dbColumn, dbValue)
                        dbColumn = ""
                        dbValue = ""
                        showDbEditor = false
                        onDbEditorClosed()
                    },
                    enabled = validValue,
                ) { Text("변경") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDbEditor = false
                    onDbEditorClosed()
                }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun MenuCard(title: String, detail: String) {
    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) { Column(Modifier.padding(18.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }}
}
