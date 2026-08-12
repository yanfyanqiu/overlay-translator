package com.offlineinterpreter.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary   = Color(0xFF1565C0),
    secondary = Color(0xFF00897B),
    tertiary  = Color(0xFFFF6F00),
    background= Color(0xFFF5F5F5),
    surface   = Color.White,
)

private val DarkColors = darkColorScheme(
    primary   = Color(0xFF42A5F5),
    secondary = Color(0xFF26A69A),
    tertiary  = Color(0xFFFFB300),
    background= Color(0xFF121212),
    surface   = Color(0xFF1E1E1E),
)

@Composable
fun OfflineInterpreterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
