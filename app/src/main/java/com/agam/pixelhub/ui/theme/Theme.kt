package com.agam.pixelhub.ui.theme

import android.app.Activity
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion

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
    // "Remove animations" in Accessibility sets the animator scale to 0. Re-read on every resume:
    // flipping it isn't a configuration change, so the activity is never recreated for it.
    fun animationsOff() =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember { mutableStateOf(animationsOff()) }
    LifecycleResumeEffect(Unit) {
        reduced = animationsOff()
        onPauseOrDispose { }
    }
    // Switching theme in Settings crossfades every colour instead of snapping. Colour change
    // isn't travel, so it stays on under reduced motion; at launch start equals target.
    val shown = animateHubColors(hub)
    // Material's sheets and dialogs pick their bar icon colours from isSystemInDarkTheme(),
    // which reads the configuration. Make its night bit follow the app theme, not the phone.
    val base = LocalConfiguration.current
    val config = remember(base, hub.isDark) {
        Configuration(base).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (hub.isDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        }
    }
    CompositionLocalProvider(
        LocalConfiguration provides config,
        LocalHubColors provides shown,
        LocalReducedMotion provides reduced,
    ) {
        MaterialTheme(colorScheme = shown.toScheme(), typography = HubTypography, content = content)
    }
}

@Composable
private fun animateHubColors(target: HubColors): HubColors = target.copy(
    ground = fade(target.ground),
    raised = fade(target.raised),
    raised2 = fade(target.raised2),
    well = fade(target.well),
    ink = fade(target.ink),
    inkMuted = fade(target.inkMuted),
    dawn = fade(target.dawn),
    onDawn = fade(target.onDawn),
    sky = fade(target.sky),
    onSky = fade(target.onSky),
    sun = fade(target.sun),
    onSun = fade(target.onSun),
    chrome = fade(target.chrome),
    onChrome = fade(target.onChrome),
    chromeWell = fade(target.chromeWell),
    ok = fade(target.ok),
    danger = fade(target.danger),
    onDanger = fade(target.onDanger),
)

@Composable
private fun fade(c: Color): Color =
    animateColorAsState(c, tween(280, easing = Motion.EaseOut), label = "theme").value

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
        // Material applies its own alpha; a dark scrim dims in every theme.
        scrim = Color.Black,
    )
}
