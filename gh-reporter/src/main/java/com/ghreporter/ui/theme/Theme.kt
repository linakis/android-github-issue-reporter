package com.ghreporter.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = GHReporterColors.Primary,
    onPrimary = GHReporterColors.OnPrimary,
    primaryContainer = GHReporterColors.PrimaryVariant,
    onPrimaryContainer = GHReporterColors.OnPrimary,
    secondary = GHReporterColors.Secondary,
    onSecondary = GHReporterColors.OnSecondary,
    secondaryContainer = GHReporterColors.SecondaryVariant,
    onSecondaryContainer = GHReporterColors.OnSecondary,
    background = GHReporterColors.Background,
    onBackground = GHReporterColors.OnBackground,
    surface = GHReporterColors.Surface,
    onSurface = GHReporterColors.OnSurface,
    surfaceVariant = GHReporterColors.Surface,
    onSurfaceVariant = GHReporterColors.TextSecondary,
    error = GHReporterColors.Error,
    onError = GHReporterColors.OnError,
    outline = GHReporterColors.Border,
    outlineVariant = GHReporterColors.Border
)

private val DarkColorScheme = darkColorScheme(
    primary = GHReporterColors.PrimaryVariant,
    onPrimary = GHReporterColors.OnPrimary,
    primaryContainer = GHReporterColors.Primary,
    onPrimaryContainer = GHReporterColors.OnPrimary,
    secondary = GHReporterColors.Secondary,
    onSecondary = GHReporterColors.OnSecondary,
    secondaryContainer = GHReporterColors.SecondaryVariant,
    onSecondaryContainer = GHReporterColors.OnSecondary,
    background = GHReporterColors.BackgroundDark,
    onBackground = GHReporterColors.OnBackgroundDark,
    surface = GHReporterColors.SurfaceDark,
    onSurface = GHReporterColors.OnSurfaceDark,
    surfaceVariant = GHReporterColors.SurfaceDark,
    onSurfaceVariant = GHReporterColors.TextSecondaryDark,
    error = GHReporterColors.ErrorDark,
    onError = GHReporterColors.OnError,
    outline = GHReporterColors.BorderDark,
    outlineVariant = GHReporterColors.BorderDark
)

/**
 * GHReporter theme wrapper.
 *
 * @param darkTheme Whether to use dark theme
 * @param dynamicColor Whether to use dynamic colors (Android 12+)
 * @param content Composable content
 */
@Composable
fun GHReporterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GHReporterTypography,
        content = content
    )
}
