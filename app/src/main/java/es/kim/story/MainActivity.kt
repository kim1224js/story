package es.kim.story

import android.os.Bundle
import android.media.MediaPlayer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dagger.hilt.android.AndroidEntryPoint
import es.kim.story.ui.theme.ProjectSTheme
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(android.graphics.Color.WHITE)
        enableEdgeToEdge()
        setContent { ProjectSTheme { StoryApp() } }
    }
}
private enum class Screen { Splash, Login, Home, Switching }

val LocalGameAudioVolume = staticCompositionLocalOf { 1f }

@Composable private fun StoryApp(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val bgmPreferences = remember {
        context.getSharedPreferences("audio_settings", android.content.Context.MODE_PRIVATE)
    }
    var bgmEnabled by remember {
        mutableStateOf(bgmPreferences.getBoolean("bgm_enabled", true))
    }
    var bgmVolume by remember {
        mutableFloatStateOf(bgmPreferences.getFloat("bgm_volume", 0.3f))
    }
    var masterVolume by remember {
        mutableFloatStateOf(bgmPreferences.getFloat("master_volume", 0.3f))
    }
    var gameSoundEnabled by remember {
        mutableStateOf(bgmPreferences.getBoolean("game_sound_enabled", true))
    }
    var gameSoundVolume by remember {
        mutableFloatStateOf(bgmPreferences.getFloat("game_sound_volume", 0.3f))
    }
    var ttsSettings by remember {
        mutableStateOf(
            AppTtsSettings(
                enabled = bgmPreferences.getBoolean("tts_enabled", true),
                guideVoice = bgmPreferences.getString("tts_guide_voice", "").orEmpty(),
                celebrationVoice = bgmPreferences.getString("tts_celebration_voice", "").orEmpty(),
                characterVoice = bgmPreferences.getString("tts_character_voice", "").orEmpty(),
                gameCharacterVoice = bgmPreferences.getString("tts_game_character_voice", "").orEmpty(),
                style = runCatching {
                    TtsStyle.valueOf(bgmPreferences.getString("tts_style", TtsStyle.Natural.name).orEmpty())
                }.getOrDefault(TtsStyle.Natural),
                pitch = bgmPreferences.getFloat("tts_pitch", 1f),
                speed = bgmPreferences.getFloat("tts_speed", 1f),
            ),
        )
    }
    var homeBgm by remember { mutableIntStateOf(R.raw.bgm_wandering_woodlands) }
    val savedUser by vm.user.collectAsState()
    val accounts by vm.accounts.collectAsState()
    var screen by remember { mutableStateOf(Screen.Splash) }
    var loginInitialId by remember { mutableStateOf<String?>(null) }
    var switchingDestination by remember { mutableStateOf(Screen.Home) }
    var minimumSplashElapsed by remember { mutableStateOf(false) }
    var permissionsReady by remember { mutableStateOf(false) }
    var showHealthDisclosure by remember { mutableStateOf(false) }
    val permissions = remember { setOf(HealthPermission.getReadPermission(StepsRecord::class)) }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        permissionsReady = true
    }
    LaunchedEffect(Unit) {
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            val granted = HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
            if (granted.containsAll(permissions)) permissionsReady = true else showHealthDisclosure = true
        } else permissionsReady = true
    }
    LaunchedEffect(Unit) {
        delay(2_000)
        minimumSplashElapsed = true
    }
    LaunchedEffect(minimumSplashElapsed, permissionsReady) {
        if (minimumSplashElapsed && permissionsReady) screen = Screen.Login
    }
    LaunchedEffect(screen) {
        if (screen == Screen.Switching) {
            delay(900)
            screen = switchingDestination
        }
    }
    AppBackgroundMusic(
        musicRes = when (screen) {
            Screen.Splash, Screen.Login, Screen.Switching -> R.raw.bgm_fairy_lights
            Screen.Home -> homeBgm
        },
        enabled = bgmEnabled && masterVolume > 0f,
        volume = bgmVolume * masterVolume,
    )
    CompositionLocalProvider(
        LocalGameAudioVolume provides if (gameSoundEnabled) gameSoundVolume * masterVolume else 0f,
        LocalTtsSettings provides ttsSettings,
    ) {
    Scaffold(Modifier.fillMaxSize()) { padding -> Box(Modifier.fillMaxSize().padding(padding)) {
        when (screen) {
            Screen.Splash -> SplashScreen()
            Screen.Login -> LoginScreen(
                initialId = loginInitialId ?: savedUser?.userId.orEmpty(),
                accountIds = accounts.map { it.userId },
                onLogin = { id, onResult ->
                    vm.saveUserId(id) { success ->
                        if (success) {
                            loginInitialId = null
                            screen = Screen.Home
                        }
                        onResult(success)
                    }
                },
            )
            Screen.Home -> MainMenuScreen(
                savedUser?.userId.orEmpty(),
                vm,
                bgmEnabled = bgmEnabled,
                bgmVolume = bgmVolume,
                masterVolume = masterVolume,
                gameSoundEnabled = gameSoundEnabled,
                gameSoundVolume = gameSoundVolume,
                onBgmEnabledChange = {
                    bgmEnabled = it
                    bgmPreferences.edit().putBoolean("bgm_enabled", it).apply()
                },
                onBgmVolumeChange = {
                    bgmVolume = it
                    bgmPreferences.edit().putFloat("bgm_volume", it).apply()
                },
                onMasterVolumeChange = {
                    masterVolume = it
                    bgmPreferences.edit().putFloat("master_volume", it).apply()
                },
                onGameSoundEnabledChange = {
                    gameSoundEnabled = it
                    bgmPreferences.edit().putBoolean("game_sound_enabled", it).apply()
                },
                onGameSoundVolumeChange = {
                    gameSoundVolume = it
                    bgmPreferences.edit().putFloat("game_sound_volume", it).apply()
                },
                ttsSettings = ttsSettings,
                onTtsSettingsChange = {
                    ttsSettings = it
                    bgmPreferences.edit()
                        .putBoolean("tts_enabled", it.enabled)
                        .putString("tts_guide_voice", it.guideVoice)
                        .putString("tts_celebration_voice", it.celebrationVoice)
                        .putString("tts_character_voice", it.characterVoice)
                        .putString("tts_game_character_voice", it.gameCharacterVoice)
                        .putString("tts_style", it.style.name)
                        .putFloat("tts_pitch", it.pitch)
                        .putFloat("tts_speed", it.speed)
                        .apply()
                },
                onBgmTrackChange = { homeBgm = it },
                onLogout = {
                    loginInitialId = ""
                    screen = Screen.Login
                },
                onSwitchAccount = { accountId ->
                    vm.switchAccount(accountId)
                    switchingDestination = Screen.Home
                    screen = Screen.Switching
                },
            )
            Screen.Switching -> SwitchingScreen()
        }
    }}
    }
    if (showHealthDisclosure) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("걸음 수 권한 안내") },
            text = {
                Text("걸음 퀘스트의 달성 여부와 보상을 계산하기 위해 Health Connect의 걸음 수를 읽습니다. 걸음 수는 기기 안에서만 처리하며 외부 서버로 전송하거나 다른 사람에게 제공하지 않습니다. 권한을 허용하지 않아도 걸음 퀘스트를 제외한 기능은 이용할 수 있습니다.")
            },
            confirmButton = {
                Button(onClick = {
                    showHealthDisclosure = false
                    launcher.launch(permissions)
                }) { Text("권한 설정") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showHealthDisclosure = false
                    permissionsReady = true
                }) { Text("나중에") }
            },
        )
    }
}

