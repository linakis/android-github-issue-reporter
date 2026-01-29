package com.ghreporter.sample.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF238636),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2EA043),
    secondary = Color(0xFF0969DA),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF6F8FA)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF2EA043),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF238636),
    secondary = Color(0xFF0969DA),
    background = Color(0xFF0D1117),
    surface = Color(0xFF161B22)
)

@Composable
fun GHReporterSampleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
