package com.agam.pixelhub.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * PixelHub's own palette. Two hues carry meaning everywhere in the app:
 * dawn = alarms (and the primary action), sky = reminders.
 * Surfaces are separated by tone steps, never by lines or shadows.
 */
@Immutable
data class HubColors(
    val isDark: Boolean,
    /** The page. */
    val ground: Color,
    /** One step up from the ground: sheets, the tab bar, input fields. */
    val raised: Color,
    /** Two steps up: pressed states, chips, the dial track. */
    val raised2: Color,
    val ink: Color,
    val inkMuted: Color,
    /** Alarms, and the primary action. */
    val dawn: Color,
    val onDawn: Color,
    /** Reminders. */
    val sky: Color,
    val onSky: Color,
    val ok: Color,
    val danger: Color,
    val onDanger: Color,
) {
    val dawnSoft: Color get() = dawn.copy(alpha = if (isDark) 0.16f else 0.12f)
    val skySoft: Color get() = sky.copy(alpha = if (isDark) 0.16f else 0.12f)
    val dangerSoft: Color get() = danger.copy(alpha = if (isDark) 0.16f else 0.10f)
}

val NightColors = HubColors(
    isDark = true,
    ground = Color(0xFF0E1525),
    raised = Color(0xFF172036),
    raised2 = Color(0xFF212C46),
    ink = Color(0xFFEDF0F7),
    inkMuted = Color(0xFF98A2BA),
    dawn = Color(0xFFFFB36B),
    onDawn = Color(0xFF2B1500),
    sky = Color(0xFF86BFFF),
    onSky = Color(0xFF04213F),
    ok = Color(0xFF8FD6A8),
    danger = Color(0xFFFF8A80),
    onDanger = Color(0xFF3B0603),
)

val DayColors = HubColors(
    isDark = false,
    ground = Color(0xFFEEF1F7),
    raised = Color(0xFFFFFFFF),
    raised2 = Color(0xFFE1E6EF),
    ink = Color(0xFF121A2B),
    inkMuted = Color(0xFF556079),
    dawn = Color(0xFFB25500),
    onDawn = Color(0xFFFFFFFF),
    sky = Color(0xFF1D63C4),
    onSky = Color(0xFFFFFFFF),
    ok = Color(0xFF2E7D4F),
    danger = Color(0xFFC0362C),
    onDanger = Color(0xFFFFFFFF),
)

val LocalHubColors = staticCompositionLocalOf { NightColors }

/**
 * The colour of the sky at a given hour, used by the 24-hour dial. It is data
 * (where daylight falls), not decoration.
 */
object Sky {
    private val keys = listOf(
        0f to Color(0xFF1B2550),
        4.5f to Color(0xFF26346A),
        6f to Color(0xFFFF9E6B),
        8f to Color(0xFFFFD08A),
        12f to Color(0xFF8EC9FF),
        16.5f to Color(0xFF7FB2F0),
        18.75f to Color(0xFFFF8F6B),
        20.25f to Color(0xFF35407C),
        24f to Color(0xFF1B2550),
    )

    fun at(hour: Float): Color {
        val h = ((hour % 24f) + 24f) % 24f
        val i = keys.indexOfLast { it.first <= h }.coerceIn(0, keys.size - 2)
        val (h0, c0) = keys[i]
        val (h1, c1) = keys[i + 1]
        return lerp(c0, c1, ((h - h0) / (h1 - h0)).coerceIn(0f, 1f))
    }
}
