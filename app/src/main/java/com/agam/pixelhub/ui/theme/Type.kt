@file:OptIn(ExperimentalTextApi::class)

package com.agam.pixelhub.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.agam.pixelhub.R

/*
 * Two faces, clearly distinct:
 *  - Anybody (variable width) for clock numerals and headings: condensed and light
 *    for time, wide and heavy for titles.
 *  - Instrument Sans for everything you read.
 */

private fun anybody(weight: Int, width: Float) = Font(
    R.font.anybody,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
)

private fun instrument(weight: Int) = Font(
    R.font.instrument_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(100f)),
)

/** Condensed clock numerals. */
val NumeralFamily = FontFamily(anybody(300, 72f), anybody(400, 72f), anybody(500, 72f), anybody(700, 72f))

/** Wide, heavy headings. */
val HeadingFamily = FontFamily(anybody(600, 118f), anybody(800, 125f))

val TextFamily = FontFamily(instrument(400), instrument(500), instrument(600), instrument(700))

/** Tabular figures so rolling digits never shift sideways. */
private const val TNUM = "tnum"

object HubType {
    /** Ring screen and the dial's centre. */
    val clockHuge = TextStyle(
        fontFamily = NumeralFamily, fontWeight = FontWeight(300), fontSize = 96.sp,
        lineHeight = 96.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TNUM,
    )
    val clockLarge = TextStyle(
        fontFamily = NumeralFamily, fontWeight = FontWeight(300), fontSize = 64.sp,
        lineHeight = 64.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TNUM,
    )
    /** Alarm rows. */
    val clockMedium = TextStyle(
        fontFamily = NumeralFamily, fontWeight = FontWeight(400), fontSize = 44.sp,
        lineHeight = 44.sp, letterSpacing = (-0.01).em, fontFeatureSettings = TNUM,
    )
    /** Times inside lists. */
    val clockSmall = TextStyle(
        fontFamily = NumeralFamily, fontWeight = FontWeight(500), fontSize = 20.sp,
        lineHeight = 24.sp, fontFeatureSettings = TNUM,
    )
    /** Screen titles. */
    val title = TextStyle(
        fontFamily = HeadingFamily, fontWeight = FontWeight(800), fontSize = 34.sp,
        lineHeight = 38.sp, letterSpacing = (-0.03).em,
    )
    /** Section headings inside a screen. */
    val section = TextStyle(
        fontFamily = HeadingFamily, fontWeight = FontWeight(600), fontSize = 19.sp,
        lineHeight = 24.sp, letterSpacing = (-0.01).em,
    )
    val body = TextStyle(fontFamily = TextFamily, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 23.sp)
    val bodyStrong = TextStyle(fontFamily = TextFamily, fontWeight = FontWeight(600), fontSize = 16.sp, lineHeight = 23.sp)
    val small = TextStyle(fontFamily = TextFamily, fontWeight = FontWeight(500), fontSize = 13.sp, lineHeight = 18.sp)
    val button = TextStyle(fontFamily = TextFamily, fontWeight = FontWeight(600), fontSize = 16.sp, lineHeight = 20.sp)
}

/** Material components (pickers, menus, dialogs) pick up the same faces. */
val HubTypography: Typography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = NumeralFamily, fontWeight = FontWeight(300)),
        displayMedium = base.displayMedium.copy(fontFamily = NumeralFamily, fontWeight = FontWeight(300)),
        displaySmall = base.displaySmall.copy(fontFamily = NumeralFamily, fontWeight = FontWeight(400)),
        headlineLarge = base.headlineLarge.copy(fontFamily = HeadingFamily, fontWeight = FontWeight(800)),
        headlineMedium = base.headlineMedium.copy(fontFamily = HeadingFamily, fontWeight = FontWeight(800)),
        headlineSmall = base.headlineSmall.copy(fontFamily = HeadingFamily, fontWeight = FontWeight(600)),
        titleLarge = base.titleLarge.copy(fontFamily = HeadingFamily, fontWeight = FontWeight(600)),
        titleMedium = base.titleMedium.copy(fontFamily = TextFamily, fontWeight = FontWeight(600)),
        titleSmall = base.titleSmall.copy(fontFamily = TextFamily, fontWeight = FontWeight(600)),
        bodyLarge = base.bodyLarge.copy(fontFamily = TextFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = TextFamily),
        bodySmall = base.bodySmall.copy(fontFamily = TextFamily),
        labelLarge = base.labelLarge.copy(fontFamily = TextFamily, fontWeight = FontWeight(600)),
        labelMedium = base.labelMedium.copy(fontFamily = TextFamily, fontWeight = FontWeight(500)),
        labelSmall = base.labelSmall.copy(fontFamily = TextFamily, fontWeight = FontWeight(500)),
    )
}
