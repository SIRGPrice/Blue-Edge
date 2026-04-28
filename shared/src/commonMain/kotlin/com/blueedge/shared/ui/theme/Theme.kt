/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Multiplatform mirror of `:app/.../ui/theme/Theme.kt`.
 *
 * Differences:
 *  - Drops the `Theme` proto enum dependency. The shared theme exposes a
 *    plain enum `ThemeMode { AUTO, LIGHT, DARK }`; the Android `:app` keeps
 *    its proto-DataStore as the source of truth and converts both ways.
 *  - `StatusBarColorController` is now `expect`/`actual`.
 *  - Typography baseline only; custom font wiring (Nunito) lives in `:app`
 *    until Compose Multiplatform fonts are migrated through `compose.resources`.
 */
package com.blueedge.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemeMode { AUTO, LIGHT, DARK }

object ThemeSettings {
  val themeOverride = mutableStateOf(ThemeMode.AUTO)
}

private val lightScheme = lightColorScheme(
  primary = primaryLight,
  onPrimary = onPrimaryLight,
  primaryContainer = primaryContainerLight,
  onPrimaryContainer = onPrimaryContainerLight,
  secondary = secondaryLight,
  onSecondary = onSecondaryLight,
  secondaryContainer = secondaryContainerLight,
  onSecondaryContainer = onSecondaryContainerLight,
  tertiary = tertiaryLight,
  onTertiary = onTertiaryLight,
  tertiaryContainer = tertiaryContainerLight,
  onTertiaryContainer = onTertiaryContainerLight,
  error = errorLight,
  onError = onErrorLight,
  errorContainer = errorContainerLight,
  onErrorContainer = onErrorContainerLight,
  background = backgroundLight,
  onBackground = onBackgroundLight,
  surface = surfaceLight,
  onSurface = onSurfaceLight,
  surfaceVariant = surfaceVariantLight,
  onSurfaceVariant = onSurfaceVariantLight,
  outline = outlineLight,
  outlineVariant = outlineVariantLight,
  scrim = scrimLight,
  inverseSurface = inverseSurfaceLight,
  inverseOnSurface = inverseOnSurfaceLight,
  inversePrimary = inversePrimaryLight,
  surfaceDim = surfaceDimLight,
  surfaceBright = surfaceBrightLight,
  surfaceContainerLowest = surfaceContainerLowestLight,
  surfaceContainerLow = surfaceContainerLowLight,
  surfaceContainer = surfaceContainerLight,
  surfaceContainerHigh = surfaceContainerHighLight,
  surfaceContainerHighest = surfaceContainerHighestLight,
)

private val darkScheme = darkColorScheme(
  primary = primaryDark,
  onPrimary = onPrimaryDark,
  primaryContainer = primaryContainerDark,
  onPrimaryContainer = onPrimaryContainerDark,
  secondary = secondaryDark,
  onSecondary = onSecondaryDark,
  secondaryContainer = secondaryContainerDark,
  onSecondaryContainer = onSecondaryContainerDark,
  tertiary = tertiaryDark,
  onTertiary = onTertiaryDark,
  tertiaryContainer = tertiaryContainerDark,
  onTertiaryContainer = onTertiaryContainerDark,
  error = errorDark,
  onError = onErrorDark,
  errorContainer = errorContainerDark,
  onErrorContainer = onErrorContainerDark,
  background = backgroundDark,
  onBackground = onBackgroundDark,
  surface = surfaceDark,
  onSurface = onSurfaceDark,
  surfaceVariant = surfaceVariantDark,
  onSurfaceVariant = onSurfaceVariantDark,
  outline = outlineDark,
  outlineVariant = outlineVariantDark,
  scrim = scrimDark,
  inverseSurface = inverseSurfaceDark,
  inverseOnSurface = inverseOnSurfaceDark,
  inversePrimary = inversePrimaryDark,
  surfaceDim = surfaceDimDark,
  surfaceBright = surfaceBrightDark,
  surfaceContainerLowest = surfaceContainerLowestDark,
  surfaceContainerLow = surfaceContainerLowDark,
  surfaceContainer = surfaceContainerDark,
  surfaceContainerHigh = surfaceContainerHighDark,
  surfaceContainerHighest = surfaceContainerHighestDark,
)

@Immutable
data class CustomColors(
  val appTitleGradientColors: List<Color> = emptyList(),
  val tabHeaderBgColor: Color = Color.Transparent,
  val taskCardBgColor: Color = Color.Transparent,
  val taskBgColors: List<Color> = emptyList(),
  val taskBgGradientColors: List<List<Color>> = emptyList(),
  val taskIconColors: List<Color> = emptyList(),
  val taskIconShapeBgColor: Color = Color.Transparent,
  val homeBottomGradient: List<Color> = emptyList(),
  val userBubbleBgColor: Color = Color.Transparent,
  val agentBubbleBgColor: Color = Color.Transparent,
  val linkColor: Color = Color.Transparent,
  val successColor: Color = Color.Transparent,
  val recordButtonBgColor: Color = Color.Transparent,
  val waveFormBgColor: Color = Color.Transparent,
  val modelInfoIconColor: Color = Color.Transparent,
  val warningContainerColor: Color = Color.Transparent,
  val warningTextColor: Color = Color.Transparent,
  val errorContainerColor: Color = Color.Transparent,
  val errorTextColor: Color = Color.Transparent,
  val newFeatureContainerColor: Color = Color.Transparent,
  val newFeatureTextColor: Color = Color.Transparent,
  val bgStarColor: Color = Color.Transparent,
)

