package com.alsoug.keswa.features.auth.presentation.screens.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.features.auth.domain.usecase.BootstrapFirstAdminUseCase
import com.alsoug.keswa.features.auth.domain.usecase.ChangeOwnSecretUseCase
import com.alsoug.keswa.features.auth.domain.usecase.ListSellersUseCase
import com.alsoug.keswa.features.auth.domain.usecase.NeedsFirstRunSetupUseCase
import com.alsoug.keswa.features.auth.domain.usecase.SignInResult
import com.alsoug.keswa.features.auth.domain.usecase.SignInUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Overwrites a credential in place, so it is not left sitting in the heap until collection. */
private fun CharArray.wipe() = fill(Char.MIN_VALUE)

class SignInViewModel(
    private val signIn: SignInUseCase,
    private val needsSetup: NeedsFirstRunSetupUseCase,
    private val bootstrap: BootstrapFirstAdminUseCase,
    private val listSellers: ListSellersUseCase,
    private val changeSecret: ChangeOwnSecretUseCase,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    private val _navigation = MutableSharedFlow<SignInNavigation>(replay = 0, extraBufferCapacity = 1)
    val navigation = _navigation.asSharedFlow()

    private val _effect = MutableSharedFlow<SignInUiEffect>(replay = 0, extraBufferCapacity = 1)
    val effect = _effect.asSharedFlow()

    fun onEvent(event: SignInUiEvent) {
        when (event) {
            SignInUiEvent.Load -> load()
            is SignInUiEvent.ModeChanged ->
                _state.update { it.copy(mode = event.mode, pin = "", password = "") }
            is SignInUiEvent.SellerSelected ->
                _state.update { it.copy(selectedSellerId = event.userId, pin = "") }
            is SignInUiEvent.PinDigit -> appendPin(event.digit)
            SignInUiEvent.PinBackspace -> _state.update { it.copy(pin = it.pin.dropLast(1)) }
            is SignInUiEvent.UsernameChanged -> _state.update { it.copy(username = event.value) }
            is SignInUiEvent.PasswordChanged -> _state.update { it.copy(password = event.value) }
            SignInUiEvent.SubmitPassword -> submitPassword()
            is SignInUiEvent.Bootstrap -> createOwner(event)
            SignInUiEvent.DismissRecoveryCode -> {
                _state.update { it.copy(recoveryCode = null) }
                viewModelScope.launch { _navigation.emit(SignInNavigation.SignedIn) }
            }
            is SignInUiEvent.ReplaceSecret -> replaceSecret(event.newSecret)
        }
    }

    private fun load() {
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isLoading = true) }
            val setupNeeded = needsSetup().getOrElse { false }
            val sellers = listSellers().getOrElse { emptyList() }
            _state.update {
                it.copy(
                    isLoading = false,
                    needsSetup = setupNeeded,
                    sellers = sellers,
                    selectedSellerId = it.selectedSellerId ?: sellers.firstOrNull()?.id,
                    // Nobody to pick from means the PIN keypad has no purpose yet.
                    mode = if (sellers.isEmpty()) SignInMode.ADMIN else it.mode,
                )
            }
        }
    }

    /** Submits itself once the PIN is complete — a keypad that needs a second tap is tiring. */
    private fun appendPin(digit: Char) {
        val next = (_state.value.pin + digit).take(SignInUiState.PIN_LENGTH)
        _state.update { it.copy(pin = next) }
        if (next.length == SignInUiState.PIN_LENGTH) submitPin()
    }

    private fun submitPin() {
        val current = _state.value
        val userId = current.selectedSellerId ?: return
        val secret = current.pin.toCharArray()
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isLoading = true) }
            signIn.byUserId(userId, secret).fold(onSuccess = { handle(it) }, onFailure = { fail(it) })
            secret.wipe()
        }
    }

    private fun submitPassword() {
        val current = _state.value
        val secret = current.password.toCharArray()
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isLoading = true) }
            signIn(current.username, secret).fold(onSuccess = { handle(it) }, onFailure = { fail(it) })
            secret.wipe()
        }
    }

    private suspend fun handle(result: SignInResult) {
        _state.update { it.copy(isLoading = false, pin = "", password = "") }
        when (result) {
            is SignInResult.Success -> _navigation.emit(SignInNavigation.SignedIn)
            is SignInResult.MustChangeSecret -> _state.update { it.copy(mustChangeFor = result.user) }
            is SignInResult.Locked -> {
                _state.update { it.copy(lockedUntilMillis = result.untilMillis) }
                _effect.emit(SignInUiEffect.ShowError("Too many attempts — locked for a few minutes"))
            }
            // One message for both failure modes: naming which half was wrong turns this screen
            // into a way to discover who works here.
            SignInResult.BadCredentials -> _effect.emit(SignInUiEffect.ShowError("Incorrect details"))
        }
    }

    private fun createOwner(event: SignInUiEvent.Bootstrap) {
        val secret = event.password.toCharArray()
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isLoading = true) }
            bootstrap(event.username, event.displayName, secret).fold(
                onSuccess = { result ->
                    _state.update {
                        it.copy(isLoading = false, needsSetup = false, recoveryCode = result.recoveryCode)
                    }
                },
                onFailure = { fail(it) },
            )
            secret.wipe()
        }
    }

    private fun replaceSecret(newSecret: String) {
        val user = _state.value.mustChangeFor ?: return
        val secret = newSecret.toCharArray()
        viewModelScope.launch(dispatchers.io) {
            changeSecret(user.id, secret).fold(
                onSuccess = {
                    _state.update { it.copy(mustChangeFor = null) }
                    _effect.emit(SignInUiEffect.ShowMessage("Updated — sign in with your new details"))
                },
                onFailure = { fail(it) },
            )
            secret.wipe()
        }
    }

    private suspend fun fail(cause: Throwable) {
        _state.update { it.copy(isLoading = false) }
        _effect.emit(SignInUiEffect.ShowError(cause.message ?: "Sign-in failed"))
    }
}
