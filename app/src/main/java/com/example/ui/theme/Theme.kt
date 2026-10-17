package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ArvexaColorScheme = darkColorScheme(
    primary = ArvexaBlue,
    onPrimary = Color.White,
    primaryContainer = ArvexaIndigo,
    onPrimaryContainer = Color.White,
    secondary = ArvexaCyan,
    onSecondary = Color(0xFF031A2F),
    secondaryContainer = DarkNavySurfaceVariant,
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = RecordRed,
    onTertiary = Color.White,
    background = DarkNavyBackground,
    onBackground = TextPrimary,
    surface = DarkNavySurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkNavySurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DarkNavyCardBorder,
    error = RecordRed,
    onError = Color.White
)

@Composable
fun ArvexaTheme(
    darkTheme: Boolean = true, // Default to sleek dark navy theme
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ArvexaColorScheme,
        typography = Typography,
        content = content
    )
}

// Retain alias for compatibility
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    ArvexaTheme(darkTheme = true, content = content)
}
