package com.alsoug.keswa.features.sell.presentation.screens.till

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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alsoug.keswa.core.designsystem.KeswaTheme
import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.core.domain.model.TenderMethod
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.features.sell.domain.model.Basket
import com.alsoug.keswa.features.sell.domain.model.BasketLine
import com.alsoug.keswa.features.sell.domain.usecase.CalculateBasketTotalUseCase
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * ADR-012 — a humble view: renders state, forwards events, owns no business logic.
 *
 * Nothing on this screen adds up money. Every figure comes from the totals the ViewModel was
 * handed by [CalculateBasketTotalUseCase].
 */
@Composable
fun TillScreen(
    viewModel: TillViewModel,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onEvent(TillUiEvent.Load) }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is TillUiEffect.ShowError -> onMessage(effect.message)
                is TillUiEffect.ShowMessage -> onMessage(effect.message)
                is TillUiEffect.StockWarning -> onMessage(effect.message)
            }
        }
    }

    TillContent(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
internal fun TillContent(
    state: TillUiState,
    onEvent: (TillUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (state.isLoading || state.isCommitting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                ScanBar(state, onEvent)
                if (state.results.isNotEmpty()) SearchResults(state.results, onEvent)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                CartLines(state, onEvent, modifier = Modifier.weight(1f))
            }

            VerticalDivider()

            Column(modifier = Modifier.width(320.dp).padding(12.dp)) {
                ShiftBanner(state, onEvent)
                TotalsPanel(state)
                Actions(state, onEvent)
                if (state.heldSales.isNotEmpty()) HeldSales(state, onEvent)
            }
        }
    }

    // ADR-030: dialogs are driven by what is in state, never by a boolean the screen keeps.
    state.pendingApproval?.let { pending -> ApprovalDialog(pending, onEvent) }
    if (state.isTendering) TenderDialog(state, onEvent)
}

@Composable
private fun ScanBar(state: TillUiState, onEvent: (TillUiEvent) -> Unit) {
    var entry by remember { mutableStateOf("") }

    OutlinedTextField(
        value = entry,
        onValueChange = { entry = it },
        label = { Text("Scan or search — barcode, SKU or name") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = {
                // A barcode scanner is an HID keyboard that types and presses Enter, so the same
                // field serves both; the domain decides what it found.
                onEvent(TillUiEvent.Scanned(entry))
                entry = ""
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                onEvent(TillUiEvent.QueryChanged(entry))
                onEvent(TillUiEvent.Search)
            },
        ) { Text("Search") }
        if (!state.basket.isEmpty) {
            OutlinedButton(onClick = { onEvent(TillUiEvent.ClearBasket) }) { Text("Clear") }
        }
    }
}

@Composable
private fun SearchResults(results: List<SellableItem>, onEvent: (TillUiEvent) -> Unit) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        results.take(MAX_VISIBLE_RESULTS).forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEvent(TillUiEvent.PickResult(item.variantId)) }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(item.description, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${item.sku} · ${item.onHand} in stock",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(item.price?.format() ?: "no price", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun CartLines(state: TillUiState, onEvent: (TillUiEvent) -> Unit, modifier: Modifier) {
    if (state.basket.isEmpty) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Scan something to start",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        itemsIndexed(state.totals.lines) { index, allocated ->
            CartLineRow(index, allocated.line, allocated.lineTotal, onEvent)
            HorizontalDivider()
        }
    }
}

