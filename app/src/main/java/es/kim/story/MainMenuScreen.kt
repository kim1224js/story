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
import androidx.compose.foundation.horizontalScroll
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

fun gameSpeechParams(volume: Float) = Bundle().apply {
    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f))
}

private enum class MainMenu(val label: String, val icon: String, val title: String, val description: String) {
    Work("알바", "💼", "알바", "한 번에 한 개의 알바만 진행할 수 있어요"),
    Settlement("런닝", "👟", "런닝하기", "걸음 수를 채우고 보상을 받는 공간이에요"),
    Gamble("게임", "🎲", "미니게임", "게임 머니로 즐기는 카드와 승부 게임"),
    Puzzle("퍼즐", "🧩", "퍼즐", "블록을 맞추고 연쇄 콤보에 도전해 보세요"),
    Stock("주식", "📈", "주식", "5분마다 움직이는 가상 주식 시장이에요"),
    Apartment("아파트", "🏙️", "서울 아파트", "서울 25개 자치구의 아파트를 모아 보세요"),
    Rpg("RPG", "⚔️", "원정 RPG", "캐릭터를 성장시키고 장비를 강화하세요"),
    Settings("설정", "⚙", "설정", "로컬 프로필과 캐릭터를 관리하세요"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainMenuScreen(
    userId: String,
    viewModel: MainViewModel,
    bgmEnabled: Boolean,
    bgmVolume: Float,
    masterVolume: Float,
    gameSoundEnabled: Boolean,
    gameSoundVolume: Float,
    onBgmEnabledChange: (Boolean) -> Unit,
    onBgmVolumeChange: (Float) -> Unit,
    onMasterVolumeChange: (Float) -> Unit,
    onGameSoundEnabledChange: (Boolean) -> Unit,
    onGameSoundVolumeChange: (Float) -> Unit,
    ttsSettings: AppTtsSettings,
    onTtsSettingsChange: (AppTtsSettings) -> Unit,
    onBgmTrackChange: (Int) -> Unit,
    onLogout: () -> Unit,
    onSwitchAccount: (String) -> Unit,
) {
    var selected by remember { mutableStateOf(MainMenu.Apartment) }
    var allowLoginStockNewsPopup by remember(userId) { mutableStateOf(true) }
    val user by viewModel.user.collectAsState()
    val stockState by viewModel.stockState.collectAsState()
    val rpgState by viewModel.rpgState.collectAsState()
    val ownedApartments = remember(user?.ownedApartmentDistricts) {
        user?.ownedApartmentDistricts.orEmpty().split(',').filter(String::isNotBlank)
    }
    LaunchedEffect(stockState.quotes, stockState.pendingBreakingNews) {
        if (stockState.quotes.isNotEmpty() && stockState.pendingBreakingNews.isEmpty()) {
            allowLoginStockNewsPopup = false
        }
    }
    LaunchedEffect(selected) {
        onBgmTrackChange(
            when (selected) {
                MainMenu.Work -> R.raw.bgm_jaunt
                MainMenu.Gamble -> R.raw.bgm_bells_of_winter
                MainMenu.Puzzle, MainMenu.Stock, MainMenu.Apartment, MainMenu.Rpg -> R.raw.bgm_fairy_lights
                MainMenu.Settlement, MainMenu.Settings -> R.raw.bgm_fairy_lights
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
                                    selectedTitle = user?.selectedTitle.orEmpty(),
                                    modifier = Modifier.fillMaxWidth(),
                                    onChangeGender = { viewModel.changeGender(user?.gender ?: "남성") },
                                )
                            }
                            NotebookHeaderCard(
                                modifier = Modifier.weight(1f),

                                containerColor = Color(0xFFFCE4EC),
                                accentColor = Color(0xFFD81B60),
                            ) {
                                HeaderValue(
                                    "아파트 월세",
                                    "시간당 ${formatGameCurrency(apartmentHourlyRent(ownedApartments))}",
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
                                if (selected == MainMenu.Rpg) {
                                    "RPG 공용지갑 ${formatGameCurrency(rpgState.walletBalance)}"
                                } else {
                                    "${selected.title} - ${selected.description}"
                                },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF263238),
                                textAlign = TextAlign.Start,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            BottomMenuBar(
                selected = selected,
                onSelected = {
                    selected = it
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selected) {
                MainMenu.Work -> WorkView(viewModel, onBgmTrackChange)
                MainMenu.Settlement -> SettlementView(viewModel)
                MainMenu.Gamble -> GambleView(viewModel)
                MainMenu.Puzzle -> PuzzleView(viewModel)
                MainMenu.Stock -> StockView(viewModel)
                MainMenu.Apartment -> ApartmentView(viewModel)
                MainMenu.Rpg -> RpgView(viewModel)
                MainMenu.Settings -> SettingsView(
                    userId = userId,
                    viewModel = viewModel,
                    bgmEnabled = bgmEnabled,
                    bgmVolume = bgmVolume,
                    masterVolume = masterVolume,
                    gameSoundEnabled = gameSoundEnabled,
                    gameSoundVolume = gameSoundVolume,
                    onBgmEnabledChange = onBgmEnabledChange,
                    onBgmVolumeChange = onBgmVolumeChange,
                    onMasterVolumeChange = onMasterVolumeChange,
                    onGameSoundEnabledChange = onGameSoundEnabledChange,
                    onGameSoundVolumeChange = onGameSoundVolumeChange,
                    ttsSettings = ttsSettings,
                    onTtsSettingsChange = onTtsSettingsChange,
                    onLogout = onLogout,
                    onSwitchAccount = onSwitchAccount,
                )
            }
        }
    }
    stockState.pendingBreakingNews.firstOrNull()
        ?.takeIf { allowLoginStockNewsPopup || selected == MainMenu.Stock }
        ?.let { breaking ->
        AlertDialog(
            onDismissRequest = {},
            icon = { Text("🚨", style = MaterialTheme.typography.displaySmall) },
            title = { Text("보유 주식 속보", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${breaking.stockName} · ${breaking.stockId}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(breaking.title, color = Color(0xFFC62828), fontWeight = FontWeight.ExtraBold)
                    Text(breaking.summary)
                    Text(
                        "${"%+.2f".format(Locale.KOREAN, breaking.changePercent)}%",
                        color = if (breaking.changePercent >= 0) Color(0xFFD32F2F) else Color(0xFF1976D2),
                        fontWeight = FontWeight.Black,
                    )
                    if (stockState.pendingBreakingNews.size > 1) {
                        Text("확인하지 않은 속보 ${stockState.pendingBreakingNews.size}건",
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    allowLoginStockNewsPopup = false
                    viewModel.acknowledgeStockBreakingNews()
                    selected = MainMenu.Stock
                }) { Text("주식 탭 보기") }
            },
            dismissButton = {
                TextButton(onClick = {
                    allowLoginStockNewsPopup = false
                    viewModel.acknowledgeStockBreakingNews()
                }) { Text("확인") }
            },
        )
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
            fontWeight = FontWeight.Bold, textAlign = alignment, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun IdentityHeader(
    userId: String,
    gender: String,
    selectedTitle: String,
    modifier: Modifier,
    onChangeGender: () -> Unit,
) {
    val gameMaster = selectedTitle == TITLE_GAME_MASTER
    val realEstateMaster = selectedTitle == TITLE_REAL_ESTATE_MASTER
    val hero = selectedTitle == TITLE_HERO
    Column(modifier) {
        if (hero) {
            Surface(
                color = Color(0xFF311B5B),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, Color(0xFFCE93D8)),
                shadowElevation = 3.dp,
            ) {
                Text(
                    "⚔️ [용사]",
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    color = Color(0xFFE1BEE7),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
            }
        }
        if (gameMaster || realEstateMaster) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (realEstateMaster) Color(0xFF062F2B) else Color(0xFF251207),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(
                        1.dp,
                        if (realEstateMaster) Color(0xFF80CBC4) else Color(0xFFFFC107),
                    ),
                    shadowElevation = 3.dp,
                ) {
                    Text(
                        if (realEstateMaster) "♛ 부동산 마스터 ♛" else "♠ 게임 마스터 ♠",
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        color = if (realEstateMaster) Color(0xFFA7FFEB) else Color(0xFFFFD54F),
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
                style = if (gameMaster || realEstateMaster) {
                    MaterialTheme.typography.bodyMedium.copy(
                        brush = Brush.linearGradient(
                            if (realEstateMaster) {
                                listOf(
                                    Color(0xFF004D40),
                                    Color(0xFF00A896),
                                    Color(0xFF64FFDA),
                                    Color(0xFF00796B),
                                )
                            } else {
                                listOf(
                                    Color(0xFF7A3E00),
                                    Color(0xFFFFB300),
                                    Color(0xFFFFE082),
                                    Color(0xFFD06B00),
                                )
                            },
                        ),
                    )
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
    var showIncrease by remember { mutableStateOf(false) }

    LaunchedEffect(money) {
        if (money == previousMoney) return@LaunchedEffect
        val difference = money - previousMoney
        previousMoney = money
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

    Column(
        modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.CenterEnd) {
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
                overflow = TextOverflow.Ellipsis,
            )
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
private fun BottomMenuBar(
    selected: MainMenu,
    onSelected: (MainMenu) -> Unit,
) {
    val barColor = Color(0xFFFFF4C2)
    val selectedColor = Color(0xFFFFD95A)
    val dividerColor = Color(0xFFD7C777)
    val visibleMenus = MainMenu.entries
    Surface(color = barColor, shadowElevation = 10.dp, tonalElevation = 0.dp) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            visibleMenus.forEachIndexed { index, menu ->
                Column(
                    Modifier.width(68.dp).clickable { onSelected(menu) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.size(36.dp).background(
                            if (selected == menu) selectedColor else Color.Transparent,
                            RoundedCornerShape(12.dp),
                        ), contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            menu.icon,
                            style = MaterialTheme.typography.titleMedium,
                            color = LocalContentColor.current,
                        )
                    }
                    Text(menu.label, style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected == menu) FontWeight.Bold else FontWeight.Normal,
                        color = LocalContentColor.current,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (index < visibleMenus.lastIndex) {
                    VerticalDivider(Modifier.height(34.dp), thickness = 1.dp, color = dividerColor)
                }
            }
        }
    }
}

@Composable
internal fun Page(
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
