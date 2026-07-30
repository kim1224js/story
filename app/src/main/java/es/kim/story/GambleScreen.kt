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
import kotlin.math.roundToLong
import kotlin.random.Random
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import java.util.Random as JavaRandom

@Composable
internal fun GambleView(viewModel: MainViewModel) {
    val context = LocalContext.current
    val gameAudioVolume = LocalGameAudioVolume.current
    val ttsSettings = LocalTtsSettings.current
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    val gameLabels = listOf("가위바위보", "1대1 카드", "3장 카드", "교환소")
    var gambleTts by remember { mutableStateOf<TextToSpeech?>(null) }
    var gambleTtsReady by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                gambleTts?.language = Locale.KOREAN
                gambleTts?.let { configureAppTts(it, ttsSettings, TtsRole.Guide) }
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
        if (gambleTtsReady && gameAudioVolume > 0f && ttsSettings.enabled) {
            gambleTts?.let { configureAppTts(it, ttsSettings, TtsRole.Guide) }
            gambleTts?.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                gameSpeechParams(gameAudioVolume),
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
internal fun GambleShopView(viewModel: MainViewModel) {
    val context = LocalContext.current
    val gameAudioVolume = LocalGameAudioVolume.current
    val ttsSettings = LocalTtsSettings.current
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
                        if (money < exchangeCost) {
                            Text(
                                "게임 머니가 부족합니다.",
                                color = Color(0xFFFFCC80),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                viewModel.exchangeBlueChip {
                                    shopSoundPool.play(exchangeSound, gameAudioVolume, gameAudioVolume, 1, 0, 1f)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = money >= exchangeCost,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1976D2),
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFF455A64),
                                disabledContentColor = Color.White,
                            ),
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
                        if (blueChips < 1L) {
                            Text(
                                "보유한 블루칩이 없습니다.",
                                color = Color(0xFFFFCC80),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (money > MAX_PLAYER_MONEY - sellValue) {
                            Text(
                                "보유 게임 머니가 ${(MAX_PLAYER_MONEY - sellValue).won()} 이하일 때 바꿀 수 있어요.",
                                color = Color(0xFFFFCC80),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                viewModel.sellBlueChip {
                                    shopSoundPool.play(exchangeSound, gameAudioVolume, gameAudioVolume, 1, 0, 0.92f)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = blueChips >= 1L && money <= MAX_PLAYER_MONEY - sellValue,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00838F),
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFF455A64),
                                disabledContentColor = Color.White,
                            ),
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
internal fun RpsGambleView(viewModel: MainViewModel, speakResult: (String) -> Unit) {
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
                    Text("보유한 게임 머니보다 많이 걸 수 없습니다.", color = Color(0xFFFF8A80),
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
                    Text("결과는 게임 머니에 이미 반영되었습니다.",
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
internal fun SeotdaView(viewModel: MainViewModel, speakResult: (String) -> Unit) {
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
                                "순이익 +${seotdaBetLabel(GambleManager.seotdaNetProfit(result.wager, result.playerCount), result.betCurrency)}" +
                                "\n전체 팟 ${seotdaBetLabel(GambleManager.seotdaTotalPot(result.wager, result.playerCount), result.betCurrency)}" +
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
internal fun ThreeCardSeotdaView(viewModel: MainViewModel, speakResult: (String) -> Unit) {
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
                                "순이익 +${seotdaBetLabel(GambleManager.seotdaNetProfit(result.wager, result.playerCount), result.betCurrency)}" +
                                "\n전체 팟 ${seotdaBetLabel(GambleManager.seotdaTotalPot(result.wager, result.playerCount), result.betCurrency)}" +
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
internal fun PlayerCountSelector(selected: Int, onSelected: (Int) -> Unit) {
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
internal fun SeotdaCardView(card: SeotdaCard, hidden: Boolean = false, compact: Boolean = false) {
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
                Text(
                    when {
                        card.bright -> "광"
                        card.animal -> "열끗"
                        card.variant == 1 -> "띠"
                        else -> "피"
                    },
                    Modifier.align(Alignment.BottomEnd).padding(5.dp),
                    color = if (card.bright) Color(0xFFE65100) else Color(0xFF4E342E),
                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
internal fun SeotdaCardArtwork(card: SeotdaCard, modifier: Modifier = Modifier) {
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
                if (card.animal) {
                    drawOval(Color(0xFF5D4037), Offset(w * .34f, h * .52f), Size(w * .42f, h * .24f))
                    drawCircle(Color.White, w * .035f, Offset(w * .64f, h * .57f))
                } else if (card.variant == 1) ribbon(h * .62f)
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
                if (card.animal) {
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
internal fun SeotdaRankGuide() {
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
