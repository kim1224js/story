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
internal fun StoryView(viewModel: MainViewModel) {
    val context = LocalContext.current
    val gameAudioVolume = LocalGameAudioVolume.current
    val ttsSettings = LocalTtsSettings.current
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
                storyTts?.let { configureAppTts(it, ttsSettings, TtsRole.StoryCharacter) }
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
                                    storyTts?.let { configureAppTts(it, ttsSettings, TtsRole.StoryCharacter) }
                                    storyTts?.speak(
                                        storyText,
                                        TextToSpeech.QUEUE_FLUSH,
                                        gameSpeechParams(gameAudioVolume),
                                        "story_chapter_${chapter.number}",
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = storyTtsReady && ttsSettings.enabled && gameAudioVolume > 0f,
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
