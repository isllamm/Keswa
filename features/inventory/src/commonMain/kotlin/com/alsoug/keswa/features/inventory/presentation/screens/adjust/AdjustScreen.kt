package com.alsoug.keswa.features.inventory.presentation.screens.adjust

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alsoug.keswa.core.designsystem.KeswaTheme
import com.alsoug.keswa.core.domain.model.MovementReason
import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.core.domain.model.StockMovement
import com.alsoug.keswa.core.domain.money.Money
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun AdjustScreen(
    viewModel: AdjustViewModel,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onEvent(AdjustUiEvent.Load) }

    LaunchedEffect(Unit) {
        viewModel.navigation.collect { navigation ->
            when (navigation) {
                AdjustNavigation.Done -> onBack()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is AdjustUiEffect.ShowError -> onMessage(effect.message)
                is AdjustUiEffect.ShowMessage -> onMessage(effect.message)
            }
        }
    }

    AdjustContent(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}

@Composable
internal fun AdjustContent(
    state: AdjustUiState,
    onEvent: (AdjustUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Adjust stock", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "For what a delivery and a count cannot explain: damage, theft, a sample given away.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.scanEntry,
            onValueChange = { onEvent(AdjustUiEvent.ScanEntryChanged(it)) },
            label = { Text("Scan the garment") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onEvent(AdjustUiEvent.Scanned(state.scanEntry)) }),
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(top = 8.dp),
        )

        state.item?.let { item -> Form(item, state, onEvent) }

        if (state.history.isNotEmpty()) History(state.history, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Form(item: SellableItem, state: AdjustUiState, onEvent: (AdjustUiEvent) -> Unit) {
    Card(modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(top = 12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(item.description, style = MaterialTheme.typography.titleSmall)
            Text(
                "${item.sku} · ${item.onHand} on hand",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = state.quantityEntry,
                onValueChange = { onEvent(AdjustUiEvent.QuantityChanged(it)) },
                // Signed, because a correction goes both ways and "-3" is clearer than a
                // direction toggle the operator has to notice.
                label = { Text("Change — negative to write off, e.g. -3") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(MovementReason.DAMAGE, MovementReason.ADJUSTMENT).forEach { reason ->
                    FilterChip(
                        selected = state.reason == reason,
                        onClick = { onEvent(AdjustUiEvent.ReasonChanged(reason)) },
                        label = { Text(reason.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = { onEvent(AdjustUiEvent.NoteChanged(it)) },
                label = { Text("Reason — required") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { onEvent(AdjustUiEvent.Apply) }, enabled = state.canApply) {
                    Text("Apply")
                }
                TextButton(onClick = { onEvent(AdjustUiEvent.Clear) }) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun History(movements: List<StockMovement>, modifier: Modifier) {
    Column(modifier = modifier.padding(top = 16.dp)) {
        Text("This item's ledger", style = MaterialTheme.typography.titleSmall)
        Text(
            "Append-only: a correction is another line, never an edit of one above it.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 6.dp)) {
            items(movements.asReversed(), key = { it.id }) { movement ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            movement.reason.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        movement.note?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        if (movement.quantity > 0) "+${movement.quantity}" else "${movement.quantity}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

private val previewItem = SellableItem(
    variantId = "v1",
    productId = "p1",
    sku = "KSW-TSH-022-NV",
    name = "Round-neck t-shirt",
    nameAr = "تيشيرت رقبة دائرية",
    colourName = "Navy",
    colourNameAr = "كحلي",
    cost = Money.ofPounds(120),
    price = Money.ofPounds(180),
    onHand = 9,
)

private val previewHistory = listOf(
    StockMovement("m1", "v1", "loc-shop", 24, MovementReason.RECEIPT, null, null, 0, "usr-1"),
    StockMovement("m2", "v1", "loc-shop", -12, MovementReason.SALE, "SALE", "s1", 1, "usr-1"),
    StockMovement(
        id = "m3",
        variantId = "v1",
        locationId = "loc-shop",
        quantity = -3,
        reason = MovementReason.DAMAGE,
        refType = null,
        refId = null,
        occurredAt = 2,
        userId = "usr-1",
        note = "water damage in the stockroom",
    ),
)

@Preview
@Composable
private fun AdjustSuccessPreview() {
    KeswaTheme {
        AdjustContent(
            state = AdjustUiState(
                item = previewItem,
                quantityEntry = "-3",
                note = "water damage in the stockroom",
                history = previewHistory,
            ),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun AdjustEmptyPreview() {
    KeswaTheme { AdjustContent(state = AdjustUiState(), onEvent = {}, onBack = {}) }
}

@Preview
@Composable
private fun AdjustLoadingPreview() {
    KeswaTheme { AdjustContent(state = AdjustUiState(isLoading = true), onEvent = {}, onBack = {}) }
}
