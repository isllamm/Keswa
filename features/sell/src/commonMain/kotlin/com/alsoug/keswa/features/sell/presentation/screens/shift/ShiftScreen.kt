package com.alsoug.keswa.features.sell.presentation.screens.shift

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alsoug.keswa.core.designsystem.EmptyState
import com.alsoug.keswa.core.designsystem.KeswaTheme
import com.alsoug.keswa.core.designsystem.ScreenHeader
import com.alsoug.keswa.core.domain.model.Shift
import com.alsoug.keswa.core.domain.model.ZReport
import com.alsoug.keswa.core.domain.money.Money
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun ShiftScreen(
    viewModel: ShiftViewModel,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onEvent(ShiftUiEvent.Load) }

    LaunchedEffect(Unit) {
        viewModel.navigation.collect { navigation ->
            when (navigation) {
                ShiftNavigation.Back -> onBack()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ShiftUiEffect.ShowError -> onMessage(effect.message)
                is ShiftUiEffect.ShowMessage -> onMessage(effect.message)
            }
        }
    }

    ShiftContent(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
internal fun ShiftContent(
    state: ShiftUiState,
    onEvent: (ShiftUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        ScreenHeader(
            title = KeswaTheme.strings.shift,
            subtitle = KeswaTheme.strings.shiftSubtitle,
        )

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            when {
                state.report != null -> ReportCard(state.report)
                state.shift == null -> EmptyState(
                    title = KeswaTheme.strings.noShiftOpen,
                    hint = KeswaTheme.strings.noShiftOpenHint,
                )
                else -> CountForm(state, onEvent)
            }

            Row(modifier = Modifier.padding(top = 16.dp)) {
                TextButton(onClick = { onEvent(ShiftUiEvent.Done) }) { Text(KeswaTheme.strings.backToTill) }
            }
        }
    }
}

@Composable
private fun CountForm(state: ShiftUiState, onEvent: (ShiftUiEvent) -> Unit) {
    Column(modifier = Modifier.widthIn(max = 420.dp).padding(top = 12.dp)) {
        Text(
            KeswaTheme.strings.countTheDrawerFirst,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.countedCash,
            onValueChange = { onEvent(ShiftUiEvent.CountedCashChanged(it)) },
            label = { Text(KeswaTheme.strings.cashCounted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        OutlinedTextField(
            value = state.note,
            onValueChange = { onEvent(ShiftUiEvent.NoteChanged(it)) },
            label = { Text(KeswaTheme.strings.noteOptional) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Button(
            onClick = { onEvent(ShiftUiEvent.Close) },
            enabled = state.canClose,
            modifier = Modifier.padding(top = 12.dp),
        ) { Text(KeswaTheme.strings.closeShift) }
    }
}

@Composable
private fun ReportCard(report: ZReport) {
    Card(modifier = Modifier.widthIn(max = 480.dp).padding(top = 12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(KeswaTheme.strings.zReport, style = MaterialTheme.typography.titleMedium)
            ReportRow("Sales", "${report.saleCount}")
            ReportRow("Voided", "${report.voidedCount}")
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            ReportRow("Gross", report.grossSales.format())
            ReportRow("Discounts", report.discounts.format())
            if (!report.tax.isZero) ReportRow("VAT", report.tax.format())
            ReportRow("Net", report.netSales.format())
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            ReportRow("Float", report.shift.openingFloat.format())
            ReportRow("Cash", report.cashTaken.format())
            ReportRow("Card", report.cardTaken.format())
            ReportRow(KeswaTheme.strings.changeGiven, report.changeGiven.format())
            if (report.returnCount > 0) {
                ReportRow("Returns", "${report.returnCount}")
                // Cash refunds genuinely leave the drawer; card refunds never touch it.
                ReportRow(KeswaTheme.strings.refundedInCash, report.cashRefunded.format())
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            ReportRow(KeswaTheme.strings.expectedInDrawer, report.expectedCash.format())
            ReportRow("Counted", report.countedCash?.format() ?: "—")
            report.difference?.let { difference ->
                Text(
                    if (difference.isZero) "Balanced" else "Difference ${difference.format()}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (difference.isZero) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (report.salesOutsideShift > 0) {
                Text(
                    "${report.salesOutsideShift} sale(s) were rung up outside any shift",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ReportRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private val previewShift = Shift(
    id = "s1",
    locationId = "loc-shop",
    openedByUserId = "u1",
    openedAt = 0,
    openingFloat = Money.ofPounds(500),
    closedAt = 1,
    closedByUserId = "u1",
    countedCash = Money.ofPounds(4_180),
    expectedCash = Money.ofPounds(4_200),
)

@Preview
@Composable
private fun ShiftReportPreview() {
    KeswaTheme {
        ShiftContent(
            state = ShiftUiState(
                shift = previewShift,
                report = ZReport(
                    shift = previewShift,
                    saleCount = 23,
                    voidedCount = 1,
                    grossSales = Money.ofPounds(4_050),
                    discounts = Money.ofPounds(150),
                    tax = Money.ZERO,
                    netSales = Money.ofPounds(3_900),
                    cashTaken = Money.ofPounds(3_700),
                    cardTaken = Money.ofPounds(200),
                    changeGiven = Money.ofPounds(320),
                    returnCount = 1,
                    cashRefunded = Money.ofPounds(180),
                    cardRefunded = Money.ZERO,
                    expectedCash = Money.ofPounds(4_200),
                    countedCash = Money.ofPounds(4_180),
                    salesOutsideShift = 2,
                ),
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun ShiftCountPreview() {
    KeswaTheme {
        ShiftContent(
            state = ShiftUiState(shift = previewShift.copy(closedAt = null, countedCash = null)),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun ShiftLoadingPreview() {
    KeswaTheme { ShiftContent(state = ShiftUiState(isLoading = true), onEvent = {}) }
}
