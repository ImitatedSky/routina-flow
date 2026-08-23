package com.routina.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = RoutinaColors.TriggerTime,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4FD),
    onPrimaryContainer = Color(0xFF0B3C6B),
    secondary = RoutinaColors.ActionNotify,
    onSecondary = Color.White,
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFEDEFF3),
    onSurfaceVariant = Color(0xFF44474E),
    error = RoutinaColors.Failure,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FC3F5),
    onPrimary = Color(0xFF00325B),
    primaryContainer = Color(0xFF0F4A80),
    onPrimaryContainer = Color(0xFFD3E4FD),
    secondary = Color(0xFFE2B4F0),
    onSecondary = Color(0xFF46135B),
    background = Color(0xFF121417),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF1B1E22),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF2A2E34),
    onSurfaceVariant = Color(0xFFC4C6CF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun RoutinaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
