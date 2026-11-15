package com.kinoteka.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.kinoteka.app.data.AppTheme
import androidx.compose.ui.graphics.Color

/** Тёплый «кинозальный» янтарь + глубокий фиолетово-чёрный фон. */
private val Amber = Color(0xFFF5B841)
private val AmberDark = Color(0xFF8A5A00)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF2A1C00),
    primaryContainer = Color(0xFF5C3F00),
    onPrimaryContainer = Color(0xFFFFDEA6),
    secondary = Color(0xFFD8C4A0),
    onSecondary = Color(0xFF3B2F15),
    secondaryContainer = Color(0xFF3A3242),
    onSecondaryContainer = Color(0xFFEDE0F5),
    background = Color(0xFF121016),
    onBackground = Color(0xFFE9E1EA),
    surface = Color(0xFF121016),
    onSurface = Color(0xFFE9E1EA),
    surfaceVariant = Color(0xFF2A2530),
    onSurfaceVariant = Color(0xFFCBC3CF),
    surfaceContainer = Color(0xFF1C1921),
    surfaceContainerHigh = Color(0xFF26222C),
    surfaceContainerHighest = Color(0xFF312C37),
    outline = Color(0xFF948E99),
    outlineVariant = Color(0xFF4A4550),
)

private val LightColors = lightColorScheme(
    primary = AmberDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDEA6),
    onPrimaryContainer = Color(0xFF2A1C00),
    secondary = Color(0xFF6B5D3F),
    secondaryContainer = Color(0xFFEDE0F5),
    onSecondaryContainer = Color(0xFF231A2C),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF1E1B20),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF1E1B20),
    surfaceVariant = Color(0xFFEDE4EC),
    onSurfaceVariant = Color(0xFF4A4550),
    surfaceContainer = Color(0xFFF6EEF0),
    surfaceContainerHigh = Color(0xFFF0E8EA),
    surfaceContainerHighest = Color(0xFFEAE2E4),
)

/** «Джунглі»: глубокий тёмно-зелёный, салатовые листья, золотистые акценты. */
private val JungleColors = darkColorScheme(
    primary = Color(0xFFA5D86E),
    onPrimary = Color(0xFF10230A),
    primaryContainer = Color(0xFF2E5A1C),
    onPrimaryContainer = Color(0xFFCDEFB0),
    secondary = Color(0xFFE8C35A),
    onSecondary = Color(0xFF2B2000),
    secondaryContainer = Color(0xFF34482A),
    onSecondaryContainer = Color(0xFFE2EFCF),
    tertiary = Color(0xFFF2A65A),
    background = Color(0xFF0D1A10),
    onBackground = Color(0xFFE2EEDA),
    surface = Color(0xFF0D1A10),
    onSurface = Color(0xFFE2EEDA),
    surfaceVariant = Color(0xFF213628),
    onSurfaceVariant = Color(0xFFB7CBB1),
    surfaceContainerLowest = Color(0xFF09140C),
    surfaceContainerLow = Color(0xFF112016),
    surfaceContainer = Color(0xFF14261A),
    surfaceContainerHigh = Color(0xFF1A2F21),
    surfaceContainerHighest = Color(0xFF213829),
    outline = Color(0xFF83977E),
    outlineVariant = Color(0xFF384D3B),
)

val StarColor = Color(0xFFF5B841)

/** Какая тема сейчас активна (для украшений вроде листьев в джунглях). */
val LocalAppTheme = staticCompositionLocalOf { AppTheme.SYSTEM }

/** Тёмная ли итоговая тема (нужно, например, для цвета значков в строке состояния). */
@Composable
fun AppTheme.isDark(): Boolean = when (this) {
    AppTheme.SYSTEM -> isSystemInDarkTheme()
    AppTheme.LIGHT -> false
    AppTheme.DARK, AppTheme.JUNGLE -> true
}

@Composable
fun KinotekaTheme(theme: AppTheme = AppTheme.SYSTEM, content: @Composable () -> Unit) {
    val colors = when (theme) {
        AppTheme.JUNGLE -> JungleColors
        else -> if (theme.isDark()) DarkColors else LightColors
    }
    CompositionLocalProvider(LocalAppTheme provides theme) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

/** Цвета для образцов в настройках (фон, карточка, акцент). */
fun themeSwatch(theme: AppTheme, systemDark: Boolean): Triple<Color, Color, Color> = when (theme) {
    AppTheme.JUNGLE -> Triple(JungleColors.background, JungleColors.surfaceContainerHighest, JungleColors.primary)
    AppTheme.DARK -> Triple(DarkColors.background, DarkColors.surfaceContainerHighest, DarkColors.primary)
    AppTheme.LIGHT -> Triple(LightColors.background, LightColors.surfaceContainerHighest, LightColors.primary)
    AppTheme.SYSTEM -> themeSwatch(if (systemDark) AppTheme.DARK else AppTheme.LIGHT, systemDark)
}
