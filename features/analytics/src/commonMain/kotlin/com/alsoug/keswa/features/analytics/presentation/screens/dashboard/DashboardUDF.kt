package com.alsoug.keswa.features.analytics.presentation.screens.dashboard

import com.alsoug.keswa.core.designsystem.Message
import com.alsoug.keswa.core.domain.model.AnalyticsPeriod
import com.alsoug.keswa.core.domain.model.BusyHours
import com.alsoug.keswa.core.domain.model.ColourBucket
import com.alsoug.keswa.core.domain.model.DailyPoint
import com.alsoug.keswa.core.domain.model.HeadlineKpis

import com.alsoug.keswa.core.domain.model.Mover
import com.alsoug.keswa.core.domain.model.SellThroughRowModel

data class DashboardUiState(
    val isLoading: Boolean = false,
    /**
     * One period scoping the whole page.
     *
     * Never per-panel filters: two panels that silently disagree destroy trust in the numbers
     * faster than any bug does.
     */
    val period: AnalyticsPeriod = AnalyticsPeriod.WEEK,
    val kpis: HeadlineKpis? = null,
    val trend: List<DailyPoint> = emptyList(),
    val colours: List<ColourBucket> = emptyList(),
    val sellThrough: List<SellThroughRowModel> = emptyList(),
    val busyHours: BusyHours = BusyHours(emptyList()),
    val movers: List<Mover> = emptyList(),
    /** False for a seller: margin and cost panels are absent, not blanked. */
    val showsCost: Boolean = false,
    val isPermitted: Boolean = true,
) {
    val hasAnything: Boolean get() = (kpis?.transactions ?: 0) > 0
}

sealed interface DashboardUiEvent {
    data object Load : DashboardUiEvent
    data class PeriodChanged(val period: AnalyticsPeriod) : DashboardUiEvent
    data object Refresh : DashboardUiEvent
}

sealed interface DashboardNavigation {
    data object Back : DashboardNavigation
}

sealed interface DashboardUiEffect {
    data class ShowError(val message: Message) : DashboardUiEffect
}
