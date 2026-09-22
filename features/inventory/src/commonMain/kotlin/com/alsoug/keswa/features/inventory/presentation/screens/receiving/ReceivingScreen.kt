package com.alsoug.keswa.features.inventory.presentation.screens.receiving

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alsoug.keswa.core.designsystem.KeswaTheme
import com.alsoug.keswa.core.domain.model.DocumentStatus
import com.alsoug.keswa.core.domain.model.StockReceipt
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.CostChange
import org.jetbrains.compose.ui.tooling.preview.Preview

/** ADR-012 — a humble view: renders state, forwards events, owns no business logic. */
@Composable
fun ReceivingScreen(
    viewModel: ReceivingViewModel,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onEvent(ReceivingUiEvent.Load) }

    LaunchedEffect(Unit) {
        viewModel.navigation.collect { navigation ->
            when (navigation) {
                ReceivingNavigation.Done -> onBack()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ReceivingUiEffect.ShowError -> onMessage(effect.message)
                is ReceivingUiEffect.ShowMessage -> onMessage(effect.message)
            }
        }
    }

    ReceivingContent(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}

@Composable
internal fun ReceivingContent(
    state: ReceivingUiState,
    onEvent: (ReceivingUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (state.isLoading || state.isPosting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Receiving", style = MaterialTheme.typography.titleMedium)
        }

        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                if (state.receipt == null) StartForm(state, onEvent) else ScanBar(state, onEvent)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Lines(state, onEvent, modifier = Modifier.weight(1f))
            }

            VerticalDivider()

            Column(modifier = Modifier.width(320.dp).padding(12.dp)) {
                Summary(state, onEvent)
                if (state.costChanges.isNotEmpty()) CostChanges(state.costChanges)
                if (state.recent.isNotEmpty()) Recent(state, onEvent)
            }
        }
    }

    state.pendingItem?.let { LineDialog(state, onEvent) }
}

