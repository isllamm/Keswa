package com.alsoug.keswa.features.inventory.presentation.screens.count

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alsoug.keswa.core.designsystem.EmptyState
import com.alsoug.keswa.core.designsystem.KeswaTheme
import com.alsoug.keswa.core.designsystem.ScreenHeader
import com.alsoug.keswa.core.domain.model.DocumentStatus
import com.alsoug.keswa.core.domain.model.StockCount
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun CountScreen(
    viewModel: CountViewModel,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onEvent(CountUiEvent.Load) }

    LaunchedEffect(Unit) {
        viewModel.navigation.collect { navigation ->
            when (navigation) {
                CountNavigation.Done -> onBack()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is CountUiEffect.ShowError -> onMessage(effect.message)
                is CountUiEffect.ShowMessage -> onMessage(effect.message)
            }
        }
    }

    CountContent(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}

@Composable
internal fun CountContent(
    state: CountUiState,
    onEvent: (CountUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        if (state.isLoading || state.isPosting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        ScreenHeader(
            title = KeswaTheme.strings.stockCount,
            subtitle = KeswaTheme.strings.stockCountHint,
            onBack = onBack,
        )

        when {
            state.count == null -> StartCard(onEvent)
            state.isOpen -> OpenCount(state, onEvent)
            else -> PostedCount(state)
        }
    }

    if (state.pendingVariantId != null) CountDialog(state, onEvent)
}

@Composable
private fun StartCard(onEvent: (CountUiEvent) -> Unit) {
    Column(modifier = Modifier.widthIn(max = 520.dp).padding(top = 12.dp)) {
        Text("Blind by design", style = MaterialTheme.typography.titleSmall)
        Text(
            "You will not see what the system expects until the count is posted. A counter who " +
                "can see that the system expects twelve will count until they get twelve — and " +
                "the discrepancy that would have told the owner something disappears.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { onEvent(CountUiEvent.Start) }, modifier = Modifier.padding(top = 12.dp)) {
            Text("Start a count")
        }
    }
}

@Composable
private fun ColumnScope.OpenCount(state: CountUiState, onEvent: (CountUiEvent) -> Unit) {
    OutlinedTextField(
        value = state.scanEntry,
        onValueChange = { onEvent(CountUiEvent.ScanEntryChanged(it)) },
        label = { Text("Scan a garment, then type what is on the shelf") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onEvent(CountUiEvent.Scanned(state.scanEntry)) }),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
        items(state.lines, key = { it.lineId }) { line ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        line.description.ifBlank { line.sku },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        line.sku,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Counted only. There is nothing else to show yet, and that is the feature.
                Text("${line.counted}", style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider()
        }
    }

    OutlinedTextField(
        value = state.note,
        onValueChange = { onEvent(CountUiEvent.NoteChanged(it)) },
        label = { Text("Note (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onEvent(CountUiEvent.Post) }, enabled = state.canPost) {
            Text("Post count")
        }
        OutlinedButton(onClick = { onEvent(CountUiEvent.Discard) }) { Text("Discard") }
    }
}

@Composable
private fun PostedCount(state: CountUiState) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
        Card(modifier = Modifier.widthIn(max = 520.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Counted", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${state.lines.size} line(s), ${state.discrepancies.size} did not match",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        if (state.lines.isEmpty()) {
            EmptyState(
                title = KeswaTheme.strings.nothingCounted,
                hint = KeswaTheme.strings.nothingCountedHint,
            )
            return
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.lines, key = { it.lineId }) { line ->
                val variance = line.variance ?: 0
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            line.description.ifBlank { line.sku },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "counted ${line.counted} · expected ${line.expected ?: "—"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        if (variance == 0) "—" else (if (variance > 0) "+$variance" else "$variance"),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (variance == 0) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CountDialog(state: CountUiState, onEvent: (CountUiEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(CountUiEvent.CancelLine) },
        title = { Text(state.pendingSku.orEmpty()) },
        text = {
            OutlinedTextField(
                value = state.countedEntry,
                onValueChange = { onEvent(CountUiEvent.CountedChanged(it)) },
                label = { Text("How many are on the shelf?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onEvent(CountUiEvent.ConfirmLine) }) { Text("Record") }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(CountUiEvent.CancelLine) }) { Text("Cancel") }
        },
    )
}

private val previewCount = StockCount(
    id = "cnt-1",
    locationId = "loc-shop",
    status = DocumentStatus.DRAFT,
    note = null,
    startedAt = 0,
    startedByUserId = "usr-1",
)

private val previewLines = listOf(
    CountLineUiModel("l1", "v1", "KSW-TSH-022-NV", "Round-neck t-shirt — Navy", counted = 9),
    CountLineUiModel("l2", "v2", "KSW-TSH-022-WH", "Round-neck t-shirt — White", counted = 14),
)

@Preview
@Composable
private fun CountOpenPreview() {
    KeswaTheme {
        CountContent(
            state = CountUiState(count = previewCount, lines = previewLines),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun CountPostedPreview() {
    KeswaTheme {
        CountContent(
            state = CountUiState(
                count = previewCount.copy(status = DocumentStatus.POSTED),
                lines = listOf(
                    previewLines[0].copy(expected = 12, variance = -3),
                    previewLines[1].copy(expected = 14, variance = 0),
                ),
            ),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun CountStartPreview() {
    KeswaTheme { CountContent(state = CountUiState(), onEvent = {}, onBack = {}) }
}
