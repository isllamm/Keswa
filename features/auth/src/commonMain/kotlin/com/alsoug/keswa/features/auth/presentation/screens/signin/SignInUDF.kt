package com.alsoug.keswa.features.auth.presentation.screens.signin

import com.alsoug.keswa.core.domain.model.User

enum class SignInMode { SELLER, ADMIN }

data class SignInUiState(
    val isLoading: Boolean = false,
    val needsSetup: Boolean = false,
    val mode: SignInMode = SignInMode.SELLER,
    val sellers: List<User> = emptyList(),
    val selectedSellerId: String? = null,
    val pin: String = "",
    val username: String = "",
    val password: String = "",
    val lockedUntilMillis: Long? = null,
    /** Shown once, after setup. Persistent content the user must act on, so state — not an effect. */
    val recoveryCode: String? = null,
    val mustChangeFor: User? = null,
) {
    val isLocked: Boolean get() = lockedUntilMillis != null
    val canSubmitPassword: Boolean get() = username.isNotBlank() && password.isNotEmpty() && !isLocked

    companion object {
        const val PIN_LENGTH = 4
    }
}

sealed interface SignInUiEvent {
    data object Load : SignInUiEvent
    data class ModeChanged(val mode: SignInMode) : SignInUiEvent
    data class SellerSelected(val userId: String) : SignInUiEvent
    data class PinDigit(val digit: Char) : SignInUiEvent
    data object PinBackspace : SignInUiEvent
    data class UsernameChanged(val value: String) : SignInUiEvent
    data class PasswordChanged(val value: String) : SignInUiEvent
    data object SubmitPassword : SignInUiEvent
    data class Bootstrap(
        val username: String,
        val displayName: String,
        val password: String,
    ) : SignInUiEvent
    data object DismissRecoveryCode : SignInUiEvent
    data class ReplaceSecret(val newSecret: String) : SignInUiEvent
}

sealed interface SignInNavigation {
    data object SignedIn : SignInNavigation
}

sealed interface SignInUiEffect {
    data class ShowError(val message: String) : SignInUiEffect
    data class ShowMessage(val message: String) : SignInUiEffect
}
