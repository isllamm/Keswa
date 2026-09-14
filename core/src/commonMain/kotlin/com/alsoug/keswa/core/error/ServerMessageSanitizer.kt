package com.alsoug.keswa.core.error

/**
 * Returns a safe server message for direct UI display.
 *
 * Mirrors [com.alsoug.cashi.features.form.presentation.FormErrorMapper] —
 * rejects JSON/HTML bodies that must not be shown (PCI / deserialization safety).
 */
fun String?.toSafeServerMessage(): String? {
    val trimmed = this?.trim().orEmpty()
    return trimmed.takeIf {
        it.isNotEmpty() && !it.startsWith("{") && !it.startsWith("<")
    }
}
