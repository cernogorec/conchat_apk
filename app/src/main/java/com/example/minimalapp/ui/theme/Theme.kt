package com.example.minimalapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    background = MinimalBackground,
    onBackground = MinimalText,
    surface = MinimalBackground,
    onSurface = MinimalText
)

private val DarkColorScheme = darkColorScheme()

@Composable
fun MinimalAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
