package com.agam.pixelhub.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.agam.pixelhub.R

/*
 * One face, Poppins: geometric, round, friendly at large sizes and clear at small ones.
 * Hierarchy comes from size and weight, never from a second family.
 */
val Poppins = FontFamily(
    Font(R.font.poppins_light, FontWeight.Light),
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

/** Kept as names so call sites read by role. All three are Poppins. */
val NumeralFamily = Poppins
val HeadingFamily = Poppins
val TextFamily = Poppins

/** Tabular figures so rolling digits never shift sideways. */
private const val TNUM = "tnum"

object HubType {
    /** Ring screen. */
    val clockHuge = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 84.sp,
        lineHeight = 88.sp, letterSpacing = (-0.03).em, fontFeatureSettings = TNUM,
    )
    /** The big figure on a card: the next alarm, a balance. */
    val clockLarge = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 40.sp,
        lineHeight = 46.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TNUM,
    )
    /** Alarm rows. */
    val clockMedium = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 34.sp,
        lineHeight = 40.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TNUM,
    )
    /** Times and amounts inside lists. */
    val clockSmall = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 15.sp,
        lineHeight = 22.sp, fontFeatureSettings = TNUM,
    )
    /** Screen titles and the greeting. */
    val title = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 28.sp,
        lineHeight = 36.sp, letterSpacing = (-0.01).em,
    )
    /** Headings inside a screen or card. */
    val section = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 16.sp,
        lineHeight = 24.sp,
    )
    val body = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 23.sp)
    val bodyStrong = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 23.sp)
    val small = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp)
    val button = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
}

/** Material components (pickers, menus, dialogs) pick up the same face. */
val HubTypography: Typography = Typography().let { base ->
    fun TextStyle.p(w: FontWeight) = copy(fontFamily = Poppins, fontWeight = w)
    Typography(
        displayLarge = base.displayLarge.p(FontWeight.Medium),
        displayMedium = base.displayMedium.p(FontWeight.Medium),
        displaySmall = base.displaySmall.p(FontWeight.Medium),
        headlineLarge = base.headlineLarge.p(FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.p(FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.p(FontWeight.SemiBold),
        titleLarge = base.titleLarge.p(FontWeight.SemiBold),
        titleMedium = base.titleMedium.p(FontWeight.Medium),
        titleSmall = base.titleSmall.p(FontWeight.Medium),
        bodyLarge = base.bodyLarge.p(FontWeight.Normal),
        bodyMedium = base.bodyMedium.p(FontWeight.Normal),
        bodySmall = base.bodySmall.p(FontWeight.Normal),
        labelLarge = base.labelLarge.p(FontWeight.Medium),
        labelMedium = base.labelMedium.p(FontWeight.Medium),
        labelSmall = base.labelSmall.p(FontWeight.Medium),
    )
}
