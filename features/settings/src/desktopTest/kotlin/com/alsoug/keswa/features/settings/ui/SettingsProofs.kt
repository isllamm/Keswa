package com.alsoug.keswa.features.settings.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.alsoug.keswa.core.designsystem.KeswaLanguage
import com.alsoug.keswa.core.designsystem.KeswaTheme
import com.alsoug.keswa.core.domain.model.ShopSettings
import com.alsoug.keswa.core.domain.model.SyncDeviceStatus
import com.alsoug.keswa.features.settings.presentation.screens.settings.SettingsContent
import com.alsoug.keswa.features.settings.presentation.screens.settings.SettingsUiState
import java.io.File
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat

/**
 * Visual regression proofs for Hardware and Device Settings screen.
 *
 * Renders into `features/settings/build/ui-proofs/`.
 */
class SettingsProofs {

    private val enrolledStatus = SyncDeviceStatus(
        deviceId = "dev-101",
        deviceName = "Till 1 — Front Counter",
        serverUrl = "http://192.168.1.100:8080",
        ordinal = 1,
        isEnrolled = true,
        receiptRange = "1,000,000 – 1,999,999",
    )

    private val configuredSettings = ShopSettings(
        receiptHost = "192.168.1.50",
        receiptPort = 9100,
        paperWidthDots = 576,
        labelHost = "192.168.1.51",
        labelPort = 9100,
        labelWidthMm = 50,
        labelHeightMm = 30,
        labelGapMm = 2,
        scanMaxGapMillis = 50,
    )

    @Test
    fun `render all settings screen states`() {
        render("settings-configured") {
            Page(dark = false) {
                SettingsContent(
                    state = SettingsUiState(
                        settings = configuredSettings,
                        syncStatus = enrolledStatus,
                    ),
                    onEvent = {},
                )
            }
        }

        render("settings-unenrolled") {
            Page(dark = false) {
                SettingsContent(
                    state = SettingsUiState(
                        settings = ShopSettings(),
                        syncServerUrlInput = "http://192.168.1.100:8080",
                        syncDeviceNameInput = "Till 2 — Back Counter",
                        syncCodeInput = "ENROL-4821",
                    ),
                    onEvent = {},
                )
            }
        }

        render("settings-arabic") {
            Page(dark = false, language = KeswaLanguage.ARABIC) {
                SettingsContent(
                    state = SettingsUiState(
                        settings = configuredSettings,
                        syncStatus = enrolledStatus,
                    ),
                    onEvent = {},
                )
            }
        }

        render("settings-dark") {
            Page(dark = true) {
                SettingsContent(
                    state = SettingsUiState(
                        settings = configuredSettings,
                        syncStatus = enrolledStatus,
                    ),
                    onEvent = {},
                )
            }
        }

        render("settings-busy") {
            Page(dark = false) {
                SettingsContent(
                    state = SettingsUiState(
                        isBusy = true,
                        isSyncing = true,
                        settings = configuredSettings,
                        syncStatus = enrolledStatus,
                    ),
                    onEvent = {},
                )
            }
        }
    }
}

@Composable
private fun Page(
    dark: Boolean,
    language: KeswaLanguage = KeswaLanguage.ENGLISH,
    content: @Composable () -> Unit,
) {
    KeswaTheme(dark = dark, language = language) {
        Surface(color = MaterialTheme.colorScheme.background) { content() }
    }
}

private val directory = File("build/ui-proofs").also { it.mkdirs() }

private fun render(name: String, width: Int = 1100, height: Int = 850, content: @Composable () -> Unit) {
    val density = 2f
    val scene = ImageComposeScene(
        width = (width * density).toInt(),
        height = (height * density).toInt(),
        density = Density(density),
        content = content,
    )
    try {
        val bytes = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
            ?: error("Skia declined to encode $name")
        directory.resolve("$name.png").writeBytes(bytes)
    } finally {
        scene.close()
    }
}
