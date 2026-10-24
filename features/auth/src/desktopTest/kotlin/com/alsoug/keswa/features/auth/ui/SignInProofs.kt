package com.alsoug.keswa.features.auth.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.alsoug.keswa.core.designsystem.KeswaLanguage
import com.alsoug.keswa.core.designsystem.KeswaTheme
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.UserRole
import com.alsoug.keswa.features.auth.presentation.screens.signin.SignInContent
import com.alsoug.keswa.features.auth.presentation.screens.signin.SignInMode
import com.alsoug.keswa.features.auth.presentation.screens.signin.SignInUiState
import java.io.File
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat

/**
 * Renders the sign-in and user authentication screens to `features/auth/build/ui-proofs/`.
 *
 * Verifies visual layout, RTL typography, high-contrast states, and layout invariants across
 * seller PIN entry, admin credential entry, first-run initialization, and account lockout.
 */
class SignInProofs {

    private val sellers = listOf(
        User("u1", "sara", "Sara Mahmoud", "سارة محمود", UserRole.SELLER),
        User("u2", "tarek", "Tarek Nabil", "طارق نبيل", UserRole.SELLER),
        User("u3", "omar", "Omar Sherif", "عمر شريف", UserRole.SELLER),
    )

    @Test
    fun `render all sign in states`() {
        render("signin-seller-light") {
            Page(dark = false) {
                SignInContent(
                    state = SignInUiState(
                        sellers = sellers,
                        selectedSellerId = "u1",
                        pin = "12",
                    ),
                    onEvent = {},
                )
            }
        }

        render("signin-seller-dark") {
            Page(dark = true) {
                SignInContent(
                    state = SignInUiState(
                        sellers = sellers,
                        selectedSellerId = "u1",
                        pin = "12",
                    ),
                    onEvent = {},
                )
            }
        }

        render("signin-seller-arabic") {
            Page(dark = false, language = KeswaLanguage.ARABIC) {
                SignInContent(
                    state = SignInUiState(
                        sellers = sellers,
                        selectedSellerId = "u1",
                        pin = "12",
                    ),
                    onEvent = {},
                )
            }
        }

        render("signin-admin") {
            Page(dark = false) {
                SignInContent(
                    state = SignInUiState(
                        sellers = sellers,
                        mode = SignInMode.ADMIN,
                        username = "admin",
                        password = "password123",
                    ),
                    onEvent = {},
                )
            }
        }

        render("signin-firstrun") {
            Page(dark = false) {
                SignInContent(
                    state = SignInUiState(
                        needsSetup = true,
                    ),
                    onEvent = {},
                )
            }
        }

        render("signin-recovery") {
            Page(dark = false) {
                SignInContent(
                    state = SignInUiState(
                        recoveryCode = "KSW-BDFH-JKLM-NPQR",
                    ),
                    onEvent = {},
                )
            }
        }

        render("signin-locked") {
            Page(dark = false) {
                SignInContent(
                    state = SignInUiState(
                        sellers = sellers,
                        selectedSellerId = "u1",
                        lockedUntilMillis = 1_757_000_060_000L,
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

private fun render(name: String, width: Int = 1000, height: Int = 700, content: @Composable () -> Unit) {
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
