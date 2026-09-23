package com.alsoug.keswa.features.sell.presentation.screens.till

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alsoug.keswa.core.designsystem.EmptyState
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
    val focus = remember { FocusRequester() }

    // A barcode scanner is an HID keyboard: it types the code and presses Enter into whatever has
    // focus. If focus has drifted — to a discount field, or nowhere after a dialog closed — the
    // scan is silently swallowed and the operator scans again, harder. So this field takes focus
    // on arrival and takes it back whenever the till returns to rest.
    LaunchedEffect(state.isTendering, state.pendingApproval, state.basket.lines.size) {
        if (!state.isTendering && state.pendingApproval == null) {
            runCatching { focus.requestFocus() }
        }
    }

    OutlinedTextField(
        value = entry,
        onValueChange = { entry = it },
        label = { Text(KeswaTheme.strings.scanOrSearch) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = {
                onEvent(TillUiEvent.Scanned(entry))
                entry = ""
            },
        ),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
    Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                onEvent(TillUiEvent.QueryChanged(entry))
                onEvent(TillUiEvent.Search)
            },
        ) { Text(KeswaTheme.strings.search) }
        if (!state.basket.isEmpty) {
            OutlinedButton(onClick = { onEvent(TillUiEvent.ClearBasket) }) { Text(KeswaTheme.strings.clear) }
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
                Text(item.price?.format() ?: KeswaTheme.strings.noPrice, style = KeswaTheme.figure)
            }
        }
    }
}

@Composable
private fun CartLines(state: TillUiState, onEvent: (TillUiEvent) -> Unit, modifier: Modifier) {
    if (state.basket.isEmpty) {
        EmptyState(
            title = KeswaTheme.strings.scanToStart,
            hint = KeswaTheme.strings.scanToStartHint,
            modifier = modifier,
        )
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(line.description, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${line.sku} · ${line.unitPrice.format()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = KeswaTheme.semantics.muted,
                )
                if (line.isPriceOverridden) {
                    Marker("was ${line.listPrice.format()}", KeswaTheme.semantics.warning)
                }
                if (line.isDiscounted) {
                    Marker("−${line.lineDiscount.format()}", KeswaTheme.semantics.sold)
                }
                // The shop's figures say this is not there. Worth seeing before it is sold, not
                // only in a warning afterwards — and as its own mark rather than the tail of a
                // sentence somebody has stopped reading by the third sale of the morning.
                if (line.exceedsStock) {
                    Marker("only ${line.onHand} in stock", MaterialTheme.colorScheme.error)
                }
            }
        }

        QuantityStepper(
            quantity = line.quantity,
            onChange = { onEvent(TillUiEvent.QuantityChanged(index, it)) },
        )

        // Fixed width and tabular, so every line total in the cart sits on the same decimal point.
        Text(
            lineTotal.format(),
            style = KeswaTheme.figure,
            textAlign = TextAlign.End,
            modifier = Modifier.width(96.dp).padding(start = 8.dp),
        )
        TextButton(onClick = { onEvent(TillUiEvent.RemoveLine(index)) }) { Text("✕") }
    }
}

/** A small coloured mark beside the line's detail. Never coloured text — it fails at this size. */
@Composable
private fun Marker(label: String, colour: Color) {
    Row(
        modifier = Modifier.padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(colour, RoundedCornerShape(2.dp)))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/**
 * Minus, quantity, plus — at a size a finger can hit and an eye can read.
 *
 * Minus is disabled at one rather than removing the line: a mis-tap that empties a line is a
 * re-scan, and the ✕ is right there for when removal is what was meant.
 */
@Composable
private fun QuantityStepper(quantity: Int, onChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(KeswaTheme.semantics.sunk, RoundedCornerShape(5.dp))
            .padding(2.dp),
    ) {
        StepperButton("−", enabled = quantity > 1) { onChange(quantity - 1) }
        Text(
            quantity.toString(),
            style = KeswaTheme.figure,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp),
        )
        StepperButton("+", enabled = true) { onChange(quantity + 1) }
    }
}

@Composable
private fun StepperButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(28.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                KeswaTheme.semantics.muted
            },
        )
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(KeswaTheme.semantics.sunk, RoundedCornerShape(7.dp))
            .padding(12.dp),
    ) {
        AmountRow(KeswaTheme.strings.subtotal, state.totals.subtotal)
        if (!state.totals.discount.isZero) AmountRow(KeswaTheme.strings.discount, -state.totals.discount)
        if (state.vatBasisPoints > 0) AmountRow(KeswaTheme.strings.vat, state.totals.tax)
        HorizontalDivider(
            color = KeswaTheme.semantics.hair,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        // The total is the number the customer is about to be asked for, and the one the operator
        // reads out. It was `titleLarge`, the same size as a section heading.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(KeswaTheme.strings.total, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${state.totals.itemCount} ${KeswaTheme.strings.pieces}",
                    style = MaterialTheme.typography.labelSmall,
                    color = KeswaTheme.semantics.muted,
                )
            }
            Text(state.totals.total.format(), style = KeswaTheme.figureLarge)
        }
    }
}

@Composable
private fun AmountRow(label: String, amount: Money, emphasised: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(amount.format(), style = if (emphasised) KeswaTheme.figureLarge else KeswaTheme.figure)
    }
}

@Composable
private fun Actions(state: TillUiState, onEvent: (TillUiEvent) -> Unit) {
    var discount by remember { mutableStateOf("") }
    var holdLabel by remember { mutableStateOf("") }
    // Local disclosure, per ADR-030: a `remember` in the screen, never a boolean in the state.
    var showMore by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        // Tall on purpose. It is the action of the screen, it is hit hundreds of times a day, and
        // on a touch till a 40dp button is a mis-tap waiting for a queue.
        Button(
            onClick = { onEvent(TillUiEvent.StartTender) },
            enabled = state.canTender,
            shape = RoundedCornerShape(7.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(KeswaTheme.strings.takePayment, style = MaterialTheme.typography.titleMedium)
        }

        // A discount and a hold are the exceptions, not the routine, and two permanently-open text
        // fields said the opposite — they took a third of the column and drew the eye away from
        // the total on every single sale.
        TextButton(
            onClick = { showMore = !showMore },
            enabled = !state.basket.isEmpty,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text(if (showMore) KeswaTheme.strings.fewerOptions else KeswaTheme.strings.discountOrHold)
        }

        if (showMore) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = discount,
                    onValueChange = { discount = it },
                    label = { Text(KeswaTheme.strings.orderDiscount) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onEvent(TillUiEvent.RequestOrderDiscount(discount)) },
                    enabled = !state.basket.isEmpty,
                ) { Text(KeswaTheme.strings.apply) }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = holdLabel,
                    onValueChange = { holdLabel = it },
                    label = { Text(KeswaTheme.strings.holdAs) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        onEvent(TillUiEvent.Hold(holdLabel))
                        holdLabel = ""
                        showMore = false
                    },
                    enabled = !state.basket.isEmpty,
                ) { Text(KeswaTheme.strings.hold) }
            }
        }

        if (state.lastSaleId != null) {
            OutlinedButton(
                onClick = { onEvent(TillUiEvent.Reprint) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("${KeswaTheme.strings.reprint} #${state.lastReceiptNumber}") }
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