@Composable
private fun CartLineRow(
    index: Int,
    line: BasketLine,
    lineTotal: Money,
    onEvent: (TillUiEvent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(line.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                buildString {
                    append(line.sku)
                    append(" · ")
                    append(line.unitPrice.format())
                    if (line.isPriceOverridden) append(" (was ${line.listPrice.format()})")
                    if (line.isDiscounted) append(" · −${line.lineDiscount.format()}")
                    // The shop's figures say this is not there. Worth seeing before it is sold,
                    // not only in a warning afterwards.
                    if (line.exceedsStock) append(" · only ${line.onHand} in stock")
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (line.exceedsStock) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        TextButton(onClick = { onEvent(TillUiEvent.QuantityChanged(index, line.quantity - 1)) }) {
            Text("−")
        }
        Text("${line.quantity}", style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = { onEvent(TillUiEvent.QuantityChanged(index, line.quantity + 1)) }) {
            Text("+")
        }

        Text(
            lineTotal.format(),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp),
        )
        TextButton(onClick = { onEvent(TillUiEvent.RemoveLine(index)) }) { Text("✕") }
    }
}

@Composable
private fun ShiftBanner(state: TillUiState, onEvent: (TillUiEvent) -> Unit) {
    var float by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (state.shift == null) {
                Text("No shift open", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Sales still work, but they will not appear on a Z-report.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = float,
                    onValueChange = { float = it },
                    label = { Text("Opening float") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Button(
                    onClick = { onEvent(TillUiEvent.OpenShift(float)) },
                    modifier = Modifier.padding(top = 6.dp),
                ) { Text("Open shift") }
            } else {
                Text("Shift open", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Float ${state.shift.openingFloat.format()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TotalsPanel(state: TillUiState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AmountRow("Subtotal", state.totals.subtotal)
        if (!state.totals.discount.isZero) AmountRow("Discount", -state.totals.discount)
        if (state.vatBasisPoints > 0) AmountRow("VAT · ض.ق.م", state.totals.tax)
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        AmountRow("Total", state.totals.total, emphasised = true)
        Text(
            "${state.totals.itemCount} pcs",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AmountRow(label: String, amount: Money, emphasised: Boolean = false) {
    val style = if (emphasised) {
        MaterialTheme.typography.titleLarge
    } else {
        MaterialTheme.typography.bodyMedium
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = style)
        Text(amount.format(), style = style)
    }
}

@Composable
private fun Actions(state: TillUiState, onEvent: (TillUiEvent) -> Unit) {
    var discount by remember { mutableStateOf("") }
    var holdLabel by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Button(
            onClick = { onEvent(TillUiEvent.StartTender) },
            enabled = state.canTender,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Take payment") }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = discount,
                onValueChange = { discount = it },
                label = { Text("Order discount") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { onEvent(TillUiEvent.RequestOrderDiscount(discount)) },
                enabled = !state.basket.isEmpty,
            ) { Text("Apply") }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = holdLabel,
                onValueChange = { holdLabel = it },
                label = { Text("Hold as") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    onEvent(TillUiEvent.Hold(holdLabel))
                    holdLabel = ""
                },
                enabled = !state.basket.isEmpty,
            ) { Text("Hold") }
        }

        if (state.lastSaleId != null) {
            OutlinedButton(
                onClick = { onEvent(TillUiEvent.Reprint) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("Reprint #${state.lastReceiptNumber}") }
        }
    }
}

@Composable
private fun HeldSales(state: TillUiState, onEvent: (TillUiEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text("Held", style = MaterialTheme.typography.titleSmall)
        state.heldSales.forEach { held ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(held.label, style = MaterialTheme.typography.bodyMedium)
                Row {
                    TextButton(onClick = { onEvent(TillUiEvent.Resume(held.id)) }) { Text("Resume") }
                    TextButton(onClick = { onEvent(TillUiEvent.DiscardHeld(held.id)) }) { Text("✕") }
                }
            }
        }
    }
}

@Composable
private fun TenderDialog(state: TillUiState, onEvent: (TillUiEvent) -> Unit) {
    var cash by remember { mutableStateOf("") }
    var card by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { onEvent(TillUiEvent.CancelTender) },
        title = { Text("Take ${state.totals.total.format()}") },
        text = {
            Column {
                state.tenders.forEachIndexed { index, tender ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${tender.method.name} ${tender.amount.format()}")
                        TextButton(onClick = { onEvent(TillUiEvent.RemoveTender(index)) }) { Text("✕") }
                    }
                }

                OutlinedTextField(
                    value = cash,
                    onValueChange = { cash = it },
                    label = { Text("Cash handed over") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                TextButton(
                    onClick = {
                        // Over-tendered cash settles only what is outstanding; the rest is change.
                        val handed = Money.parse(cash) ?: Money.ZERO
                        val applied = if (handed > state.outstanding) state.outstanding else handed
                        onEvent(TillUiEvent.AddTender(TenderMethod.CASH, applied.format(), cash))
                        cash = ""
                    },
                ) { Text("Add cash") }

                OutlinedTextField(
                    value = card,
                    onValueChange = { card = it },
                    label = { Text("Card") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                TextButton(
                    onClick = {
                        onEvent(TillUiEvent.AddTender(TenderMethod.CARD, card, card))
                        card = ""
                    },
                ) { Text("Add card") }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                AmountRow("Outstanding", state.outstanding)
                AmountRow("Change", state.change)
            }
        },
        confirmButton = {
            Button(
                onClick = { onEvent(TillUiEvent.Complete) },
                enabled = state.isFullySettled && !state.isCommitting,
            ) { Text("Complete") }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(TillUiEvent.CancelTender) }) { Text("Cancel") }
        },
    )
}

@Composable
private fun ApprovalDialog(pending: PendingApproval, onEvent: (TillUiEvent) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { onEvent(TillUiEvent.CancelApproval) },
        title = { Text("Approval needed") },
        text = {
            Column {
                Text(
                    "This needs ${pending.permission.name.lowercase().replace('_', ' ')}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onEvent(TillUiEvent.Approve(username, password)) }) { Text("Approve") }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(TillUiEvent.CancelApproval) }) { Text("Cancel") }
        },
    )
}

private const val MAX_VISIBLE_RESULTS = 8

/**
 * ADR-027 — loading, empty and a full cart.
 *
 * ADR-030 keeps failures out of `UiState`, so there is no error state to render; the third case
 * worth eyeballing is a cart carrying a discount and a line that oversells stock, because that is
 * where the screen has the most to say.
 */
private val previewBasket = Basket(
    lines = listOf(
        BasketLine(
            variantId = "v1",
            sku = "KSW-TSH-022-NV",
            description = "Round-neck t-shirt — Navy",
            descriptionAr = "تيشيرت رقبة دائرية — كحلي",
            quantity = 2,
            unitPrice = Money.ofPounds(180),
            listPrice = Money.ofPounds(180),
            unitCost = Money.ofPounds(120),
            onHand = 6,
        ),
        BasketLine(
            variantId = "v2",
            sku = "KSW-SHT-004-WH",
            description = "Oxford shirt — White",
            descriptionAr = "قميص أكسفورد — أبيض",
            quantity = 3,
            unitPrice = Money.ofPounds(340),
            listPrice = Money.ofPounds(380),
            unitCost = Money.ofPounds(240),
            lineDiscount = Money.ofPounds(40),
            onHand = 1,
            authorisedByUserId = "admin",
        ),
    ),
    orderDiscount = Money.ofPounds(50),
    orderDiscountAuthorisedByUserId = "admin",
)

@Preview
@Composable
private fun TillSuccessPreview() {
    KeswaTheme {
        TillContent(
            state = TillUiState(
                basket = previewBasket,
                totals = CalculateBasketTotalUseCase()(previewBasket, vatBasisPoints = 1_400),
                vatBasisPoints = 1_400,
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun TillLoadingPreview() {
    KeswaTheme { TillContent(state = TillUiState(isLoading = true), onEvent = {}) }
}

@Preview
@Composable
private fun TillEmptyPreview() {
    KeswaTheme { TillContent(state = TillUiState(), onEvent = {}) }
}
