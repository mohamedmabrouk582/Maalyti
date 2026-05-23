package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CosmicPrimary,
    secondary = CosmicSecondary,
    tertiary = CosmicAccentGold,
    background = CosmicSlateBg,
    surface = CosmicSurface,
    onPrimary = CosmicTextOnPrimary,
    onSecondary = CosmicTextOnPrimary,
    onBackground = CosmicTextWhite,
    onSurface = CosmicTextWhite,
    error = CosmicCrimson
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
