package com.alsoug.keswa.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.alsoug.keswa.core.resources.Res
import com.alsoug.keswa.core.resources.ibm_plex_sans_arabic_medium
import com.alsoug.keswa.core.resources.ibm_plex_sans_arabic_regular
import com.alsoug.keswa.core.resources.ibm_plex_sans_arabic_semibold
import com.alsoug.keswa.core.resources.ibm_plex_sans_medium
import com.alsoug.keswa.core.resources.ibm_plex_sans_regular
import com.alsoug.keswa.core.resources.ibm_plex_sans_semibold
import org.jetbrains.compose.resources.Font

/**
 * IBM Plex, bundled — and one rule that matters more than the typeface.
 *
 * **Figures are tabular.** `fontFeatureSettings = "tnum"` makes every digit the same width, so a
 * column of prices lines up on the decimal point and the eye can compare two numbers without
 * reading them. A 1 narrower than a 7 is how a total gets misread at the end of a shift.
 *
 * The font ships with the app rather than being asked for by name. A till is an appliance and a
 * shop's Windows machine has whatever its OEM installed; `FontFamily.Default` meant the app looked
 * different in every shop, and the prototype's metrics — 13.5sp body, 1.45 line height — were
 * being applied to a typeface they were not drawn for.
 */
private const val TABULAR = "tnum"

/**
 * The Latin family.
 *
 * Three weights, which is what the scale below uses. Plex ships nine; shipping the six we do not
 * use would be a megabyte of installer for nothing.
 */
@Composable
fun plexSans(): FontFamily = FontFamily(
    Font(Res.font.ibm_plex_sans_regular, FontWeight.Normal, FontStyle.Normal),
    Font(Res.font.ibm_plex_sans_medium, FontWeight.Medium, FontStyle.Normal),
    Font(Res.font.ibm_plex_sans_semibold, FontWeight.SemiBold, FontStyle.Normal),
)

/**
 * The Arabic family, used for the whole interface when the language is Arabic.
 *
 * Not a fallback appended to the Latin family: IBM Plex Sans Arabic draws Latin too, and drawing
 * both scripts from one file is what keeps a bilingual line — "تيشيرت · KSW-TSH-022-NV" — sitting
 * on one baseline at one weight. Two families would render that in two typefaces.
 */
@Composable
fun plexSansArabic(): FontFamily = FontFamily(
    Font(Res.font.ibm_plex_sans_arabic_regular, FontWeight.Normal, FontStyle.Normal),
    Font(Res.font.ibm_plex_sans_arabic_medium, FontWeight.Medium, FontStyle.Normal),
    Font(Res.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold, FontStyle.Normal),
)

/** Money, quantities, receipt numbers — anything a person compares down a column. */
internal fun figureStyle(family: FontFamily) = TextStyle(
    fontFamily = family,
    fontFeatureSettings = TABULAR,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
)

/** The one number a screen is about: a basket total, a day's takings. */
internal fun figureLargeStyle(family: FontFamily) = TextStyle(
    fontFamily = family,
    fontFeatureSettings = TABULAR,
    fontWeight = FontWeight.SemiBold,
    fontSize = 28.sp,
    lineHeight = 34.sp,
)

internal fun keswaTypography(family: FontFamily): Typography {
    fun style(size: Int, line: Int, weight: FontWeight, tabular: Boolean = false) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontFeatureSettings = if (tabular) TABULAR else null,
    )

    return Typography(
        displaySmall = style(32, 38, FontWeight.SemiBold, tabular = true),
        headlineMedium = style(24, 30, FontWeight.SemiBold),
        headlineSmall = style(20, 26, FontWeight.SemiBold),
        titleLarge = style(18, 24, FontWeight.SemiBold),
        titleMedium = style(15, 21, FontWeight.Medium),
        titleSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
        ),
        bodyLarge = style(15, 22, FontWeight.Normal),
        bodyMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
        ),
        bodySmall = style(12, 17, FontWeight.Normal),
        labelLarge = style(13, 18, FontWeight.Medium),
        labelMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
        ),
    )
}
