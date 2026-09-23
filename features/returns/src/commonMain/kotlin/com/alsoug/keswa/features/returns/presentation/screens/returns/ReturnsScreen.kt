package com.alsoug.keswa.features.returns.presentation.screens.returns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alsoug.keswa.core.designsystem.EmptyState
import com.alsoug.keswa.core.designsystem.KeswaTheme
import com.alsoug.keswa.core.designsystem.ScreenHeader
import com.alsoug.keswa.core.designsystem.resolve
import com.alsoug.keswa.core.domain.model.ReturnCondition
import com.alsoug.keswa.core.domain.model.TenderMethod
import com.alsoug.keswa.core.domain.money.Money
import org.jetbrains.compose.ui.tooling.preview.Preview

/** ADR-012 — a humble view: renders state, forwards events, owns no business logic. */
@Composable
fun ReturnsScreen(
    viewModel: ReturnsViewModel,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val strings = KeswaTheme.strings

    LaunchedEffect(Unit) { viewModel.onEvent(ReturnsUiEvent.Load) }

    LaunchedEffect(Unit) {
        viewModel.navigation.collect { navigation ->
            when (navigation) {
                ReturnsNavigation.Done -> onBack()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ReturnsUiEffect.ShowError -> onMessage(effect.message.resolve(strings))
                is ReturnsUiEffect.ShowMessage -> onMessage(effect.message.resolve(strings))
            }
        }
    }

    ReturnsContent(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
internal fun ReturnsContent(
    state: ReturnsUiState,
    onEvent: (ReturnsUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (state.isLoading || state.isCommitting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        ScreenHeader(
            title = KeswaTheme.strings.returns,
            subtitle = KeswaTheme.strings.returnsSubtitle,
        )

        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                Lookup(state, onEvent)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Lines(state, onEvent, modifier = Modifier.weight(1f))
            }

            VerticalDivider()

            Column(modifier = Modifier.width(320.dp).padding(12.dp)) {
                Summary(state, onEvent)
            }
        }
    }

    if (state.isAwaitingApproval) ApprovalDialog(onEvent)
}

@Composable
private fun Lookup(state: ReturnsUiState, onEvent: (ReturnsUiEvent) -> Unit) {
    OutlinedTextField(
        value = state.lookupEntry,
        onValueChange = { onEvent(ReturnsUiEvent.LookupEntryChanged(it)) },
        // The QR on the receipt carries the sale's id. Phase 5 printed it for exactly this.
        label = { Text(KeswaTheme.strings.scanReceiptQrOrNumber) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onEvent(ReturnsUiEvent.Lookup) }),
        modifier = Modifier.fillMaxWidth(),
    )

    if (state.hasSale) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Receipt #${state.receiptNumber} · ${state.daysSince} days ago",
                style = MaterialTheme.typography.labelMedium,
            )
            if (!state.isInsidePolicy) {
                Text(
                    "Outside the ${state.returnWindowDays}-day window",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun Lines(
    state: ReturnsUiState,
    onEvent: (ReturnsUiEvent) -> Unit,
    modifier: Modifier,
) {
    if (state.lines.isEmpty()) {
        state.lastReturn?.let { done ->
            EmptyState(
                title = "${KeswaTheme.strings.returnDone} #${done.returnNumber}",
                hint = KeswaTheme.strings.returnDoneHint,
                modifier = modifier,
            )
        } ?: EmptyState(
            title = KeswaTheme.strings.findTheReceipt,
            hint = KeswaTheme.strings.findTheReceiptHint,
            modifier = modifier,
        )
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(state.lines, key = { it.saleLineId }) { line ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(line.description, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            buildString {
                                append("${line.unitPrice.format()} each · sold ${line.soldQuantity}")
                                if (line.alreadyReturned > 0) {
                                    append(" · ${line.alreadyReturned} already back")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (line.isFullyReturned) {
                        Text(
                            KeswaTheme.strings.allReturned,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        TextButton(
                            onClick = {
                                onEvent(
                                    ReturnsUiEvent.QuantityChanged(
                                        line.saleLineId,
                                        line.selectedQuantity - 1,
                                    ),
                                )
                            },
                        ) { Text("−") }
                        Text("${line.selectedQuantity}", style = MaterialTheme.typography.bodyLarge)
                        TextButton(
                            onClick = {
                                onEvent(
                                    ReturnsUiEvent.QuantityChanged(
                                        line.saleLineId,
                                        line.selectedQuantity + 1,
                                    ),
                                )
                            },
                        ) { Text("+") }
                        Text(
                            line.lineRefund.format(),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                if (line.selectedQuantity > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReturnCondition.entries.forEach { condition ->
                            FilterChip(
                                selected = line.condition == condition,
                                onClick = {
                                    onEvent(ReturnsUiEvent.ConditionChanged(line.saleLineId, condition))
                                },
                                label = {
                                    Text(
                                        condition.name.lowercase().replaceFirstChar { it.uppercase() },
                                    )
                                },
                            )
                        }
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun ColumnScope.Summary(state: ReturnsUiState, onEvent: (ReturnsUiEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(KeswaTheme.strings.refund, style = MaterialTheme.typography.titleLarge)
        Text(state.refundTotal.format(), style = KeswaTheme.figureLarge)
    }

    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TenderMethod.entries.forEach { method ->
            FilterChip(
                selected = state.refundMethod == method,
                onClick = { onEvent(ReturnsUiEvent.RefundMethodChanged(method)) },
                label = { Text(method.name.lowercase().replaceFirstChar { it.uppercase() }) },
            )
        }
    }

    OutlinedTextField(
        value = state.reason,
        onValueChange = { onEvent(ReturnsUiEvent.ReasonChanged(it)) },
        label = { Text(KeswaTheme.strings.reasonRequired) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )

    if (state.needsAuthority && state.hasSale) {
        Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(KeswaTheme.strings.needsAnApproval, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (state.approvedByUserId != null) {
                        "Approved."
                    } else {
                        KeswaTheme.strings.outsideWindowAdmin
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.approvedByUserId == null) {
                    OutlinedButton(
                        onClick = { onEvent(ReturnsUiEvent.RequestApproval) },
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text(KeswaTheme.strings.getApproval) }
                }
            }
        }
    }

    Button(
        onClick = { onEvent(ReturnsUiEvent.Complete) },
        enabled = state.canComplete,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) { Text(KeswaTheme.strings.refundAmount(state.refundTotal.format())) }

    if (state.hasSale) {
        OutlinedButton(
            onClick = { onEvent(ReturnsUiEvent.Clear) },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        ) { Text(KeswaTheme.strings.startOver) }
    }
}

@Composable
private fun ApprovalDialog(onEvent: (ReturnsUiEvent) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { onEvent(ReturnsUiEvent.CancelApproval) },
        title = { Text(KeswaTheme.strings.approvalNeeded) },
        text = {
            Column {
                Text(
                    KeswaTheme.strings.adminCanTakeBack,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(KeswaTheme.strings.username) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(KeswaTheme.strings.password) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onEvent(ReturnsUiEvent.Approve(username, password)) }) {
                Text(KeswaTheme.strings.approve)
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ReturnsUiEvent.CancelApproval) }) { Text(KeswaTheme.strings.cancel) }
        },
    )
}

private val previewLines = listOf(
    ReturnLineUiModel(
        saleLineId = "sl1",
        variantId = "v1",
        description = "Round-neck t-shirt — Navy",
        soldQuantity = 3,
        alreadyReturned = 1,
        returnable = 2,
        unitPrice = Money.ofPounds(180),
        unitCost = Money.ofPounds(120),
        selectedQuantity = 1,
    ),
    ReturnLineUiModel(
        saleLineId = "sl2",
        variantId = "v2",
        description = "Oxford shirt — White",
        soldQuantity = 1,
        alreadyReturned = 1,
        returnable = 0,
        unitPrice = Money.ofPounds(340),
        unitCost = Money.ofPounds(240),
    ),
)

@Preview
@Composable
private fun ReturnsInPolicyPreview() {
    KeswaTheme {
        ReturnsContent(
            state = ReturnsUiState(
                saleId = "s1",
                receiptNumber = 412,
                daysSince = 3,
                returnWindowDays = 14,
                lines = previewLines,
                reason = "wrong size",
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun ReturnsOutsidePolicyPreview() {
    KeswaTheme {
        ReturnsContent(
            state = ReturnsUiState(
                saleId = "s1",
                receiptNumber = 412,
                daysSince = 40,
                isInsidePolicy = false,
                returnWindowDays = 14,
                lines = previewLines,
                reason = "faulty seam",
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun ReturnsEmptyPreview() {
    KeswaTheme {
        ReturnsContent(state = ReturnsUiState(returnWindowDays = 14), onEvent = {})
    }
}
