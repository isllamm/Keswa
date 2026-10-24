package com.alsoug.keswa.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * Colours the shop needs that Material 3 has no slot for.
 *
 * Reached through [KeswaTheme.semantics] rather than by importing [Palette], so a screen can never
 * hard-code a hex and quietly opt out of dark mode — which is exactly how the analytics charts came
 * to carry cool-grey neutrals while the prototype specified warm ones.
 */
@Immutable
data class KeswaSemantics(
    /** Recessed panels: a totals strip, a table header, an inset list. */
    val sunk: Color,
    /** Hairlines between rows, where a full divider would be too loud. */
    val hair: Color,
    /** Axis text and anything deliberately quiet. */
    val muted: Color,
    val grid: Color,
    val axis: Color,
    /** Series 1 — sold, taken, out. */
    val sold: Color,
    /** Series 2 — on hand, remaining, in. */
    val onHand: Color,
    val good: Color,
    val onGood: Color,
    val warning: Color,
    val critical: Color,
    /** Seven steps, pale→strong against the current background. */
    val ramp: List<Color>,
) {
    /** A step of [ramp] for a 0..1 intensity. Heat maps and sell-through buckets use this. */
    fun rampStep(fraction: Float): Color =
        ramp[(fraction.coerceIn(0f, 1f) * (ramp.size - 1)).toInt()]
}

private val LightSemantics = KeswaSemantics(
    sunk = Palette.Sunk,
    hair = Palette.Ink.copy(alpha = 0.10f),
    muted = Palette.Muted,
    grid = Palette.Grid,
    axis = Palette.Axis,
    sold = Palette.S1,
    onHand = Palette.S2,
    good = Palette.Good,
    onGood = Palette.GoodInk,
    warning = Palette.Warn,
    critical = Palette.Crit,
    ramp = Palette.Ramp,
)

private val DarkSemantics = KeswaSemantics(
    sunk = Palette.SunkDark,
    hair = Color.White.copy(alpha = 0.10f),
    muted = Palette.Muted,
    grid = Palette.GridDark,
    axis = Palette.AxisDark,
    sold = Palette.S1Dark,
    onHand = Palette.S2Dark,
    good = Palette.Good,
    onGood = Palette.Good,
    warning = Palette.Warn,
    critical = Palette.CritDark,
    ramp = Palette.RampDark,
)

private val LightColours = lightColorScheme(
    primary = Palette.S1,
    onPrimary = Color.White,
    primaryContainer = Palette.Ramp.first(),
    onPrimaryContainer = Palette.Ramp.last(),
    secondary = Palette.S2,
    onSecondary = Color.White,
    // Deliberately a warm neutral rather than a tint of `secondary`. Tonal buttons are the app's
    // quiet actions — Hold, Discount — and a pale orange one reads as a warning on a till.
    secondaryContainer = Color(0xFFEDEBE3),
    onSecondaryContainer = Palette.Ink,
    tertiary = Palette.Good,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCF2DC),
    onTertiaryContainer = Palette.GoodInk,
    background = Palette.Page,
    onBackground = Palette.Ink,
    surface = Palette.Surface,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Sunk,
    onSurfaceVariant = Palette.Ink2,
    // All five containers, not the three that happened to be visible. `Card` takes its default
    // from `surfaceContainerHighest`, so leaving that one unset is how the shift banner stayed
    // lavender on a warm page long after the theme was "done".
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.Sunk,
    surfaceContainerHigh = Color(0xFFEFEEE8),
    surfaceContainerHighest = Color(0xFFEBEAE3),
    surfaceBright = Palette.Surface,
    surfaceDim = Color(0xFFECEBE5),
    outline = Palette.Axis,
    outlineVariant = Palette.Grid,
    error = Palette.Crit,
    onError = Color.White,
    errorContainer = Color(0xFFFBE4E4),
    onErrorContainer = Color(0xFF6E1414),
    // Material tints raised surfaces with this. Left unset it is the baseline purple, which is
    // how a card at elevation 1 quietly goes lilac on a warm page.
    surfaceTint = Palette.S1,
    // Snackbars invert. Unset, they arrive in Material's dark violet.
    inverseSurface = Palette.PageDark,
    inverseOnSurface = Palette.Page,
    inversePrimary = Palette.Ramp[1],
    scrim = Palette.Ink,
)

