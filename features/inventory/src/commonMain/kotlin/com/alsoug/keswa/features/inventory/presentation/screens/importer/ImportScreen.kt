package com.alsoug.keswa.features.inventory.presentation.screens.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.alsoug.keswa.core.designsystem.KeswaTheme
import com.alsoug.keswa.core.designsystem.ScreenHeader
import com.alsoug.keswa.core.designsystem.resolve
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.features.inventory.domain.usecase.ImportProblem
import com.alsoug.keswa.features.inventory.domain.usecase.ImportRow
import com.alsoug.keswa.features.inventory.domain.usecase.ImportSummary
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val strings = KeswaTheme.strings

    LaunchedEffect(Unit) { viewModel.onEvent(ImportUiEvent.Load) }

    LaunchedEffect(Unit) {
        viewModel.navigation.collect { navigation ->
            when (navigation) {
                ImportNavigation.Done -> onBack()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ImportUiEffect.ShowError -> onMessage(effect.message.resolve(strings))
                is ImportUiEffect.ShowMessage -> onMessage(effect.message.resolve(strings))
            }
        }
    }

    ImportContent(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}

@Composable
internal fun ImportContent(
    state: ImportUiState,
    onEvent: (ImportUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        if (state.isLoading || state.isApplying) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        ScreenHeader(
            title = KeswaTheme.strings.importCatalogue,
            subtitle = KeswaTheme.strings.importCatalogueHint,
            onBack = onBack,
        )
        Text(
            KeswaTheme.strings.pasteSpreadsheetNote,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.summary != null) {
            Summary(state.summary, onEvent)
            return@Column
        }

        OutlinedTextField(
            value = state.text,
            onValueChange = { onEvent(ImportUiEvent.TextChanged(it)) },
            label = { Text(KeswaTheme.strings.rows) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 260.dp).padding(top = 8.dp),
        )

        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Check and apply are separate presses on purpose: an import creates products, SKUs,
            // prices and stock at once, and none of it is undoable.
            OutlinedButton(
                onClick = { onEvent(ImportUiEvent.Check) },
                enabled = state.text.isNotBlank(),
            ) { Text(KeswaTheme.strings.check) }
            Button(onClick = { onEvent(ImportUiEvent.Apply) }, enabled = state.canApply) {
                Text(KeswaTheme.strings.importRows(state.rows.size))
            }
        }

        when {
            state.problems.isNotEmpty() -> Problems(state.problems)
            state.hasParsed -> Preview(state)
        }
    }
}

@Composable
private fun ColumnScope.Problems(problems: List<ImportProblem>) {
    Text(
        KeswaTheme.strings.nothingWasImported,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 12.dp),
    )
    Text(
        KeswaTheme.strings.oneBadRowNone,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp)) {
        items(problems) { problem ->
            Text(
                "Line ${problem.lineNumber}: ${problem.message}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ColumnScope.Preview(state: ImportUiState) {
    Text(
        "${state.rows.size} rows, ${state.pieceCount} pieces",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 12.dp),
    )
    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp)) {
        items(state.rows) { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${row.productName} — ${row.colourName}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "${row.sku} · cost ${row.cost.format()} · ${row.quantity} pcs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(row.price.format(), style = KeswaTheme.figure)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun Summary(summary: ImportSummary, onEvent: (ImportUiEvent) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(KeswaTheme.strings.imported, style = MaterialTheme.typography.titleSmall)
            Text("${summary.productsCreated} products", style = MaterialTheme.typography.bodyMedium)
            Text("${summary.variantsCreated} SKUs", style = MaterialTheme.typography.bodyMedium)
            Text(
                "${summary.piecesReceived} pieces received",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                KeswaTheme.strings.stockArrivedAsDelivery,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            OutlinedButton(
                onClick = { onEvent(ImportUiEvent.Reset) },
                modifier = Modifier.padding(top = 12.dp),
            ) { Text(KeswaTheme.strings.importAnother) }
        }
    }
}

private val previewRows = listOf(
    ImportRow(1, "Round-neck t-shirt", "تيشيرت", "Navy", "KSW-TSH-001-NV", Money.ofPounds(120), Money.ofPounds(180), 20),
    ImportRow(2, "Round-neck t-shirt", "تيشيرت", "White", "KSW-TSH-001-WH", Money.ofPounds(118), Money.ofPounds(180), 30),
)

@Preview
@Composable
private fun ImportCheckedPreview() {
    KeswaTheme {
        ImportContent(
            state = ImportUiState(text = "…", rows = previewRows),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun ImportRejectedPreview() {
    KeswaTheme {
        ImportContent(
            state = ImportUiState(
                text = "…",
                problems = listOf(
                    ImportProblem(3, "cost 'two hundred' is not an amount"),
                    ImportProblem(7, "SKU KSW-TSH-001-NV already appears on line 1"),
                ),
            ),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun ImportEmptyPreview() {
    KeswaTheme { ImportContent(state = ImportUiState(), onEvent = {}, onBack = {}) }
}
