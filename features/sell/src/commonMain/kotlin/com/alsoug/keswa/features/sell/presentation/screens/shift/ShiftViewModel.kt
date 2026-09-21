package com.alsoug.keswa.features.sell.presentation.screens.shift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.features.sell.domain.usecase.CloseShiftUseCase
import com.alsoug.keswa.features.sell.domain.usecase.CurrentShiftUseCase
import com.alsoug.keswa.features.sell.domain.usecase.ResolveTillContextUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ShiftViewModel(
    private val resolveContext: ResolveTillContextUseCase,
    private val currentShift: CurrentShiftUseCase,
    private val closeShift: CloseShiftUseCase,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(ShiftUiState())
    val state: StateFlow<ShiftUiState> = _state.asStateFlow()

    private val _navigation = MutableSharedFlow<ShiftNavigation>(replay = 0, extraBufferCapacity = 1)
    val navigation = _navigation.asSharedFlow()

    private val _effect = MutableSharedFlow<ShiftUiEffect>(replay = 0, extraBufferCapacity = 1)
    val effect = _effect.asSharedFlow()

    fun onEvent(event: ShiftUiEvent) {
        when (event) {
            ShiftUiEvent.Load -> load()
            is ShiftUiEvent.CountedCashChanged -> _state.update { it.copy(countedCash = event.value) }
            is ShiftUiEvent.NoteChanged -> _state.update { it.copy(note = event.value) }
            ShiftUiEvent.Close -> close()
            ShiftUiEvent.Done -> viewModelScope.launch { _navigation.emit(ShiftNavigation.Back) }
        }
    }

    private fun load() {
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isLoading = true) }
            resolveContext().fold(
                onSuccess = { till ->
                    // Read before updating: `update` re-runs its block on contention.
                    val shift = currentShift(till).getOrNull()
                    _state.update { it.copy(isLoading = false, shift = shift) }
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun close() {
        val current = _state.value
        val shift = current.shift ?: return
        val counted = Money.parse(current.countedCash) ?: return reject("That is not an amount")

        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isLoading = true) }
            closeShift(shift.id, counted, current.note).fold(
                onSuccess = { report ->
                    _state.update { it.copy(isLoading = false, report = report, shift = report.shift) }
                    val difference = report.difference
                    if (difference != null && !difference.isZero) {
                        _effect.emit(ShiftUiEffect.ShowMessage("Drawer differs by ${difference.format()}"))
                    }
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun reject(message: String) {
        viewModelScope.launch { _effect.emit(ShiftUiEffect.ShowError(message)) }
    }

    private suspend fun fail(cause: Throwable) {
        _state.update { it.copy(isLoading = false) }
        _effect.emit(ShiftUiEffect.ShowError(cause.message ?: "Could not close the shift"))
    }
}
