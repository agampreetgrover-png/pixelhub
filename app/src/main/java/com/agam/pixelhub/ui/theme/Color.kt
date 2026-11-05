package com.agam.pixelhub.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * PixelHub's palette: a neutral ground, white (or near-black) cards, black chrome, and two
 * saturated card colours that carry meaning everywhere in the app:
 * violet = alarms, sun = reminders. Surfaces are separated by tone steps, not lines or shadows.
 */
@Immutable
data class HubColors(
    val isDark: Boolean,
    /** The page. */
    val ground: Color,
    /** Cards, sheets, input fields. */
    val raised: Color,
    /** Chips, tracks, pressed and disabled fills. */
    val raised2: Color,
    /** The round wells inside a card that hold an icon (quick actions, list rows). */
    val well: Color,
    val ink: Color,
    val inkMuted: Color,
    /** Alarms and the violet card. */
    val dawn: Color,
    val onDawn: Color,
    /** Reminders, as text or an icon tint (stays legible on the page). */
    val sky: Color,
    val onSky: Color,
    /** Reminders, as a filled card. */
    val sun: Color,
    val onSun: Color,
    /** The black pill: the tab bar and the main action of a screen. */
    val chrome: Color,
    val onChrome: Color,
    /** Round buttons sitting inside the chrome pill. */
    val chromeWell: Color,
    val ok: Color,
    val danger: Color,
    val onDanger: Color,
) {
    val dawnSoft: Color get() = dawn.copy(alpha = if (isDark) 0.20f else 0.12f)
    val skySoft: Color get() = sun.copy(alpha = if (isDark) 0.18f else 0.30f)
    val dangerSoft: Color get() = danger.copy(alpha = if (isDark) 0.16f else 0.10f)
}

val NightColors = HubColors(
    isDark = true,
    ground = Color(0xFF000000),
    raised = Color(0xFF1A1A1A),
    raised2 = Color(0xFF2A2A2A),
    well = Color(0xFF050505),
    ink = Color(0xFFF5F5F5),
    inkMuted = Color(0xFF9C9C9C),
    dawn = Color(0xFF9D4EDD),
    onDawn = Color(0xFFFFFFFF),
    sky = Color(0xFFFFD15C),
    onSky = Color(0xFF1A1400),
    sun = Color(0xFFFFD15C),
    onSun = Color(0xFF141414),
    chrome = Color(0xFF262626),
    onChrome = Color(0xFFFFFFFF),
    chromeWell = Color(0xFF3D3D3D),
    ok = Color(0xFF7FD6A0),
    danger = Color(0xFFFF8A80),
    onDanger = Color(0xFF3B0603),
)

val DayColors = HubColors(
    isDark = false,
    ground = Color(0xFFEDEDED),
    raised = Color(0xFFFFFFFF),
    raised2 = Color(0xFFF0F0F0),
    well = Color(0xFFF0F0F0),
    ink = Color(0xFF111111),
    inkMuted = Color(0xFF636363),
    dawn = Color(0xFF9A35E0),
    onDawn = Color(0xFFFFFFFF),
    sky = Color(0xFF8C6200),
    onSky = Color(0xFFFFFFFF),
    sun = Color(0xFFFFD15C),
    onSun = Color(0xFF141414),
    chrome = Color(0xFF111111),
    onChrome = Color(0xFFFFFFFF),
    chromeWell = Color(0xFF2B2B2B),
    ok = Color(0xFF1E7B46),
    danger = Color(0xFFC0362C),
    onDanger = Color(0xFFFFFFFF),
)

val LocalHubColors = staticCompositionLocalOf { NightColors }

/**
 * The colour of the sky at a given hour, used behind the ring screen. It is data
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
