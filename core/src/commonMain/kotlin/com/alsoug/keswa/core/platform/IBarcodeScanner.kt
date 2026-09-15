package com.alsoug.keswa.core.platform

import kotlinx.coroutines.flow.Flow

/**
 * Completed barcode scans.
 *
 * A scan must never land in whatever text field happens to have focus — capture is global and the
 * screen that wants scans consumes this explicitly.
 */
interface IBarcodeScanner {
    val scans: Flow<String>
}
