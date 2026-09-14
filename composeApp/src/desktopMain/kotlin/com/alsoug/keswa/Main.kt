package com.alsoug.keswa

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.alsoug.keswa.di.initKoin

fun main() {
    // Koin 4 wires the Compose context from startKoin itself — no KoinContext wrapper needed.
    initKoin()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Keswa",
        ) {
            App()
        }
    }
}
