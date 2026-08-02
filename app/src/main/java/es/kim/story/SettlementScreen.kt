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
internal fun SettlementView(viewModel: MainViewModel) {
    val context = LocalContext.current
    val state by viewModel.stepQuestState.collectAsState()
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
                StepQuestCard(quest, state.dailySteps, state, economyPercentReward(quest.rewardPercent)) {
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
                StepQuestCard(quest, state.weeklySteps, state, economyPercentReward(quest.rewardPercent)) {
                    moreRewardApplied = false
                    settlementQuest = quest
                }
            }
        }
    }

    settlementQuest?.let { quest ->
        val chapterReward = economyPercentReward(quest.rewardPercent)
        val settlementReward = if (moreRewardApplied) chapterReward + chapterReward / 2 else chapterReward
        Dialog(onDismissRequest = { settlementQuest = null }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            ) {
                Box {
                    Image(
                        painter = painterResource(R.drawable.story_background),
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

internal const val NAVER_MAP_PACKAGE = "com.nhn.android.nmap"

@Composable
internal fun StepQuestCard(
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
                painter = painterResource(R.drawable.running_background),
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
internal fun RewardClaimButton(
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

internal fun Long.formattedNumber(): String = NumberFormat.getNumberInstance(Locale.KOREA).format(this)
