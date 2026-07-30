package es.kim.story

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

internal enum class TtsRole { Guide, Celebration, StoryCharacter, GameCharacter }

internal enum class TtsStyle(
    val label: String,
    val pitch: Float,
    val speed: Float,
) {
    Natural("자연스럽게", 1.0f, 1.0f),
    Cute("귀엽게", 1.3f, 1.12f),
    Calm("차분하게", 0.88f, 0.9f),
    Bright("밝게", 1.15f, 1.05f),
    Deep("낮고 진하게", 0.76f, 0.9f),
}

internal data class AppTtsSettings(
    val enabled: Boolean = true,
    val voice: String = "",
    val style: TtsStyle = TtsStyle.Natural,
    val pitch: Float = 1f,
    val speed: Float = 1f,
)

internal val LocalTtsSettings = staticCompositionLocalOf { AppTtsSettings() }

internal fun configureAppTts(
    tts: TextToSpeech,
    settings: AppTtsSettings,
    role: TtsRole,
) {
    tts.language = Locale.KOREAN
    val voiceName = settings.voice
    if (voiceName.isNotBlank()) {
        tts.voices?.firstOrNull { it.name == voiceName }?.let { tts.voice = it }
    }
    tts.setPitch((settings.style.pitch * settings.pitch).coerceIn(0.5f, 2f))
    tts.setSpeechRate((settings.style.speed * settings.speed).coerceIn(0.5f, 2f))
}

@Composable
internal fun TtsSettingsPanel(
    settings: AppTtsSettings,
    onSettingsChange: (AppTtsSettings) -> Unit,
) {
    val context = LocalContext.current
    var engine by remember { mutableStateOf<TextToSpeech?>(null) }
    var voices by remember { mutableStateOf(emptyList<Voice>()) }
    var ready by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                voices = engine?.voices.orEmpty()
                    .filter { it.locale.language == Locale.KOREAN.language }
                    .sortedWith(compareBy<Voice> { it.isNetworkConnectionRequired }.thenBy { it.name })
            }
        }
        engine = tts
        onDispose {
            tts.stop()
            tts.shutdown()
            engine = null
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("TTS 음성", fontWeight = FontWeight.ExtraBold)
                Text(
                    "스타일 버튼으로 목소리·음높이·속도를 함께 적용해요",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = settings.enabled,
                onCheckedChange = { onSettingsChange(settings.copy(enabled = it)) },
            )
        }

        if (settings.enabled) {
            Text("목소리 스타일", fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TtsStyle.entries.forEach { style ->
                    FilterChip(
                        selected = settings.style == style,
                        onClick = {
                            val next = settings.copy(
                                voice = voiceForStyle(style, voices),
                                style = style,
                            )
                            onSettingsChange(next)
                            previewTts(engine, next, TtsRole.Guide, "${style.label} 목소리를 적용했습니다.")
                        },
                        label = { Text(style.label) },
                    )
                }
            }
            TtsSlider("음높이", settings.pitch, 0.7f..1.4f) {
                onSettingsChange(settings.copy(pitch = it))
            }
            TtsSlider("말하기 속도", settings.speed, 0.7f..1.4f) {
                onSettingsChange(settings.copy(speed = it))
            }
            Button(
                onClick = {
                    previewTts(engine, settings, TtsRole.Guide, "선택한 목소리와 스타일의 미리듣기입니다.")
                },
                enabled = ready,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("▶ 선택한 목소리 미리듣기")
            }
            Text(
                if (voices.isEmpty()) {
                    "한국어 음성이 보이지 않으면 휴대폰의 TTS 음성 데이터를 설치해 주세요."
                } else {
                    "남성·여성 음성 종류와 명칭은 휴대폰의 Google 또는 Samsung TTS 엔진에 따라 달라요."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TtsSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.width(82.dp), style = MaterialTheme.typography.labelMedium)
        Slider(value = value, onValueChange = onValueChange, valueRange = range, modifier = Modifier.weight(1f))
        Text(String.format(Locale.KOREAN, "%.1f", value), modifier = Modifier.width(34.dp))
    }
}

private fun voiceForStyle(style: TtsStyle, voices: List<Voice>): String =
    voices.getOrNull(style.ordinal % voices.size.coerceAtLeast(1))?.name.orEmpty()

private fun previewTts(
    engine: TextToSpeech?,
    settings: AppTtsSettings,
    role: TtsRole,
    message: String,
) {
    if (!settings.enabled) return
    engine?.let {
        configureAppTts(it, settings, role)
        it.speak(message, TextToSpeech.QUEUE_FLUSH, null, "tts_preview_${System.currentTimeMillis()}")
    }
}
