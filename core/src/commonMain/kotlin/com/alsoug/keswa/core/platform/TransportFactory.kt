package com.alsoug.keswa.core.platform

/**
 * Builds a transport for a given address, so host and port can come from settings at call time.
 *
 * In `:core` rather than beside the settings screen because every feature that prints needs one —
 * settings to prove a printer, the till to print a receipt — and `features:A` must never import
 * `features:B`.
 */
fun interface TransportFactory {
    fun create(host: String, port: Int): IPrinterTransport
}
