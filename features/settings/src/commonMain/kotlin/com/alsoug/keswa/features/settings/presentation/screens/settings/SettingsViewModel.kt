package com.alsoug.keswa.features.settings.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.designsystem.message
import com.alsoug.keswa.features.settings.domain.usecase.GetSettingsUseCase
import com.alsoug.keswa.features.settings.domain.usecase.PrintResult
import com.alsoug.keswa.features.settings.domain.usecase.PrintTestLabelUseCase
import com.alsoug.keswa.features.settings.domain.usecase.PrintTestPageUseCase
import com.alsoug.keswa.features.settings.domain.usecase.SaveSettingsUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val getSettings: GetSettingsUseCase,
    private val saveSettings: SaveSettingsUseCase,
    private val printTestPage: PrintTestPageUseCase,
    private val printTestLabel: PrintTestLabelUseCase,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _navigation = MutableSharedFlow<SettingsNavigation>(replay = 0, extraBufferCapacity = 1)
    val navigation = _navigation.asSharedFlow()

    private val _effect = MutableSharedFlow<SettingsUiEffect>(replay = 0, extraBufferCapacity = 1)
    val effect = _effect.asSharedFlow()

    fun onEvent(event: SettingsUiEvent) {
        when (event) {
            SettingsUiEvent.Load -> load()
            is SettingsUiEvent.Edit -> _state.update { it.copy(settings = event.settings) }
            SettingsUiEvent.Save -> save()
            SettingsUiEvent.TestReceipt -> runPrint { printTestPage() }
            SettingsUiEvent.TestLabel -> runPrint { printTestLabel() }
            SettingsUiEvent.Back -> viewModelScope.launch { _navigation.emit(SettingsNavigation.Back) }
        }
    }

    private fun load() {
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isLoading = true) }
            getSettings().fold(
                onSuccess = { settings -> _state.update { it.copy(isLoading = false, settings = settings) } },
                onFailure = { fail(it) },
            )
        }
    }

    private fun save() {
        viewModelScope.launch(dispatchers.io) {
            saveSettings(_state.value.settings).fold(
                onSuccess = { _effect.emit(SettingsUiEffect.ShowMessage(message { it.settingsSaved })) },
                onFailure = { fail(it) },
            )
        }
    }

    /**
     * Saves before printing, so the button tests what is on screen rather than what was last
     * stored — the alternative is someone changing the address and testing the old one.
     */
    private fun runPrint(attempt: suspend () -> Result<PrintResult>) {
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isBusy = true) }
            saveSettings(_state.value.settings)
            attempt().fold(
                onSuccess = { result ->
                    _state.update { it.copy(isBusy = false) }
                    when (result) {
                        PrintResult.Printed ->
                            _effect.emit(SettingsUiEffect.ShowMessage(message { it.sentToPrinter }))
                        PrintResult.NotConfigured ->
                            _effect.emit(SettingsUiEffect.ShowError(message { it.enterPrinterAddressFirst }))
                        is PrintResult.Unreachable ->
                            _effect.emit(
                                SettingsUiEffect.ShowError(message { it.printerDidNotAnswer(result.detail) }),
                            )
                    }
                },
                onFailure = { fail(it) },
            )
        }
    }

    private suspend fun fail(cause: Throwable) {
        _state.update { it.copy(isLoading = false, isBusy = false) }
        _effect.emit(SettingsUiEffect.ShowError(message { it.somethingWentWrong }))
    }
}
