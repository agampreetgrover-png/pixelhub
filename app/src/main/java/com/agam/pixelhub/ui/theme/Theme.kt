package com.agam.pixelhub.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import com.agam.pixelhub.ui.motion.LocalReducedMotion

/** PixelHub's own palette in light (day) and dark (night). */
@Composable
fun PixelHubTheme(content: @Composable () -> Unit) {
    val hub = if (isSystemInDarkTheme()) NightColors else DayColors
    val context = LocalContext.current
    // "Remove animations" in Accessibility sets the animator scale to 0.
    val reduced = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    CompositionLocalProvider(LocalHubColors provides hub, LocalReducedMotion provides reduced) {
        MaterialTheme(colorScheme = hub.toScheme(), typography = HubTypography, content = content)
    }
}

object Hub {
    val colors: HubColors
        @Composable @ReadOnlyComposable get() = LocalHubColors.current
}

private fun HubColors.toScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = dawn, onPrimary = onDawn,
        primaryContainer = dawnSoft.compositeOver(raised), onPrimaryContainer = ink,
        secondary = sky, onSecondary = onSky,
        secondaryContainer = skySoft.compositeOver(raised), onSecondaryContainer = ink,
        tertiary = sky, onTertiary = onSky,
        background = ground, onBackground = ink,
        surface = ground, onSurface = ink,
        surfaceVariant = raised2, onSurfaceVariant = inkMuted,
        surfaceContainerLowest = ground,
        surfaceContainerLow = raised,
        surfaceContainer = raised,
        surfaceContainerHigh = raised,
        surfaceContainerHighest = raised2,
        surfaceBright = raised2,
        surfaceDim = ground,
        surfaceTint = ground,
        inverseSurface = ink, inverseOnSurface = ground,
        outline = inkMuted.copy(alpha = 0.5f),
        outlineVariant = raised2,
        error = danger, onError = onDanger,
        errorContainer = dangerSoft.compositeOver(raised), onErrorContainer = ink,
        scrim = ground.copy(alpha = 0.6f),
    )
}
