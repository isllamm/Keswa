package com.alsoug.keswa.features.settings.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.designsystem.message
import com.alsoug.keswa.features.settings.domain.usecase.EnrolDeviceUseCase
import com.alsoug.keswa.features.settings.domain.usecase.GetSettingsUseCase
import com.alsoug.keswa.features.settings.domain.usecase.GetSyncStatusUseCase
import com.alsoug.keswa.features.settings.domain.usecase.PrintResult
import com.alsoug.keswa.features.settings.domain.usecase.PrintTestLabelUseCase
import com.alsoug.keswa.features.settings.domain.usecase.PrintTestPageUseCase
import com.alsoug.keswa.features.settings.domain.usecase.SaveSettingsUseCase
import com.alsoug.keswa.features.settings.domain.usecase.SyncNowUseCase
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
    private val getSyncStatus: GetSyncStatusUseCase,
    private val enrolDevice: EnrolDeviceUseCase,
    private val syncNow: SyncNowUseCase,
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
            is SettingsUiEvent.EditSyncServerUrl -> _state.update { it.copy(syncServerUrlInput = event.url) }
            is SettingsUiEvent.EditSyncDeviceName -> _state.update { it.copy(syncDeviceNameInput = event.name) }
            is SettingsUiEvent.EditSyncCode -> _state.update { it.copy(syncCodeInput = event.code) }
            SettingsUiEvent.EnrolDevice -> enrol()
            SettingsUiEvent.SyncNow -> sync()
            SettingsUiEvent.Back -> viewModelScope.launch { _navigation.emit(SettingsNavigation.Back) }
        }
    }

    private fun load() {
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isLoading = true) }
            getSettings().fold(
                onSuccess = { settings -> _state.update { it.copy(settings = settings) } },
                onFailure = { fail(it) },
            )
            getSyncStatus().fold(
                onSuccess = { status ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            syncStatus = status,
                            syncServerUrlInput = status.serverUrl ?: it.syncServerUrlInput,
                            syncDeviceNameInput = status.deviceName,
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isLoading = false) }
                },
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

    private fun enrol() {
        val current = _state.value
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isSyncing = true) }
            enrolDevice(
                serverUrl = current.syncServerUrlInput,
                deviceName = current.syncDeviceNameInput,
                code = current.syncCodeInput,
            ).fold(
                onSuccess = {
                    _effect.emit(SettingsUiEffect.ShowMessage(message { it.enrolSuccess }))
                    getSyncStatus().onSuccess { status ->
                        _state.update { it.copy(syncStatus = status, syncCodeInput = "") }
                    }
                    syncNow().fold(
                        onSuccess = { result ->
                            _state.update { it.copy(isSyncing = false) }
                            _effect.emit(
                                SettingsUiEffect.ShowMessage(
                                    message { it.syncCompleted(result.pushed, result.pulled) },
                                ),
                            )
                        },
                        onFailure = {
                            _state.update { it.copy(isSyncing = false) }
                        },
                    )
                },
                onFailure = {
                    _state.update { it.copy(isSyncing = false) }
                    _effect.emit(SettingsUiEffect.ShowError(message { it.somethingWentWrong }))
                },
            )
        }
    }

    private fun sync() {
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isSyncing = true) }
            syncNow().fold(
                onSuccess = { result ->
                    _state.update { it.copy(isSyncing = false) }
                    _effect.emit(
                        SettingsUiEffect.ShowMessage(
                            message { it.syncCompleted(result.pushed, result.pulled) },
                        ),
                    )
                    getSyncStatus().onSuccess { status ->
                        _state.update { it.copy(syncStatus = status) }
                    }
                },
                onFailure = {
                    _state.update { it.copy(isSyncing = false) }
                    _effect.emit(SettingsUiEffect.ShowError(message { it.somethingWentWrong }))
                },
            )
        }
    }

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
        _state.update { it.copy(isLoading = false, isBusy = false, isSyncing = false) }
        _effect.emit(SettingsUiEffect.ShowError(message { it.somethingWentWrong }))
    }
}