@Composable
private fun StartForm(state: ReceivingUiState, onEvent: (ReceivingUiEvent) -> Unit) {
    Column {
        Text(
            "Nothing moves until the delivery is posted, so unpacking can be interrupted.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.reference,
            onValueChange = { onEvent(ReceivingUiEvent.ReferenceChanged(it)) },
            label = { Text("Reference — invoice or delivery note") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            value = state.supplierName,
            onValueChange = { onEvent(ReceivingUiEvent.SupplierChanged(it)) },
            label = { Text("Supplier") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Button(
            onClick = { onEvent(ReceivingUiEvent.StartReceipt) },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Start delivery") }
    }
}

@Composable
private fun ScanBar(state: ReceivingUiState, onEvent: (ReceivingUiEvent) -> Unit) {
    OutlinedTextField(
        value = state.scanEntry,
        onValueChange = { onEvent(ReceivingUiEvent.ScanEntryChanged(it)) },
        label = { Text("Scan a garment to add it — colour by colour") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = { onEvent(ReceivingUiEvent.Scanned(state.scanEntry)) },
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Lines(
    state: ReceivingUiState,
    onEvent: (ReceivingUiEvent) -> Unit,
    modifier: Modifier,
) {
    if (state.lines.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (state.receipt == null) "Start a delivery to begin" else "Scan the first carton",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(state.lines, key = { it.lineId }) { line ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        line.description.ifBlank { line.sku },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "${line.sku} · ${line.quantity} × ${line.unitCost.format()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(line.lineTotal.format(), style = MaterialTheme.typography.bodyLarge)
                if (state.isDraft) {
                    TextButton(onClick = { onEvent(ReceivingUiEvent.RemoveLine(line.lineId)) }) {
                        Text("✕")
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun Summary(state: ReceivingUiState, onEvent: (ReceivingUiEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        state.receipt?.let { receipt ->
            Text(receipt.reference, style = MaterialTheme.typography.titleSmall)
            Text(
                receipt.supplierName.ifBlank { "No supplier" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Pieces", style = MaterialTheme.typography.bodyMedium)
            Text("${state.pieceCount}", style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Cost", style = MaterialTheme.typography.titleMedium)
            Text(state.totalCost.format(), style = MaterialTheme.typography.titleMedium)
        }

        if (state.isDraft) {
            Button(
                onClick = { onEvent(ReceivingUiEvent.Post) },
                enabled = state.canPost,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("Post delivery") }
            OutlinedButton(
                onClick = { onEvent(ReceivingUiEvent.Discard) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) { Text("Discard") }
        } else if (state.receipt != null) {
            OutlinedButton(
                onClick = { onEvent(ReceivingUiEvent.PrintTags) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("Print hang tags") }
        }
    }
}

@Composable
private fun CostChanges(changes: List<CostChange>) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Cost moved", style = MaterialTheme.typography.titleSmall)
            Text(
                "Weighted average, so the new figure sits between the old stock and this delivery.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            changes.forEach { change ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(change.variantId.take(8), style = MaterialTheme.typography.labelSmall)
                    Text(
                        "${change.before.format()} → ${change.after.format()}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun Recent(state: ReceivingUiState, onEvent: (ReceivingUiEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text("Recent deliveries", style = MaterialTheme.typography.titleSmall)
        state.recent.take(MAX_RECENT).forEach { receipt ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEvent(ReceivingUiEvent.Open(receipt.id)) }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(receipt.reference, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (receipt.isDraft) "draft" else receipt.totalCost.format(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LineDialog(state: ReceivingUiState, onEvent: (ReceivingUiEvent) -> Unit) {
    val item = state.pendingItem ?: return

    AlertDialog(
        onDismissRequest = { onEvent(ReceivingUiEvent.CancelLine) },
        title = { Text(item.description) },
        text = {
            Column {
                Text(item.sku, style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(
                    value = state.quantityEntry,
                    onValueChange = { onEvent(ReceivingUiEvent.QuantityChanged(it)) },
                    label = { Text("Quantity") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = state.costEntry,
                    onValueChange = { onEvent(ReceivingUiEvent.CostChanged(it)) },
                    label = { Text("Unit cost") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onEvent(ReceivingUiEvent.ConfirmLine) }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ReceivingUiEvent.CancelLine) }) { Text("Cancel") }
        },
    )
}

private const val MAX_RECENT = 6

private val previewReceipt = StockReceipt(
    id = "rec-1",
    reference = "INV-4471",
    supplierName = "Nile Textiles",
    locationId = "loc-shop",
    status = DocumentStatus.DRAFT,
    note = null,
    createdAt = 0,
    createdByUserId = "usr-1",
)

private val previewLines = listOf(
    ReceiptLineUiModel(
        lineId = "l1",
        variantId = "v1",
        sku = "KSW-TSH-022-NV",
        description = "Round-neck t-shirt — Navy",
        quantity = 20,
        unitCost = Money.ofPounds(120),
        lineTotal = Money.ofPounds(2_400),
    ),
    ReceiptLineUiModel(
        lineId = "l2",
        variantId = "v2",
        sku = "KSW-TSH-022-WH",
        description = "Round-neck t-shirt — White",
        quantity = 30,
        unitCost = Money.ofPounds(118),
        lineTotal = Money.ofPounds(3_540),
    ),
)

@Preview
@Composable
private fun ReceivingDraftPreview() {
    KeswaTheme {
        ReceivingContent(
            state = ReceivingUiState(receipt = previewReceipt, lines = previewLines),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun ReceivingPostedPreview() {
    KeswaTheme {
        ReceivingContent(
            state = ReceivingUiState(
                receipt = previewReceipt.copy(status = DocumentStatus.POSTED),
                lines = previewLines,
                costChanges = listOf(
                    CostChange("v1", Money.ofPounds(110), Money.ofPounds(117)),
                ),
            ),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun ReceivingLoadingPreview() {
    KeswaTheme {
        ReceivingContent(state = ReceivingUiState(isLoading = true), onEvent = {}, onBack = {})
    }
}
