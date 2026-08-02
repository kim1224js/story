package es.kim.story.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun ProjectSTheme(
    darkTheme: Boolean = false,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val systemDensity = LocalDensity.current

    // 작은 화면에서 시스템 글꼴 배율까지 그대로 적용하면 고정 높이 UI 안의
    // 한글/숫자/이모지가 잘리거나 겹친다. 화면 폭별로 글자 비율을 완만하게
    // 보정하고, 제조사/사용자 설정에 따른 과도한 fontScale 차이도 제한한다.
    val compactScreenScale = when {
        configuration.screenWidthDp <= 320 -> 0.84f
        configuration.screenWidthDp <= 360 -> 0.90f
        configuration.screenWidthDp <= 400 -> 0.95f
        else -> 1f
    }
    val responsiveFontScale = (
        systemDensity.fontScale.coerceIn(0.85f, 1.15f) * compactScreenScale
    ).coerceIn(0.80f, 1.15f)
    val responsiveDensity = Density(
        density = systemDensity.density,
        fontScale = responsiveFontScale,
    )

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalDensity provides responsiveDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
