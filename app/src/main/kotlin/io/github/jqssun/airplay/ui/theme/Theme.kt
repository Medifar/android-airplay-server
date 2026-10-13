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

// mirra design tokens (figma handoff palette)
object Mirra {
    val Background = Color(0xFF242229)
    val Surface = Color(0xFF2B2933)
    val SurfaceHigh = Color(0xFF34313D)
    val Border = Color(0xFF4C4857)
    val Cream = Color(0xFFF6F0E5)
    val CreamSecondary = Color(0xFFC8C5CD)
    val Orange = Color(0xFFF2BD86)
    val Lavender = Color(0xFFBFA0E4)
    val Gray = Color(0xFF85858A)
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
    outline = Mirra.Border,
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
