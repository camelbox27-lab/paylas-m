package com.paylasm.iptv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF04121C),
    primaryContainer = Color(0xFF14364A),
    onPrimaryContainer = Color(0xFFBFE8FB),
    secondary = Color(0xFF818CF8),
    onSecondary = Color(0xFF0B0E14),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFE6EAF2),
    surface = Color(0xFF11151F),
    onSurface = Color(0xFFE6EAF2),
    surfaceVariant = Color(0xFF1A2130),
    onSurfaceVariant = Color(0xFFA8B3C7),
    outline = Color(0xFF2B3446),
    error = Color(0xFFFF7B7B),
    onError = Color(0xFF2A0A0A)
)

@Composable
fun PaylasMTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = Typography(),
        content = content
    )
}
