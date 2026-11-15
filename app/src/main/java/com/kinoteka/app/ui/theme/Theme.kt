package com.kinoteka.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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

val StarColor = Color(0xFFF5B841)

@Composable
fun KinotekaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