@Composable
private fun AppBackgroundMusic(musicRes: Int, enabled: Boolean, volume: Float) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(musicRes, enabled) {
        player?.release()
        player = if (enabled) {
            MediaPlayer.create(context.applicationContext, musicRes)?.apply {
                isLooping = true
                setVolume(volume, volume)
                start()
            }
        } else {
            null
        }
        onDispose {
            player?.release()
            player = null
        }
    }
    LaunchedEffect(volume) {
        player?.setVolume(volume, volume)
    }
    DisposableEffect(lifecycleOwner, enabled) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (enabled && player?.isPlaying == false) player?.start()
                }
                Lifecycle.Event.ON_STOP -> {
                    if (player?.isPlaying == true) player?.pause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
@Composable private fun SwitchingScreen() {
    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(18.dp))
        Text("변경중입니다!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}
@Composable private fun SplashScreen() {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.splash_background),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x330B2330),
                            Color.Transparent,
                            Color(0x8F071B24),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "무릉도원",
                color = Color.White,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "우리들의 이야기가 시작됩니다",
                color = Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            CircularProgressIndicator(
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
            )
        }
    }
}
@Composable private fun LoginScreen(
    initialId: String,
    accountIds: List<String>,
    onLogin: (String, (Boolean) -> Unit) -> Unit,
) {
    var id by remember(initialId) { mutableStateOf(initialId) }
    var limitError by remember { mutableStateOf(false) }
    val normalizedId = id.trim()
    val canUseAccount = normalizedId in accountIds || accountIds.size < 3
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.login_background),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.5f,
        )
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                color = Color(0xFFFDF8E9).copy(alpha = 0.88f),
                shape = RoundedCornerShape(30.dp),
                border = BorderStroke(1.5.dp, Color(0xFFB88B43).copy(alpha = 0.72f)),
                shadowElevation = 14.dp,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "✦  무릉도원  ✦",
                        color = Color(0xFF315C51),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = "우리들의 이야기에 들어가기",
                        color = Color(0xFF746142),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(0.72f),
                        color = Color(0xFFB88B43).copy(alpha = 0.45f),
                    )
                    Spacer(Modifier.height(20.dp))
                    if (accountIds.isNotEmpty()) {
                        Text(
                            "프로필",
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF315C51),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            accountIds.distinct().forEach { accountId ->
                                OutlinedButton(
                                    onClick = {
                                        id = accountId
                                        limitError = false
                                        onLogin(accountId) { success -> limitError = !success }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, Color(0xFF6B9186)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (normalizedId == accountId) {
                                            Color(0xFFDCEBE5)
                                        } else {
                                            Color.White.copy(alpha = 0.5f)
                                        },
                                        contentColor = Color(0xFF315C51),
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
                                ) {
                                    Text("👤 $accountId", fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = Color(0xFFB88B43).copy(alpha = 0.35f))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "새 프로필로 시작하기",
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF746142),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(Modifier.height(7.dp))
                    }
                    OutlinedTextField(
                        id,
                        {
                            id = it
                            limitError = false
                        },
                        Modifier.fillMaxWidth(),
                        label = { Text("아이디") },
                        placeholder = { Text("이야기 속 이름을 입력하세요") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3F7668),
                            unfocusedBorderColor = Color(0xFFA79064),
                            focusedLabelColor = Color(0xFF315C51),
                            cursorColor = Color(0xFF315C51),
                            focusedContainerColor = Color.White.copy(alpha = 0.58f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.42f),
                        ),
                    )
                    if ((!canUseAccount && normalizedId.isNotBlank()) || limitError) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "프로필은 최대 3개입니다. 기존 프로필을 선택하거나 다른 캐릭터를 삭제해 주세요.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Button(
                        { onLogin(normalizedId) { success -> limitError = !success } },
                        Modifier.fillMaxWidth().height(54.dp),
                        enabled = normalizedId.isNotBlank() && canUseAccount,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF315C51),
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF829A91).copy(alpha = 0.62f),
                            disabledContentColor = Color.White.copy(alpha = 0.72f),
                        ),
                    ) {
                        Text(
                            "이야기 시작하기",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "프로필은 최대 3개까지 만들 수 있어요",
                        color = Color(0xFF746142).copy(alpha = 0.88f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

