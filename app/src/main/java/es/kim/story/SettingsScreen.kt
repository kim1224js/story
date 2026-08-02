package es.kim.story

import android.app.Activity
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
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.android.gms.games.PlayGames
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
internal fun AudioVolumeControl(
    icon: String,
    title: String,
    description: String,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    enabled: Boolean = true,
    switchChecked: Boolean? = null,
    onSwitchChange: ((Boolean) -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.ExtraBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (switchChecked != null && onSwitchChange != null) {
            Switch(checked = switchChecked, onCheckedChange = onSwitchChange)
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = volume,
            onValueChange = onVolumeChange,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            valueRange = 0f..1f,
        )
        Text(
            "${(volume * 100).roundToLong()}%",
            modifier = Modifier.padding(start = 10.dp).widthIn(min = 42.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}
@Composable
internal fun SettingsView(
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
    onLogout: () -> Unit,
    onSwitchAccount: (String) -> Unit,
) {
    val accounts by viewModel.accounts.collectAsState()
    val user by viewModel.user.collectAsState()
    var showAccounts by remember { mutableStateOf(false) }
    var showSeotdaNames by remember { mutableStateOf(false) }
    var showCompletedStories by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showCouponInput by remember { mutableStateOf(false) }
    var couponCode by remember { mutableStateOf("") }
    var couponResult by remember { mutableStateOf<CouponRedeemResult?>(null) }
    var couponReward by remember { mutableStateOf<Long?>(null) }
    var couponSubmitting by remember { mutableStateOf(false) }
    var replayChapter by remember { mutableStateOf<StoryChapter?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var seotdaName1 by remember { mutableStateOf("") }
    var seotdaName2 by remember { mutableStateOf("") }
    var seotdaName3 by remember { mutableStateOf("") }
    val ownedTitleIds = ownedPlayerTitleIds(user)

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.settings_background),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.62f,
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color(0x99FFF9ED),
                        Color(0xCCF7F3E8),
                        Color(0xE6FFFDF7),
                    ),
                ),
            ),
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xF2FFFDF7)),
                border = BorderStroke(1.dp, Color(0x55896F47)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFFE8F2D7),
                        border = BorderStroke(1.dp, Color(0xFFB6C99A)),
                    ) {
                        Text(
                            "⚙",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "설정",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF3E4D2D),
                        )
                        Text(
                            "무릉도원에서의 여정을 내게 맞게 조정해요",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF6C725F),
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xF7FFFFFF)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("🌿", style = MaterialTheme.typography.headlineSmall)
                    Column(Modifier.weight(1f)) {
                        Text(
                            "현재 프로필",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF6A7A54),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            userId,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "이 기기에 안전하게 저장되어 있어요",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                "소리",
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF4E5E3E),
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xF7FFFFFF)),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AudioVolumeControl(
                        icon = "🔊",
                        title = "전체 볼륨",
                        description = "배경음악과 게임 소리를 한 번에 조절해요",
                        volume = masterVolume,
                        onVolumeChange = onMasterVolumeChange,
                    )
                    HorizontalDivider(color = Color(0xFFE6E3DA))
                    AudioVolumeControl(
                        icon = if (bgmEnabled) "🎵" else "🔇",
                        title = "배경음악",
                        description = if (bgmEnabled) "장면에 어울리는 음악" else "배경음악이 꺼져 있어요",
                        volume = bgmVolume,
                        onVolumeChange = onBgmVolumeChange,
                        enabled = bgmEnabled,
                        switchChecked = bgmEnabled,
                        onSwitchChange = onBgmEnabledChange,
                    )
                    HorizontalDivider(color = Color(0xFFE6E3DA))
                    AudioVolumeControl(
                        icon = if (gameSoundEnabled) "🎮" else "🔇",
                        title = "게임 효과음·음성",
                        description = if (gameSoundEnabled) "퍼즐, 얼음깨기, 카드게임 등의 소리" else "게임 소리가 꺼져 있어요",
                        volume = gameSoundVolume,
                        onVolumeChange = onGameSoundVolumeChange,
                        enabled = gameSoundEnabled,
                        switchChecked = gameSoundEnabled,
                        onSwitchChange = onGameSoundEnabledChange,
                    )
                    HorizontalDivider(color = Color(0xFFE6E3DA))
                    TtsSettingsPanel(
                        settings = ttsSettings,
                        onSettingsChange = onTtsSettingsChange,
                    )
                }
            }
            Text(
                "게임 관리",
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF4E5E3E),
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xF7FFFFFF)),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text("🏅 보유 칭호 변경", fontWeight = FontWeight.ExtraBold)
                    Text(
                        "선택한 칭호는 프로필 아이디 위에 표시됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        playerTitles.filter { it.id in ownedTitleIds }.forEach { title ->
                            FilterChip(
                                selected = user?.selectedTitle.orEmpty() == title.id,
                                onClick = { viewModel.selectPlayerTitle(title.id) },
                                label = { Text("${title.icon} ${title.label}") },
                            )
                        }
                    }
                    HorizontalDivider(color = Color(0xFFE6E3DA))

                    OutlinedButton(
                        onClick = {
                            couponCode = ""
                            couponResult = null
                            couponReward = null
                            couponSubmitting = false
                            showCouponInput = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Text(
                            "🎁  쿠폰 입력",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    HorizontalDivider(color = Color(0xFFE6E3DA))

                    // =========================================================
                    // 🏆 구글 플레이 랭킹보기 버튼 추가
                    // =========================================================
                    val context = LocalContext.current
                    val leaderboardLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult(),
                    ) { result ->
                        Log.d("PlayGames", "리더보드 화면 종료: resultCode=${result.resultCode}")
                    }

                    Button(
                        onClick = {
                            val activity = (context as? Activity) ?: run {
                                Toast.makeText(context, "Activity 컨텍스트를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            // Play Console에 등록된 ID를 strings.xml 한 곳에서만 관리한다.
                            // 기존 하드코딩 값에는 'J'가 하나 더 들어가 있어 화면 요청이 실패했다.
                            val leaderboardId = context.getString(R.string.leaderboard_id)
                            val gamesSignInClient = PlayGames.getGamesSignInClient(activity)
                            val leaderboardsClient = PlayGames.getLeaderboardsClient(activity)

                            fun openLeaderboard() {
                                user?.let { currentUser ->
                                    val totalScore = viewModel.calculateTotalScore(currentUser)
                                    leaderboardsClient.submitScore(leaderboardId, totalScore)
                                }

                                leaderboardsClient.getLeaderboardIntent(leaderboardId)
                                    .addOnSuccessListener { intent ->
                                        // Play Games UI는 호출 앱의 신원 확인을 위해
                                        // 반드시 Activity Result 방식으로 실행해야 한다.
                                        leaderboardLauncher.launch(intent)
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("PlayGames", "리더보드 화면 실행 실패", e)
                                        Toast.makeText(context, "랭킹을 불러올 수 없습니다: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                            }

                            gamesSignInClient.isAuthenticated.addOnCompleteListener { task ->
                                val isAuthenticated = task.isSuccessful && task.result.isAuthenticated
                                Log.d("PlayGames", "로그인 상태: $isAuthenticated")

                                if (isAuthenticated) {
                                    openLeaderboard()
                                } else {
                                    gamesSignInClient.signIn().addOnCompleteListener { signInTask ->
                                        if (signInTask.isSuccessful && signInTask.result.isAuthenticated) {
                                            openLeaderboard()
                                        } else {
                                            Log.e("PlayGames", "Play Games 로그인 실패", signInTask.exception)
                                            Toast.makeText(context, "Play Games 로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    ) {
                        Text("🏆 전체 사용자 랭킹 보기")
                    }



                    HorizontalDivider(color = Color(0xFFE6E3DA))

                    OutlinedButton(
                        onClick = { showCompletedStories = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = (user?.chapter ?: 1) > 1,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Text(
                            if ((user?.chapter ?: 1) > 1) "📖  클리어한 스토리 다시보기"
                            else "📖  아직 클리어한 스토리가 없어요",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    OutlinedButton(
                        onClick = { showCompletedStories = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = (user?.chapter ?: 1) > 1,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Text(
                            if ((user?.chapter ?: 1) > 1) "📖  클리어한 스토리 다시보기"
                            else "📖  아직 클리어한 스토리가 없어요",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            seotdaName1 = user?.seotdaName1 ?: "졸린"
                            seotdaName2 = user?.seotdaName2 ?: "토끼"
                            seotdaName3 = user?.seotdaName3 ?: "콜라"
                            showSeotdaNames = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Text(
                            "🎴  섯다 상대 이름 설정",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Text(
                "계정 및 정보",
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF4E5E3E),
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xF7FFFFFF)),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Button(
                        onClick = { showAccounts = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Text(
                            "👥  다른 프로필로 이동",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    OutlinedButton(
                        onClick = { showPrivacyPolicy = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Text(
                            "🔒  개인정보 처리 안내",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    OutlinedButton(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Text(
                            "↪  로그아웃",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Text(
                "모든 설정은 현재 기기에 저장됩니다.",
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF74786D),
            )
        }
    }

    if (showCouponInput) {
        AlertDialog(
            onDismissRequest = { showCouponInput = false },
            title = { Text("쿠폰 입력") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("쿠폰 번호를 입력해주세요.")
                    OutlinedTextField(
                        value = couponCode,
                        onValueChange = {
                            couponCode = it.take(40)
                            couponResult = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("쿠폰 번호") },
                    )
                    couponResult?.let { result ->
                        Text(
                            text = when (result) {
                                CouponRedeemResult.Success ->
                                    "${formatGameCurrency(couponReward ?: 0L)} 보상이 지급되었습니다."
                                CouponRedeemResult.Invalid -> "유효하지 않은 쿠폰 번호입니다."
                                CouponRedeemResult.AlreadyUsed -> "이미 사용한 쿠폰입니다."
                                CouponRedeemResult.NoUser -> "로그인 정보를 확인할 수 없습니다."
                            },
                            color = if (result == CouponRedeemResult.Success) Color(0xFF2E7D32)
                            else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        couponSubmitting = true
                        viewModel.redeemCoupon(couponCode) { result, reward ->
                            couponResult = result
                            couponReward = reward
                            couponSubmitting = false
                        }
                    },
                    enabled = couponCode.isNotBlank() &&
                        couponResult != CouponRedeemResult.Success && !couponSubmitting,
                ) { Text(if (couponSubmitting) "확인 중…" else "사용") }
            },
            dismissButton = {
                TextButton(onClick = { showCouponInput = false }) { Text("닫기") }
            },
        )
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
                    "로컬 프로필 변경",
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
                                        Text("프로필", modifier = Modifier.fillMaxWidth(),
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
                                    Column(
                                        Modifier.weight(1f),
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            account.money.won(),
                                            modifier = Modifier.fillMaxWidth(),
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.End,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "🔷 ${account.blueChips.formattedNumber()}",
                                            modifier = Modifier.fillMaxWidth(),
                                            color = Color(0xFF0277BD),
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.End,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
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
                    Text("이름은 현재 로컬 프로필에만 저장됩니다.", style = MaterialTheme.typography.bodySmall)
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

}

@Composable
internal fun MenuCard(title: String, detail: String) {
    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) { Column(Modifier.padding(18.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }}
}
