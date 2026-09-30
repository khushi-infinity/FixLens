package com.fixlens.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Stable, paper-and-ink palette. Camera contrast uses its own explicit colors. */
object FixLensColors {
    val Paper = Color(0xFFF5F1E7)
    val Cream = Color(0xFFFCF9F2)
    val Ink = Color(0xFF343A36)
    val MutedInk = Color(0xFF636960)
    val Terracotta = Color(0xFF995238)
    val ClayWash = Color(0xFFEBD7C8)
    val Sage = Color(0xFFE3E9DF)
    val SageInk = Color(0xFF4E6657)
    val Lavender = Color(0xFFE2E3EE)
    val BlueGray = Color(0xFF586677)
    val Rule = Color(0xFFCCCBBE)
    val Danger = Color(0xFFA23E32)
    val CameraInk = Color(0xFF282E2A)
}

private val FixLensColorScheme = lightColorScheme(
    primary = FixLensColors.Terracotta,
    onPrimary = FixLensColors.Cream,
    primaryContainer = FixLensColors.ClayWash,
    onPrimaryContainer = FixLensColors.Ink,
    secondary = FixLensColors.SageInk,
    onSecondary = FixLensColors.Cream,
    secondaryContainer = FixLensColors.Sage,
    onSecondaryContainer = FixLensColors.Ink,
    tertiary = FixLensColors.BlueGray,
    onTertiary = FixLensColors.Cream,
    tertiaryContainer = FixLensColors.Lavender,
    onTertiaryContainer = FixLensColors.Ink,
    background = FixLensColors.Paper,
    onBackground = FixLensColors.Ink,
    surface = FixLensColors.Cream,
    onSurface = FixLensColors.Ink,
    surfaceVariant = FixLensColors.Sage,
    onSurfaceVariant = FixLensColors.MutedInk,
    surfaceTint = FixLensColors.Paper,
    surfaceContainer = FixLensColors.Paper,
    surfaceContainerLow = FixLensColors.Cream,
    surfaceContainerHigh = FixLensColors.Sage,
    outline = FixLensColors.MutedInk,
    outlineVariant = FixLensColors.Rule,
    error = FixLensColors.Danger,
    onError = FixLensColors.Cream,
    errorContainer = Color(0xFFF1DED6),
    onErrorContainer = FixLensColors.Danger,
)

@Composable
fun FixLensTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FixLensColorScheme,
        typography = Typography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(20.dp),
        ),
        content = content,
    )
}
