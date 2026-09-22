package com.alsoug.keswa.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * The palette, lifted from `design/keswa-pos-prototype.html`.
 *
 * The prototype has been the design of record since before Phase 0 and the app had never
 * implemented it: `App.kt` called `MaterialTheme { }` with no arguments, so every screen wore
 * Material's baseline purple on cool grey, and the charts — which *did* use the prototype's
 * colours — sat on surfaces from a different system entirely.
 *
 * The neutrals are warm on purpose. A shop's light is warm, the goods are fabric, and a cool grey
 * screen beside them looks like a spreadsheet. The accents are a blue/orange pair, which stays
 * distinguishable for the ~8% of men with red-green colour vision deficiency — in a clothing shop
 * the staff look at colour all day and some of them will not see it the way the designer did.
 */
internal object Palette {

    // Light — warm paper.
    val Page = Color(0xFFF9F9F7)
    val Surface = Color(0xFFFCFCFB)
    val Sunk = Color(0xFFF2F1ED)
    val Ink = Color(0xFF0B0B0B)
    val Ink2 = Color(0xFF52514E)
    val Muted = Color(0xFF898781)
    val Grid = Color(0xFFE1E0D9)
    val Axis = Color(0xFFC3C2B7)

    // Dark — near-black, not blue-black.
    val PageDark = Color(0xFF0D0D0D)
    val SurfaceDark = Color(0xFF1A1A19)
    val SunkDark = Color(0xFF141413)
    val InkDark = Color(0xFFFFFFFF)
    val Ink2Dark = Color(0xFFC3C2B7)
    val GridDark = Color(0xFF2C2C2A)
    val AxisDark = Color(0xFF383835)

    // Series. S1 is what sold, S2 is what is still on hand — the same meaning on every panel.
    val S1 = Color(0xFF2A78D6)
    val S2 = Color(0xFFEB6834)
    val S1Dark = Color(0xFF3987E5)
    val S2Dark = Color(0xFFD95926)

    val Good = Color(0xFF0CA30C)
    val GoodInk = Color(0xFF006300)
    val Warn = Color(0xFFFAB219)
    val Crit = Color(0xFFD03B3B)
    val CritDark = Color(0xFFE05A5A)

    /**
     * The sequential ramp, seven steps, light→dark in light mode and dark→light in dark mode.
     *
     * Reversed rather than reused, so "more" is always the higher-contrast end against whatever the
     * page is. A ramp that runs pale-to-dark on a black background reads inside out.
     */
    val Ramp = listOf(
        Color(0xFFCDE2FB), Color(0xFF9EC5F4), Color(0xFF6DA7EC), Color(0xFF3987E5),
        Color(0xFF256ABF), Color(0xFF184F95), Color(0xFF0D366B),
    )
    val RampDark = Ramp.reversed()
}
