package com.alsoug.keswa.core.designsystem

import androidx.compose.ui.unit.LayoutDirection

/**
 * The two languages the shop works in.
 *
 * Not a locale. A locale carries number formats, calendars and collation, and this app deliberately
 * formats money itself (`Money.format`) so a piastre never goes through a locale-aware formatter
 * that might introduce a decimal comma. What changes here is the script, the reading direction and
 * the words — nothing arithmetic.
 */
enum class KeswaLanguage(val code: String, val endonym: String) {
    ENGLISH("en", "English"),
    ARABIC("ar", "العربية"),
    ;

    val layoutDirection: LayoutDirection
        get() = if (this == ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr

    companion object {
        fun ofCode(code: String?): KeswaLanguage =
            entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}
