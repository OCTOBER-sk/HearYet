package com.hearyet.app.core.ui.theme

import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.hearyet.app.core.ui.color.HearYetColors

private val HearYetDarkScheme = darkColorScheme(
    primary = HearYetColors.Primary,
    onPrimary = HearYetColors.OnPrimary,
    primaryContainer = HearYetColors.PrimaryContainer,
    onPrimaryContainer = HearYetColors.OnPrimaryContainer,
    secondary = HearYetColors.Secondary,
    onSecondary = HearYetColors.OnSecondary,
    secondaryContainer = HearYetColors.SecondaryContainer,
    onSecondaryContainer = HearYetColors.OnSecondaryContainer,
    tertiary = HearYetColors.Tertiary,
    onTertiary = HearYetColors.OnTertiary,
    tertiaryContainer = HearYetColors.TertiaryContainer,
    onTertiaryContainer = HearYetColors.OnTertiaryContainer,
    error = HearYetColors.Error,
    onError = HearYetColors.OnError,
    errorContainer = HearYetColors.ErrorContainer,
    onErrorContainer = HearYetColors.OnErrorContainer,
    // every surface slot stays warm black, never the M3 baseline cool grey
    background = HearYetColors.Background,
    onBackground = HearYetColors.OnBackground,
    surface = HearYetColors.Surface,
    onSurface = HearYetColors.OnSurface,
    surfaceVariant = HearYetColors.SurfaceVariant,
    onSurfaceVariant = HearYetColors.OnSurfaceVariant,
    surfaceContainerLowest = HearYetColors.SurfaceContainerLowest,
    surfaceContainerLow = HearYetColors.SurfaceContainerLow,
    surfaceContainer = HearYetColors.SurfaceContainer,
    surfaceContainerHigh = HearYetColors.SurfaceContainerHigh,
    surfaceContainerHighest = HearYetColors.SurfaceContainerHighest,
    outline = HearYetColors.Outline,
    outlineVariant = HearYetColors.OutlineVariant,
    inverseSurface = HearYetColors.InverseSurface,
    inverseOnSurface = HearYetColors.InverseOnSurface,
    inversePrimary = HearYetColors.InversePrimary,
    scrim = HearYetColors.Scrim,
)

@Composable
fun HearYetTheme(content: @Composable () -> Unit) {
    // HearYet is always this static dark scheme — no dynamic color branch,
    // no light theme, regardless of system theme or Android version.
    CompositionLocalProvider(LocalRippleConfiguration provides HearYetRippleConfiguration) {
        MaterialTheme(
            colorScheme = HearYetDarkScheme,
            typography = HearYetTypography,
            shapes = HearYetShapes,
            content = content,
        )
    }
}
