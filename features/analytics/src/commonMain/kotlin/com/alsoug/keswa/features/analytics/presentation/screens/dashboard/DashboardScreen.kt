package com.alsoug.keswa.features.analytics.presentation.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alsoug.keswa.core.designsystem.KeswaTheme
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

    DashboardContent(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}

@Composable
internal fun DashboardContent(
    state: DashboardUiState,
    onEvent: (DashboardUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("How the shop is doing", style = MaterialTheme.typography.titleMedium)
        }

        if (!state.isPermitted) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "These figures are for the owner",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        ) {
            PeriodPicker(state.period, onEvent)
            state.kpis?.let { Headline(it, state.showsCost) }

            Panel("Revenue") { TrendLine(state.trend) }

            Panel("Colour performance") {
                Text(
                    "Sold against what is still on the rail. Cash tied up in the wrong colours is " +
                        "next season's buying decision.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ColourPerformanceChart(state.colours, modifier = Modifier.padding(top = 8.dp))
            }

            Panel("Sell-through") { SellThrough(state.sellThrough) }

            Panel("Busy hours") { BusyHoursHeatmap(state.busyHours) }

            Panel("Top movers") { Movers(state.movers, state.showsCost) }
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

private fun AnalyticsPeriod.label(): String = when (this) {
    AnalyticsPeriod.TODAY -> "Today"
    AnalyticsPeriod.WEEK -> "7 days"
    AnalyticsPeriod.MONTH -> "30 days"
    AnalyticsPeriod.QUARTER -> "90 days"
}

@Composable
private fun Headline(kpis: HeadlineKpis, showsCost: Boolean) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        // The one number the owner opens the app for.
        Text(kpis.netRevenue.format(), style = MaterialTheme.typography.displaySmall)
        Text(
            "net of ${kpis.refunded.format()} refunded",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Tile("Sales", "${kpis.transactions}", Modifier.weight(1f))
            Tile("Basket", kpis.averageBasket?.format() ?: "—", Modifier.weight(1f))
            Tile("Units", "${kpis.units}", Modifier.weight(1f))
            // A first-class KPI in clothing, not a footnote: fit is guesswork, so a shop with no
            // returns is a shop nobody is trying things on in.
            Tile("Returns", kpis.returnRateBasisPoints?.asPercent() ?: "—", Modifier.weight(1f))
            if (showsCost) {
                Tile("Margin", kpis.marginBasisPoints?.asPercent() ?: "—", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SellThrough(rows: List<SellThroughRowModel>) {
    if (rows.isEmpty()) {
        Text(
            "Nothing received yet",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Text(
        "Against a 70% season target. Below it late in a season means discount now, not in January.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    rows.forEach { row ->
        Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(row.name, style = MaterialTheme.typography.bodyMedium)
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
            "Nothing has sold yet",
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
                        append("${mover.sold} sold")
                        mover.sellThroughBasisPoints?.let { append(" · ${it.asPercent()} through") }
                        append(" · ${mover.onHand} left")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(mover.revenue.format(), style = MaterialTheme.typography.bodyMedium)
                if (showsCost) {
                    mover.margin?.let {
                        Text(
                            "margin ${it.format()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun Panel(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp).padding(top = 24.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Column(modifier = Modifier.padding(top = 8.dp)) { content() }
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
            onBack = {},
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
            onBack = {},
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
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun DashboardLoadingPreview() {
    KeswaTheme {
        DashboardContent(state = DashboardUiState(isLoading = true), onEvent = {}, onBack = {})
    }
}
