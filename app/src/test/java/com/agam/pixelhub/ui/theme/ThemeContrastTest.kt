package com.agam.pixelhub.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every theme must keep text readable: WCAG 4.5:1 for text, 3:1 for icons and accents. */
class ThemeContrastTest {
    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance().toDouble(), b.luminance().toDouble()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun everyThemeMeetsContrast() {
        val failures = mutableListOf<String>()
        AppTheme.entries.flatMap { listOf(it.label + " day" to it.day, it.label + " night" to it.night) }
            .distinctBy { it.second }
            .forEach { (name, p) ->
                // Same rule as AlarmsScreen's dawnCard: dark themes deepen the alarm card.
                val card = if (p.isDark) lerp(p.dawn, Color.Black, 0.16f) else p.dawn
                listOf(
                    Triple("ink on ground", contrast(p.ink, p.ground), 4.5),
                    Triple("ink on raised2", contrast(p.ink, p.raised2), 4.5),
                    Triple("muted on raised", contrast(p.inkMuted, p.raised), 4.5),
                    Triple("muted on ground", contrast(p.inkMuted, p.ground), 4.5),
                    Triple("onDawn on alarm card", contrast(p.onDawn, card), 4.5),
                    Triple("accent on raised", contrast(p.dawn, p.raised), 3.0),
                    Triple("onSun on sun", contrast(p.onSun, p.sun), 4.5),
                    Triple("sky on raised", contrast(p.sky, p.raised), 4.5),
                    Triple("onChrome on chrome", contrast(p.onChrome, p.chrome), 4.5),
                    Triple("danger on raised", contrast(p.danger, p.raised), 4.5),
                    Triple("onDanger on danger", contrast(p.onDanger, p.danger), 4.5),
                ).filter { it.second < it.third }
                    .forEach { failures += "$name: ${it.first} %.2f < ${it.third}".format(it.second) }
            }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun atLeastEightThemesBesidesSystem() {
        assertTrue(AppTheme.entries.count { it != AppTheme.SYSTEM } >= 8)
    }
}
