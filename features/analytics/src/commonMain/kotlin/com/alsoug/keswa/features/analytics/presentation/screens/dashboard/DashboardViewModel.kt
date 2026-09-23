package com.alsoug.keswa.features.analytics.presentation.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.designsystem.message
import com.alsoug.keswa.core.domain.model.AnalyticsPeriod
import com.alsoug.keswa.core.domain.model.BusyHours
import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.model.can
import com.alsoug.keswa.core.error.Error
import com.alsoug.keswa.core.session.ISessionManager
import com.alsoug.keswa.features.analytics.domain.usecase.GetBusyHoursUseCase
import com.alsoug.keswa.features.analytics.domain.usecase.GetColourPerformanceUseCase
import com.alsoug.keswa.features.analytics.domain.usecase.GetHeadlineKpisUseCase
import com.alsoug.keswa.features.analytics.domain.usecase.GetRevenueTrendUseCase
import com.alsoug.keswa.features.analytics.domain.usecase.GetSellThroughUseCase
import com.alsoug.keswa.features.analytics.domain.usecase.GetTopMoversUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

class DashboardViewModel(
    private val headline: GetHeadlineKpisUseCase,
    private val trend: GetRevenueTrendUseCase,
    private val colours: GetColourPerformanceUseCase,
    private val sellThrough: GetSellThroughUseCase,
    private val busyHours: GetBusyHoursUseCase,
    private val movers: GetTopMoversUseCase,
    private val sessions: ISessionManager,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private val _navigation = MutableSharedFlow<DashboardNavigation>(replay = 0, extraBufferCapacity = 1)
    val navigation = _navigation.asSharedFlow()

    private val _effect = MutableSharedFlow<DashboardUiEffect>(replay = 0, extraBufferCapacity = 1)
    val effect = _effect.asSharedFlow()

    fun onEvent(event: DashboardUiEvent) {
        when (event) {
            DashboardUiEvent.Load -> load(_state.value.period)
            is DashboardUiEvent.PeriodChanged -> load(event.period)
            DashboardUiEvent.Refresh -> load(_state.value.period)
        }
    }

    /**
     * Six panels, fetched together.
     *
     * ADR-040: `supervisorScope` with `async`, so one panel failing does not cancel the other
     * five — a dashboard that goes blank because the heatmap query tripped is worse than one with
     * a gap in it.
     */
    private fun load(period: AnalyticsPeriod) {
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isLoading = true, period = period) }

            if (!sessions.current.value.can(Permission.VIEW_SHOP_ANALYTICS)) {
                _state.update { it.copy(isLoading = false, isPermitted = false) }
                return@launch
            }

            supervisorScope {
                val kpis = async { headline(period) }
                val points = async { trend(period) }
                val buckets = async { colours(period) }
                val rows = async { sellThrough(period) }
                val hours = async { busyHours(period) }
                val top = async { movers(period) }

                val result = kpis.await()
                result.exceptionOrNull()?.let { cause ->
                    if (cause is Error.ForbiddenAccess) {
                        _state.update { it.copy(isLoading = false, isPermitted = false) }
                        return@supervisorScope
                    }
                    fail(cause)
                }

                _state.update {
                    it.copy(
                        isLoading = false,
                        isPermitted = true,
                        showsCost = sessions.current.value.can(Permission.VIEW_COST_AND_MARGIN),
                        kpis = result.getOrNull(),
                        trend = points.await().getOrElse { emptyList() },
                        colours = buckets.await().getOrElse { emptyList() },
                        sellThrough = rows.await().getOrElse { emptyList() },
                        busyHours = hours.await().getOrElse { BusyHours(emptyList()) },
                        movers = top.await().getOrElse { emptyList() },
                    )
                }
            }
        }
    }

    private suspend fun fail(cause: Throwable) {
        _state.update { it.copy(isLoading = false) }
        _effect.emit(DashboardUiEffect.ShowError(message { it.couldNotLoadNumbers }))
    }
}
