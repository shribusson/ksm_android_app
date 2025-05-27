package com.example.bitrix_app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.bitrix_app.AppThemeOptions // Импортируем наш enum

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    secondary = DarkSecondary,
    tertiary = DarkTertiary,
    background = DarkBackground,
    surface = DarkSurface,
    error = DarkError,
    onPrimary = DarkOnPrimary,
    onSecondary = DarkOnSecondary,
    onTertiary = DarkOnTertiary,
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface,
    onError = DarkOnError
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    secondary = LightSecondary,
    tertiary = LightTertiary,
    background = LightBackground,
    surface = LightSurface,
    error = LightError,
    onPrimary = LightOnPrimary,
    onSecondary = LightOnSecondary,
    onTertiary = LightOnTertiary,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface,
    onError = LightOnError
)

// Цветовая схема "Океан" (Светлая)
private val OceanColorScheme = lightColorScheme(
    primary = OceanPrimary,
    secondary = OceanSecondary,
    tertiary = OceanTertiary,
    background = OceanBackground,
    surface = OceanSurface,
    error = OceanError,
    onPrimary = OceanOnPrimary,
    onSecondary = OceanOnSecondary,
    onTertiary = OceanOnTertiary,
    onBackground = OceanOnBackground,
    onSurface = OceanOnSurface,
    onError = OceanOnError
)

// Цветовая схема "Лес" (Светлая)
private val ForestColorScheme = lightColorScheme(
    primary = ForestPrimary,
    secondary = ForestSecondary,
    tertiary = ForestTertiary,
    background = ForestBackground,
    surface = ForestSurface,
    error = ForestError,
    onPrimary = ForestOnPrimary,
    onSecondary = ForestOnSecondary,
    onTertiary = ForestOnTertiary,
    onBackground = ForestOnBackground,
    onSurface = ForestOnSurface,
    onError = ForestOnError
)

@Composable
fun Bitrix_appTheme(
    appTheme: AppThemeOptions = AppThemeOptions.SYSTEM, // Используем параметр для выбора темы
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Оставляем возможность для динамических цветов, но по умолчанию выключено
    content: @Composable () -> Unit
) {
    val darkThemeSystem = isSystemInDarkTheme() // Определяем системную тему один раз

    val colorScheme = when (appTheme) {
        AppThemeOptions.SYSTEM -> {
            if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (darkThemeSystem) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkThemeSystem) DarkColorScheme else LightColorScheme
            }
        }
        AppThemeOptions.LIGHT -> LightColorScheme
        AppThemeOptions.DARK -> DarkColorScheme
        AppThemeOptions.OCEAN -> OceanColorScheme // Наша новая тема "Океан"
        AppThemeOptions.FOREST -> ForestColorScheme // Наша новая тема "Лес"
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
