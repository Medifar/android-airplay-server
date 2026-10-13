package io.github.jqssun.airplay.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.jqssun.airplay.R

// mirra design tokens
object Mirra {
    val Background = Color(0xFF1F1E24)
    val Surface = Color(0xFF24242D)
    val SurfaceHigh = Color(0xFF2E2E39)
    val Cream = Color(0xFFFDF3E6)
    val CreamSecondary = Cream.copy(alpha = 0.6f)
    val Orange = Color(0xFFF5B77A)
    val Lavender = Color(0xFFC39EEE)
    val Muted = Cream.copy(alpha = 0.3f)
}

val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
    Font(R.font.nunito_black, FontWeight.Black),
)

private val colors = darkColorScheme(
    primary = Mirra.Orange,
    onPrimary = Mirra.Background,
    primaryContainer = Mirra.SurfaceHigh,
    onPrimaryContainer = Mirra.Cream,
    secondary = Mirra.Lavender,
    onSecondary = Mirra.Background,
    secondaryContainer = Mirra.SurfaceHigh,
    onSecondaryContainer = Mirra.Cream,
    tertiary = Mirra.Lavender,
    background = Mirra.Background,
    onBackground = Mirra.Cream,
    surface = Mirra.Background,
    onSurface = Mirra.Cream,
    surfaceVariant = Mirra.Surface,
    onSurfaceVariant = Mirra.CreamSecondary,
    surfaceContainerLowest = Mirra.Background,
    surfaceContainerLow = Mirra.Surface,
    surfaceContainer = Mirra.Surface,
    surfaceContainerHigh = Mirra.SurfaceHigh,
    surfaceContainerHighest = Mirra.SurfaceHigh,
    outline = Mirra.Muted,
    outlineVariant = Mirra.Cream.copy(alpha = 0.12f),
)

private val typography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Nunito),
        displayMedium = displayMedium.copy(fontFamily = Nunito),
        displaySmall = displaySmall.copy(fontFamily = Nunito),
        headlineLarge = headlineLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
        headlineMedium = headlineMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
        headlineSmall = headlineSmall.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
        titleLarge = titleLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
        titleSmall = titleSmall.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
        bodyLarge = bodyLarge.copy(fontFamily = Nunito),
        bodyMedium = bodyMedium.copy(fontFamily = Nunito),
        bodySmall = bodySmall.copy(fontFamily = Nunito),
        labelLarge = labelLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
        labelMedium = labelMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(fontFamily = Nunito, fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun AirPlayTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
