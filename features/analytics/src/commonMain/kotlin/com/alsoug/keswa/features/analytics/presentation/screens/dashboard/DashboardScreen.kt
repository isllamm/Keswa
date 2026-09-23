package com.alsoug.keswa.features.analytics.presentation.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alsoug.keswa.core.designsystem.EmptyState
import com.alsoug.keswa.core.designsystem.KeswaTheme
import com.alsoug.keswa.core.designsystem.localisedName
import com.alsoug.keswa.core.designsystem.Panel
import com.alsoug.keswa.core.designsystem.ScreenHeader
import com.alsoug.keswa.core.designsystem.StatTile
import com.alsoug.keswa.core.domain.model.AnalyticsPeriod
import com.alsoug.keswa.core.domain.model.BusyHours
import com.alsoug.keswa.core.domain.model.ColourBucket
import com.alsoug.keswa.core.domain.model.DailyPoint
import com.alsoug.keswa.core.domain.model.HeadlineKpis
import com.alsoug.keswa.core.domain.model.HourBucket
import com.alsoug.keswa.core.domain.model.Mover
import com.alsoug.keswa.core.domain.model.SellThroughRowModel
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.features.analytics.presentation.components.BusyHoursHeatmap
import com.alsoug.keswa.features.analytics.presentation.components.ColourPerformanceChart
import com.alsoug.keswa.features.analytics.presentation.components.SellThroughBar
import com.alsoug.keswa.features.analytics.presentation.components.TrendLine
import org.jetbrains.compose.ui.tooling.preview.Preview