val LocalCustomColors = staticCompositionLocalOf { CustomColors() }

val lightCustomColors = CustomColors(
  appTitleGradientColors = listOf(Color(0xFF85B1F8), Color(0xFF3174F1)),
  tabHeaderBgColor = Color(0xFF3174F1),
  taskCardBgColor = surfaceContainerLowestLight,
  taskBgColors = listOf(
    Color(0xFFFFF5F5), Color(0xFFF4FBF6), Color(0xFFF1F6FE), Color(0xFFFFFBF0),
  ),
  taskBgGradientColors = listOf(
    listOf(Color(0xFFE25F57), Color(0xFFDB372D)),
    listOf(Color(0xFF41A15F), Color(0xFF128937)),
    listOf(Color(0xFF669DF6), Color(0xFF3174F1)),
    listOf(Color(0xFFFDD45D), Color(0xFFCAA12A)),
  ),
  taskIconColors = listOf(
    Color(0xFFDB372D), Color(0xFF128937), Color(0xFF3174F1), Color(0xFFCAA12A),
  ),
  taskIconShapeBgColor = Color.White,
  homeBottomGradient = listOf(Color(0x00F8F9FF), Color(0xFFFFEFC9)),
  agentBubbleBgColor = Color(0xFFE9EEF6),
  userBubbleBgColor = Color(0xFF32628D),
  linkColor = Color(0xFF32628D),
  successColor = Color(0xFF3D860B),
  recordButtonBgColor = Color(0xFFEE675C),
  waveFormBgColor = Color(0xFFAAAAAA),
  modelInfoIconColor = Color(0xFFCCCCCC),
  warningContainerColor = Color(0xFFFEF7E0),
  warningTextColor = Color(0xFFE37400),
  errorContainerColor = Color(0xFFFCE8E6),
  errorTextColor = Color(0xFFD93025),
  newFeatureContainerColor = Color(0xFFEEDCFE),
  newFeatureTextColor = Color(0xFF400B84),
  bgStarColor = Color(0x3A669AF5),
)

val darkCustomColors = CustomColors(
  appTitleGradientColors = listOf(Color(0xFF85B1F8), Color(0xFF3174F1)),
  tabHeaderBgColor = Color(0xFF3174F1),
  taskCardBgColor = surfaceContainerHighDark,
  taskBgColors = listOf(
    Color(0xFF181210), Color(0xFF131711), Color(0xFF191924), Color(0xFF1A1813),
  ),
  taskBgGradientColors = listOf(
    listOf(Color(0xFFE25F57), Color(0xFFDB372D)),
    listOf(Color(0xFF41A15F), Color(0xFF128937)),
    listOf(Color(0xFF669DF6), Color(0xFF3174F1)),
    listOf(Color(0xFFFDD45D), Color(0xFFCAA12A)),
  ),
  taskIconColors = listOf(
    Color(0xFFE25F57), Color(0xFF41A15F), Color(0xFF669DF6), Color(0xFFCAA12A),
  ),
  taskIconShapeBgColor = Color(0xFF202124),
  homeBottomGradient = listOf(Color(0x00F8F9FF), Color(0x1AF6AD01)),
  agentBubbleBgColor = Color(0xFF1B1C1D),
  userBubbleBgColor = Color(0xFF1F3760),
  linkColor = Color(0xFF9DCAFC),
  successColor = Color(0xFFA1CE83),
  recordButtonBgColor = Color(0xFFEE675C),
  waveFormBgColor = Color(0xFFAAAAAA),
  modelInfoIconColor = Color(0xFFCCCCCC),
  warningContainerColor = Color(0xFF554C33),
  warningTextColor = Color(0xFFFCC934),
  errorContainerColor = Color(0xFF523A3B),
  errorTextColor = Color(0xFFEE675C),
  newFeatureContainerColor = Color(0xFFEEDCFE),
  newFeatureTextColor = Color(0xFF400B84),
  bgStarColor = Color(0x19346BF0),
)

val MaterialTheme.customColors: CustomColors
  @Composable @ReadOnlyComposable get() = LocalCustomColors.current

val AppTypography = Typography()

/** Platform-specific status/navigation bar color sync. */
@Composable
expect fun StatusBarColorController(useDarkTheme: Boolean)

@Composable
fun BlueEdgeTheme(content: @Composable () -> Unit) {
  val override = ThemeSettings.themeOverride.value
  val darkTheme: Boolean = when (override) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.AUTO -> isSystemInDarkTheme()
  }
  StatusBarColorController(useDarkTheme = darkTheme)
  val colorScheme = if (darkTheme) darkScheme else lightScheme
  val customPalette = if (darkTheme) darkCustomColors else lightCustomColors
  CompositionLocalProvider(LocalCustomColors provides customPalette) {
    MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
  }
}

