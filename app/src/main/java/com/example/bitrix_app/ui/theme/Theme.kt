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

// OceanColorScheme и ForestColorScheme удалены

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
        // Случаи для OCEAN и FOREST удалены
        // Добавляем else для исчерпывающего when (или он уже есть и покрывает SYSTEM)
        // В данном случае, SYSTEM уже обрабатывается, и т.к. enum стал меньше,
        // else может быть не нужен, если when покрывает все варианты AppThemeOptions.
        // Однако, для безопасности, если AppThemeOptions.SYSTEM не последний,
        // или если есть вероятность добавления новых тем, else полезен.
        // Текущая структура с else для SYSTEM уже корректна.
        else -> { // По умолчанию используем системную логику (этот else покрывает SYSTEM, если он не указан явно выше)
            if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (darkThemeSystem) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkThemeSystem) DarkColorScheme else LightColorScheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