private val DarkColours = darkColorScheme(
    primary = Palette.S1Dark,
    onPrimary = Color.White,
    primaryContainer = Palette.RampDark.last(),
    onPrimaryContainer = Palette.RampDark.first(),
    secondary = Palette.S2Dark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF262624),
    onSecondaryContainer = Palette.InkDark,
    tertiary = Palette.Good,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF13330F),
    onTertiaryContainer = Color(0xFF7FD67F),
    background = Palette.PageDark,
    onBackground = Palette.InkDark,
    surface = Palette.SurfaceDark,
    onSurface = Palette.InkDark,
    surfaceVariant = Palette.SunkDark,
    onSurfaceVariant = Palette.Ink2Dark,
    surfaceContainerLowest = Color(0xFF080808),
    surfaceContainerLow = Palette.SunkDark,
    surfaceContainer = Palette.SurfaceDark,
    surfaceContainerHigh = Color(0xFF242422),
    surfaceContainerHighest = Palette.GridDark,
    surfaceBright = Color(0xFF2C2C2A),
    surfaceDim = Palette.PageDark,
    outline = Palette.AxisDark,
    outlineVariant = Palette.GridDark,
    error = Palette.CritDark,
    onError = Color.Black,
    errorContainer = Color(0xFF3A1414),
    onErrorContainer = Color(0xFFF6C5C5),
    surfaceTint = Palette.S1Dark,
    inverseSurface = Palette.Page,
    inverseOnSurface = Palette.Ink,
    inversePrimary = Palette.RampDark[1],
    scrim = Color.Black,
)

/**
 * Squarer than Material's default.
 *
 * A till is a dense instrument, and 16dp corners on a row that is 32dp tall eat the row. This is
 * also what makes a table of figures read as a table rather than as a stack of pills.
 */
private val KeswaShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(5.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(7.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
)

private val LocalSemantics = staticCompositionLocalOf { LightSemantics }
private val LocalStrings = staticCompositionLocalOf<Strings> { EnglishStrings }
private val LocalLanguage = staticCompositionLocalOf { KeswaLanguage.ARABIC }
private val LocalFigure = staticCompositionLocalOf<TextStyle> {
    error("no figure style: something is drawing outside KeswaTheme")
}
private val LocalFigureLarge = staticCompositionLocalOf<TextStyle> {
    error("no figure style: something is drawing outside KeswaTheme")
}

/**
 * The app's theme. Wrap everything in it — including previews, or a preview lies about what ships.
 *
 * [language] decides three things at once, and they belong together: the words, the script's own
 * typeface, and the reading direction. Setting any one without the others is how an app ends up
 * with Arabic text laid out left to right, or a right-to-left layout still reading "Take payment".
 *
 * `LayoutDirection` is provided here rather than left to the platform, because the shop chooses the
 * language — not the machine. A till in Cairo running an English Windows install still has Arabic
 * staff standing at it.
 */
@Composable
fun KeswaTheme(
    dark: Boolean = isSystemInDarkTheme(),
    language: KeswaLanguage = KeswaLanguage.ARABIC,
    content: @Composable () -> Unit,
) {
    val family = if (language == KeswaLanguage.ARABIC) plexSansArabic() else plexSans()

    CompositionLocalProvider(
        LocalSemantics provides if (dark) DarkSemantics else LightSemantics,
        LocalStrings provides stringsFor(language),
        LocalLanguage provides language,
        LocalFigure provides figureStyle(family),
        LocalFigureLarge provides figureLargeStyle(family),
        LocalLayoutDirection provides language.layoutDirection,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColours else LightColours,
            typography = keswaTypography(family),
            shapes = KeswaShapes,
            content = content,
        )
    }
}

object KeswaTheme {
    val semantics: KeswaSemantics
        @Composable @ReadOnlyComposable get() = LocalSemantics.current

    /** Every word the interface says. */
    val strings: Strings
        @Composable @ReadOnlyComposable get() = LocalStrings.current

    val language: KeswaLanguage
        @Composable @ReadOnlyComposable get() = LocalLanguage.current

    /** Tabular. Money, quantities, anything compared down a column. */
    val figure: TextStyle
        @Composable @ReadOnlyComposable get() = LocalFigure.current

    /** Tabular, and the size of the one number a screen is about. */
    val figureLarge: TextStyle
        @Composable @ReadOnlyComposable get() = LocalFigureLarge.current
}
