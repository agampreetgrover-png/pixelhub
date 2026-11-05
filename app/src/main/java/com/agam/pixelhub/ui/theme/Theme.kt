package com.agam.pixelhub.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.agam.pixelhub.ui.motion.LocalReducedMotion

/** PixelHub's palette for the theme chosen in Settings (System follows the phone's dark mode). */
@Composable
fun PixelHubTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val theme by remember { ThemeStore.theme(context) }.collectAsState()
    val hub = theme.colors(isSystemInDarkTheme())
    // A forced light theme on a phone in dark mode (or the reverse) needs the status and
    // navigation bar icons flipped to stay visible, and the window behind Compose to match.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !hub.isDark
                isAppearanceLightNavigationBars = !hub.isDark
            }
            window.setBackgroundDrawable(ColorDrawable(hub.ground.toArgb()))
        }
    }
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