/** ADR-012 — a humble view: renders state, forwards events, owns no business logic. */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onEvent(DashboardUiEvent.Load) }

    LaunchedEffect(Unit) {
        viewModel.navigation.collect { navigation ->
            when (navigation) {
                DashboardNavigation.Back -> onBack()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is DashboardUiEffect.ShowError -> onMessage(effect.message)
            }
        }
    }

    DashboardContent(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
internal fun DashboardContent(
    state: DashboardUiState,
    onEvent: (DashboardUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        // No back arrow. The navigation is persistent now, so "back" had no answer — it went to
        // the till, which is a destination, not a return. The period picker belongs up here
        // instead, because one filter scopes every panel below it.
        ScreenHeader(title = KeswaTheme.strings.numbers, subtitle = KeswaTheme.strings.numbersSubtitle) {
            if (state.isPermitted) PeriodPicker(state.period, onEvent)
        }

        if (!state.isPermitted) {
            EmptyState(
                title = KeswaTheme.strings.forTheOwner,
                hint = KeswaTheme.strings.forTheOwnerHint,
            )
            return
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        ) {
            state.kpis?.let { Headline(it, state.showsCost) }

            Panel(KeswaTheme.strings.revenue) { TrendLine(state.trend) }

            Panel(KeswaTheme.strings.colourPerformance) {
                Text(
                    KeswaTheme.strings.colourPerformanceNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ColourPerformanceChart(state.colours, modifier = Modifier.padding(top = 8.dp))
            }

            Panel(KeswaTheme.strings.sellThrough) { SellThrough(state.sellThrough) }

            Panel(KeswaTheme.strings.busyHours) { BusyHoursHeatmap(state.busyHours) }

            Panel(KeswaTheme.strings.topMovers) { Movers(state.movers, state.showsCost) }
        }
    }
}

@Composable
private fun PeriodPicker(period: AnalyticsPeriod, onEvent: (DashboardUiEvent) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AnalyticsPeriod.entries.forEach { candidate ->
            FilterChip(
                selected = period == candidate,
                onClick = { onEvent(DashboardUiEvent.PeriodChanged(candidate)) },
                label = { Text(candidate.label()) },
            )
        }
    }
}

@Composable
private fun AnalyticsPeriod.label(): String = when (this) {
    AnalyticsPeriod.TODAY -> KeswaTheme.strings.today
    AnalyticsPeriod.WEEK -> KeswaTheme.strings.sevenDays
    AnalyticsPeriod.MONTH -> KeswaTheme.strings.thirtyDays
    AnalyticsPeriod.QUARTER -> KeswaTheme.strings.ninetyDays
}

@Composable
private fun Headline(kpis: HeadlineKpis, showsCost: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // The one number the owner opens the app for.
        Text(kpis.netRevenue.format(), style = MaterialTheme.typography.displaySmall)
        Text(
            "${KeswaTheme.strings.netOf} ${kpis.refunded.format()}",
            style = MaterialTheme.typography.labelSmall,
            color = KeswaTheme.semantics.muted,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatTile(KeswaTheme.strings.sales, "${kpis.transactions}", modifier = Modifier.weight(1f))
            StatTile(KeswaTheme.strings.basket, kpis.averageBasket?.format() ?: "—", modifier = Modifier.weight(1f))
            StatTile(KeswaTheme.strings.units, "${kpis.units}", modifier = Modifier.weight(1f))
            // A first-class KPI in clothing, not a footnote: fit is guesswork, so a shop with no
            // returns is a shop nobody is trying things on in.
            StatTile(
                label = KeswaTheme.strings.returnRate,
                value = kpis.returnRateBasisPoints?.asPercent() ?: "—",
                accent = KeswaTheme.semantics.warning,
                modifier = Modifier.weight(1f),
            )
            if (showsCost) {
                StatTile(
                    label = KeswaTheme.strings.margin,
                    value = kpis.marginBasisPoints?.asPercent() ?: "—",
                    accent = KeswaTheme.semantics.good,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SellThrough(rows: List<SellThroughRowModel>) {
    if (rows.isEmpty()) {
        Text(
            KeswaTheme.strings.nothingReceived,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Text(
        KeswaTheme.strings.sellThroughNote,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    rows.forEach { row ->
        Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(localisedName(row.name, row.nameAr), style = MaterialTheme.typography.bodyMedium)
                Text(
                    row.basisPoints?.asPercent() ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            SellThroughBar(
                basisPoints = row.basisPoints ?: 0,
                targetBasisPoints = SellThroughRowModel.TARGET_BASIS_POINTS,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun Movers(movers: List<Mover>, showsCost: Boolean) {
    if (movers.isEmpty()) {
        Text(
            KeswaTheme.strings.nothingHasSold,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    // Past about seven items a table beats a chart, so this is a table.
    movers.forEach { mover ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${mover.productName} — ${mover.colourName}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    buildString {
                        append("${mover.sold} ${KeswaTheme.strings.soldSuffix}")
                        mover.sellThroughBasisPoints?.let { append(" · ${it.asPercent()} ${KeswaTheme.strings.throughSuffix}") }
                        append(" · ${mover.onHand} ${KeswaTheme.strings.leftSuffix}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(mover.revenue.format(), style = KeswaTheme.figure)
                if (showsCost) {
                    mover.margin?.let {
                        Text(
                            "${KeswaTheme.strings.marginPrefix} ${it.format()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = KeswaTheme.semantics.muted,
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = KeswaTheme.semantics.hair)
    }
}

/** Basis points as a whole percent. Nothing floating-point reaches a figure anyone decides on. */
private fun Int.asPercent(): String = "${this / 100}%"

private val previewKpis = HeadlineKpis(
    revenue = Money.ofPounds(48_250),
    refunded = Money.ofPounds(1_260),
    transactions = 134,
    units = 291,
    returnedUnits = 7,
    cogs = Money.ofPounds(29_400),
)

private val previewTrend = listOf(
    DailyPoint("2026-09-14", Money.ofPounds(4_200), 12),
    DailyPoint("2026-09-15", Money.ofPounds(5_100), 15),
    DailyPoint("2026-09-16", Money.ofPounds(3_900), 11),
    DailyPoint("2026-09-17", Money.ofPounds(8_400), 24),
    DailyPoint("2026-09-18", Money.ofPounds(9_800), 29),
    DailyPoint("2026-09-19", Money.ofPounds(11_200), 31),
    DailyPoint("2026-09-20", Money.ofPounds(5_650), 12),
)

private val previewColours = listOf(
    ColourBucket("c1", "Navy", "كحلي", "#20304f", sold = 84, onHand = 6),
    ColourBucket("c2", "White", "أبيض", "#f2f2ef", sold = 61, onHand = 22),
    ColourBucket("c3", "Beige", "بيج", "#cfbfa4", sold = 12, onHand = 78),
    ColourBucket("c4", "Green", "أخضر", "#3f7a53", sold = 9, onHand = 64),
)

private val previewSellThrough = listOf(
    SellThroughRowModel("cat-1", "T-shirts", "تيشيرتات", sold = 157, received = 190),
    SellThroughRowModel("cat-2", "Shirts", "قمصان", sold = 48, received = 160),
)

private val previewHours = BusyHours(
    (0..6).flatMap { day ->
        (9..22).map { hour ->
            HourBucket(day, hour, transactions = ((day * 7 + hour * 3) % 11), amount = Money.ZERO)
        }
    },
)

private val previewMovers = listOf(
    Mover("v1", "KSW-TSH-022-NV", "Round-neck t-shirt", "Navy", 84, Money.ofPounds(15_120), 6, 90, Money.ofPounds(10_080)),
    Mover("v2", "KSW-SHT-004-WH", "Oxford shirt", "White", 31, Money.ofPounds(10_540), 22, 53, Money.ofPounds(7_440)),
)

@Preview
@Composable
private fun DashboardSuccessPreview() {
    KeswaTheme {
        DashboardContent(
            state = DashboardUiState(
                kpis = previewKpis,
                trend = previewTrend,
                colours = previewColours,
                sellThrough = previewSellThrough,
                busyHours = previewHours,
                movers = previewMovers,
                showsCost = true,
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun DashboardAsSellerPreview() {
    KeswaTheme {
        DashboardContent(
            state = DashboardUiState(isPermitted = false),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun DashboardDayOnePreview() {
    // Every new install sees this first, so it is the state most worth eyeballing.
    KeswaTheme {
        DashboardContent(
            state = DashboardUiState(
                kpis = HeadlineKpis(Money.ZERO, Money.ZERO, 0, 0, 0, Money.ZERO),
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun DashboardLoadingPreview() {
    KeswaTheme {
        DashboardContent(state = DashboardUiState(isLoading = true), onEvent = {})
    }
}
