package com.acaba.pdfreader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFD71920),
    onPrimary = Color.White,
    secondary = Color(0xFF7B4B4B),
    surface = Color(0xFFFFFBFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB3B3),
    secondary = Color(0xFFE8BDBD),
)

@Composable
fun PdfReaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
