package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme =
  darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF00273B),
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = Color(0xFF022930),
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = Color(0xFFCFF3F8),
    tertiary = DarkCoralAccent,
    onTertiary = Color(0xFF3E1404),
    tertiaryContainer = DarkCoralContainer,
    onTertiaryContainer = Color(0xFFFFE2D8),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = LinguaNavyPrimary,
    onPrimary = Color.White,
    primaryContainer = LinguaNavyContainer,
    onPrimaryContainer = OnLinguaNavyContainer,
    secondary = CyanTealSecondary,
    onSecondary = Color.White,
    secondaryContainer = CyanTealContainer,
    onSecondaryContainer = OnCyanTealContainer,
    tertiary = CoralAccent,
    onTertiary = Color.White,
    tertiaryContainer = CoralContainer,
    onTertiaryContainer = OnCoralContainer,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
  )

val ClinNoteShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
  )

@Composable
fun LinguaFloatTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = ClinNoteTypography,
    shapes = ClinNoteShapes,
    content = content,
  )
}

@Composable
fun ClinNoteTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  LinguaFloatTheme(darkTheme = darkTheme, content = content)
}
