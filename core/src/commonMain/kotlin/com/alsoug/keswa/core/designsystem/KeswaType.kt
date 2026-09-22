package com.alsoug.keswa.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * A denser scale than Material's default, and one rule that matters more than the rest.
 *
 * **Figures are tabular.** `fontFeatureSettings = "tnum"` makes every digit the same width, so a
 * column of prices lines up on the decimal point and the eye can compare two numbers without
 * reading them. Proportional digits in a totals column are the difference between a till that
 * looks like an instrument and one that looks like a web page — and a 1 that is narrower than a 7
 * is how somebody misreads a total at the end of a shift.
 *
 * The prototype specifies IBM Plex. No font files ship with the repo, so this uses the platform's
 * own and keeps the metrics: 13.5sp body, 1.45 line height. Dropping the Plex files into
 * `composeResources` later changes this file and nothing else.
 */
private const val TABULAR = "tnum"

private val Default = FontFamily.Default

/** Money, quantities, receipt numbers — anything a person compares down a column. */
val FigureStyle = TextStyle(
    fontFamily = Default,
    fontFeatureSettings = TABULAR,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
)

/** The one number a screen is about: a basket total, a day's takings. */
val FigureLargeStyle = TextStyle(
    fontFamily = Default,
    fontFeatureSettings = TABULAR,
    fontWeight = FontWeight.SemiBold,
    fontSize = 28.sp,
    lineHeight = 34.sp,
)

internal val KeswaTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Default,
        fontFeatureSettings = TABULAR,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 15.sp,
    ),
)
