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

/*
 * The other themes keep the same meaning for every token. Each one was checked for contrast:
 * body text and muted text ≥ 4.5:1 on the page and cards, white on the alarm card ≥ 4.5:1,
 * the accent ≥ 3:1 as an icon. Dark themes deepen the alarm card a little (see dawnCard).
 */

/** Dark grey rather than pure black: softer on the eyes in a lit room. */
val DarkColors = HubColors(
    isDark = true,
    ground = Color(0xFF121214),
    raised = Color(0xFF1E1E22),
    raised2 = Color(0xFF2C2C31),
    well = Color(0xFF17171A),
    ink = Color(0xFFF2F2F4),
    inkMuted = Color(0xFFA0A0A8),
    dawn = Color(0xFF9D4EDD),
    onDawn = Color(0xFFFFFFFF),
    sky = Color(0xFFFFD15C),
    onSky = Color(0xFF1A1400),
    sun = Color(0xFFFFD15C),
    onSun = Color(0xFF141414),
    chrome = Color(0xFF2A2A2F),
    onChrome = Color(0xFFFFFFFF),
    chromeWell = Color(0xFF3F3F46),
    ok = Color(0xFF7FD6A0),
    danger = Color(0xFFFF8A80),
    onDanger = Color(0xFF3B0603),
)

/** Deep navy with an indigo accent. */
val MidnightColors = HubColors(
    isDark = true,
    ground = Color(0xFF0B1020),
    raised = Color(0xFF151B30),
    raised2 = Color(0xFF232B45),
    well = Color(0xFF0F1527),
    ink = Color(0xFFEEF1FA),
    inkMuted = Color(0xFF9DA5BF),
    dawn = Color(0xFF6E62F0),
    onDawn = Color(0xFFFFFFFF),
    sky = Color(0xFFFFC94D),
    onSky = Color(0xFF1A1400),
    sun = Color(0xFFFFC94D),
    onSun = Color(0xFF141414),
    chrome = Color(0xFF252D4A),
    onChrome = Color(0xFFFFFFFF),
    chromeWell = Color(0xFF36405F),
    ok = Color(0xFF7FD6A0),
    danger = Color(0xFFFF8A80),
    onDanger = Color(0xFF3B0603),
)

/** Dark pine green with an emerald accent. */
val ForestColors = HubColors(
    isDark = true,
    ground = Color(0xFF0D1512),
    raised = Color(0xFF16211C),
    raised2 = Color(0xFF23312A),
    well = Color(0xFF111A16),
    ink = Color(0xFFEAF3EE),
    inkMuted = Color(0xFF9AACA2),
    dawn = Color(0xFF2A8A5E),
    onDawn = Color(0xFFFFFFFF),
    sky = Color(0xFFF2CF5B),
    onSky = Color(0xFF1A1400),
    sun = Color(0xFFF2CF5B),
    onSun = Color(0xFF141414),
    chrome = Color(0xFF20302A),
    onChrome = Color(0xFFFFFFFF),
    chromeWell = Color(0xFF30443B),
    ok = Color(0xFF8FD9A8),
    danger = Color(0xFFFF8A80),
    onDanger = Color(0xFF3B0603),
)

/** Dark aubergine with an orchid accent. */
val PlumColors = HubColors(
    isDark = true,
    ground = Color(0xFF15101C),
    raised = Color(0xFF211A2B),
    raised2 = Color(0xFF30273D),
    well = Color(0xFF191320),
    ink = Color(0xFFF3EEFA),
    inkMuted = Color(0xFFAA9DBA),
    dawn = Color(0xFFB04FD6),
    onDawn = Color(0xFFFFFFFF),
    sky = Color(0xFFFFCF66),
    onSky = Color(0xFF1A1400),
    sun = Color(0xFFFFCF66),
    onSun = Color(0xFF141414),
    chrome = Color(0xFF2D2339),
    onChrome = Color(0xFFFFFFFF),
    chromeWell = Color(0xFF403352),
    ok = Color(0xFF7FD6A0),
    danger = Color(0xFFFF8A80),
    onDanger = Color(0xFF3B0603),
)

/** Light, cool blue-grey with a cobalt accent. */
val OceanColors = HubColors(
    isDark = false,
    ground = Color(0xFFE4EDF5),
    raised = Color(0xFFFFFFFF),
    raised2 = Color(0xFFEDF2F7),
    well = Color(0xFFEDF2F7),
    ink = Color(0xFF0E1A26),
    inkMuted = Color(0xFF536170),
    dawn = Color(0xFF1D6BCB),
    onDawn = Color(0xFFFFFFFF),
    sky = Color(0xFF835C00),
    onSky = Color(0xFFFFFFFF),
    sun = Color(0xFFFFD15C),
    onSun = Color(0xFF141414),
    chrome = Color(0xFF0E2A47),
    onChrome = Color(0xFFFFFFFF),
    chromeWell = Color(0xFF1E3D60),
    ok = Color(0xFF1E7B46),
    danger = Color(0xFFC0362C),
    onDanger = Color(0xFFFFFFFF),
)

/** Light blush with a raspberry accent. */
val RoseColors = HubColors(
    isDark = false,
    ground = Color(0xFFF6E8EC),
    raised = Color(0xFFFFFFFF),
    raised2 = Color(0xFFF8EEF1),
    well = Color(0xFFF8EEF1),
    ink = Color(0xFF22121A),
    inkMuted = Color(0xFF6E5560),
    dawn = Color(0xFFC2185B),
    onDawn = Color(0xFFFFFFFF),
    sky = Color(0xFF835C00),
    onSky = Color(0xFFFFFFFF),
    sun = Color(0xFFFFD15C),
    onSun = Color(0xFF141414),
    chrome = Color(0xFF3A1426),
    onChrome = Color(0xFFFFFFFF),
    chromeWell = Color(0xFF552238),
    ok = Color(0xFF1E7B46),
    danger = Color(0xFFB3261E),
    onDanger = Color(0xFFFFFFFF),
)

/** Warm paper with a terracotta accent. */
val SandColors = HubColors(
    isDark = false,
    ground = Color(0xFFEFE6D8),
    raised = Color(0xFFFBF7F0),
    raised2 = Color(0xFFF1EADF),
    well = Color(0xFFF1EADF),
    ink = Color(0xFF2A2118),
    inkMuted = Color(0xFF685A49),
    dawn = Color(0xFF9A4A1C),
    onDawn = Color(0xFFFFFFFF),
    sky = Color(0xFF765300),
    onSky = Color(0xFFFFFFFF),
    sun = Color(0xFFF2C14E),
    onSun = Color(0xFF141414),
    chrome = Color(0xFF2A2118),
    onChrome = Color(0xFFFFFFFF),
    chromeWell = Color(0xFF43372A),
    ok = Color(0xFF2F7A3E),
    danger = Color(0xFFB3402F),
    onDanger = Color(0xFFFFFFFF),
)

/** The themes offered in Settings. [SYSTEM] follows the phone: Light by day, Black at night. */
enum class AppTheme(val label: String, val day: HubColors, val night: HubColors = day) {
    SYSTEM("System", DayColors, NightColors),
    LIGHT("Light", DayColors),
    DARK("Dark", DarkColors),
    BLACK("Black", NightColors),
    MIDNIGHT("Midnight", MidnightColors),
    FOREST("Forest", ForestColors),
    PLUM("Plum", PlumColors),
    OCEAN("Ocean", OceanColors),
    ROSE("Rose", RoseColors),
    SAND("Sand", SandColors),
    ;

    fun colors(systemDark: Boolean): HubColors = if (systemDark) night else day
}

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
