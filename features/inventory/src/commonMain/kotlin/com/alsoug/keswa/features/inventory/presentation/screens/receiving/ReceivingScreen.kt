package com.alsoug.keswa.features.inventory.presentation.screens.receiving

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alsoug.keswa.core.designsystem.EmptyState
import com.alsoug.keswa.core.designsystem.KeswaTheme
import com.alsoug.keswa.core.designsystem.ScreenHeader
import com.alsoug.keswa.core.designsystem.localisedName
import com.alsoug.keswa.core.designsystem.resolve
import com.alsoug.keswa.core.domain.model.Category
import com.alsoug.keswa.core.domain.model.DocumentStatus
import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.core.domain.model.StockReceipt
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.CostChange
import com.alsoug.keswa.features.inventory.domain.usecase.EntryProblem
import com.alsoug.keswa.features.inventory.presentation.components.CategoryPicker
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
    val strings = KeswaTheme.strings

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
                is ReceivingUiEffect.ShowError -> onMessage(effect.message.resolve(strings))
                is ReceivingUiEffect.ShowMessage -> onMessage(effect.message.resolve(strings))
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
    // ADR-030: transient dialog / handheld picker visibility lives in remember.
    var isHandheldPickerOpen by remember { mutableStateOf(false) }
    var lineForLabels by remember { mutableStateOf<ReceiptLineUiModel?>(null) }
    var labelCopiesEntry by remember { mutableStateOf("") }

    // On handheld, when "Add to delivery" succeeds (lines grow and typed is cleared), close picker.
    LaunchedEffect(state.lines.size, state.typed.isEmpty()) {
        if (isHandheldPickerOpen && state.typed.isEmpty() && state.problems.isEmpty() && state.lines.isNotEmpty()) {
            isHandheldPickerOpen = false
        }
    }

    val openCopiesDialog: (ReceiptLineUiModel) -> Unit = { line ->
        lineForLabels = line
        labelCopiesEntry = line.quantity.toString()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= WIDE_BREAKPOINT

        if (!isWide && isHandheldPickerOpen && state.isDraft) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenHeader(
                    title = KeswaTheme.strings.pickFromCategories,
                    subtitle = state.receipt?.reference.orEmpty(),
                    onBack = { isHandheldPickerOpen = false },
                )
                PickerPane(
                    state = state,
                    onEvent = onEvent,
                    modifier = Modifier.weight(1f).padding(12.dp),
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.isLoading || state.isPosting || state.isAddingLines) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                ScreenHeader(
                    title = KeswaTheme.strings.receiving,
                    subtitle = KeswaTheme.strings.receivingHint,
                    onBack = onBack,
                )

                if (isWide && state.isDraft) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        ScanBar(state, onEvent)
                    }
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxSize()) {
                        PickerPane(
                            state = state,
                            onEvent = onEvent,
                            modifier = Modifier.weight(1.35f).padding(12.dp),
                        )

                        VerticalDivider()

                        Lines(
                            state = state,
                            onEvent = onEvent,
                            onPrintLineLabels = openCopiesDialog,
                            modifier = Modifier.weight(1f).padding(12.dp),
                        )

                        VerticalDivider()

                        Column(modifier = Modifier.width(280.dp).padding(12.dp)) {
                            Summary(state, onEvent)
                            if (state.costChanges.isNotEmpty()) CostChanges(state.costChanges)
                            if (state.recent.isNotEmpty()) Recent(state, onEvent)
                        }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                            if (state.receipt == null) {
                                StartForm(state, onEvent)
                            } else if (state.isDraft) {
                                ScanBar(state, onEvent)
                                OutlinedButton(
                                    onClick = { isHandheldPickerOpen = true },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                ) {
                                    Text(KeswaTheme.strings.pickFromCategories)
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Lines(
                                state = state,
                                onEvent = onEvent,
                                onPrintLineLabels = openCopiesDialog,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        VerticalDivider()

                        Column(modifier = Modifier.width(300.dp).padding(12.dp)) {
                            Summary(state, onEvent)
                            if (state.costChanges.isNotEmpty()) CostChanges(state.costChanges)
                            if (state.recent.isNotEmpty()) Recent(state, onEvent)
                        }
                    }
                }
            }
        }
    }

    state.pendingItem?.let { LineDialog(state, onEvent) }

    lineForLabels?.let { line ->
        PrintLineLabelsDialog(
            line = line,
            copiesEntry = labelCopiesEntry,
            onCopiesChanged = { labelCopiesEntry = it },
            onConfirm = {
                val copies = labelCopiesEntry.trim().toIntOrNull() ?: line.quantity
                onEvent(ReceivingUiEvent.PrintLineLabels(line.lineId, copies))
                lineForLabels = null
            },
            onDismiss = { lineForLabels = null },
        )
    }
}

@Composable
private fun PickerPane(
    state: ReceivingUiState,
    onEvent: (ReceivingUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    CategoryPicker(
        crumbs = state.crumbs,
        subCategories = state.subCategories,
        items = state.levelItems,
        onGoUpTo = { onEvent(ReceivingUiEvent.GoUpTo(it)) },
        onOpenCategory = { onEvent(ReceivingUiEvent.OpenCategory(it)) },
        modifier = modifier,
        footer = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                OutlinedButton(
                    onClick = { onEvent(ReceivingUiEvent.ClearPicked) },
                    enabled = state.typed.isNotEmpty() || state.problems.isNotEmpty(),
                ) {
                    Text(KeswaTheme.strings.clear)
                }
                Button(
                    onClick = { onEvent(ReceivingUiEvent.AddPicked) },
                    enabled = state.canAddLines,
                ) {
                    Text(KeswaTheme.strings.addToDelivery(state.typedCount))
                }
            }
        },
        rowFields = { item ->
            val typed = state.typed[item.variantId]
            val existingLine = state.receipt?.lines?.firstOrNull { it.variantId == item.variantId }
            val quantityValue = typed?.quantity ?: existingLine?.quantity?.toString().orEmpty()
            val costValue = typed?.cost ?: (existingLine?.unitCost ?: item.cost).format()
            val priceValue = typed?.price ?: (existingLine?.sellPrice ?: item.price)?.format().orEmpty()
            val problem = state.problems[item.variantId]

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = quantityValue,
                        onValueChange = {
                            onEvent(ReceivingUiEvent.PickQuantityChanged(item.variantId, it))
                        },
                        label = { Text(KeswaTheme.strings.quantity) },
                        isError = problem == EntryProblem.NOT_A_QUANTITY,
                        singleLine = true,
                        modifier = Modifier.weight(0.8f),
                    )
                    OutlinedTextField(
                        value = costValue,
                        onValueChange = {
                            onEvent(ReceivingUiEvent.PickCostChanged(item.variantId, it))
                        },
                        label = { Text(KeswaTheme.strings.cost) },
                        isError = problem == EntryProblem.NOT_AN_AMOUNT,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = priceValue,
                        onValueChange = {
                            onEvent(ReceivingUiEvent.PickPriceChanged(item.variantId, it))
                        },
                        label = { Text(KeswaTheme.strings.price) },
                        isError = problem == EntryProblem.NOT_AN_AMOUNT || problem == EntryProblem.PRICE_REQUIRED,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (problem != null) {
                    val problemText = when (problem) {
                        EntryProblem.NOT_A_QUANTITY -> KeswaTheme.strings.notAQuantity
                        EntryProblem.NOT_AN_AMOUNT -> KeswaTheme.strings.notAnAmount
                        EntryProblem.PRICE_REQUIRED -> KeswaTheme.strings.priceRequired
                    }
                    Text(
                        text = problemText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun StartForm(state: ReceivingUiState, onEvent: (ReceivingUiEvent) -> Unit) {
    Column {
        Text(
            KeswaTheme.strings.nothingMovesUntilPosted,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.reference,
            onValueChange = { onEvent(ReceivingUiEvent.ReferenceChanged(it)) },
            label = { Text(KeswaTheme.strings.referenceInvoiceOrNote) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            value = state.supplierName,
            onValueChange = { onEvent(ReceivingUiEvent.SupplierChanged(it)) },
            label = { Text(KeswaTheme.strings.supplier) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Button(
            onClick = { onEvent(ReceivingUiEvent.StartReceipt) },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text(KeswaTheme.strings.startDelivery) }
    }
}

@Composable
private fun ScanBar(state: ReceivingUiState, onEvent: (ReceivingUiEvent) -> Unit) {
    val focus = remember { FocusRequester() }

    LaunchedEffect(state.pendingItem, state.lines.size) {
        if (state.pendingItem == null) {
            runCatching { focus.requestFocus() }
        }
    }

    OutlinedTextField(
        value = state.scanEntry,
        onValueChange = { onEvent(ReceivingUiEvent.ScanEntryChanged(it)) },
        label = { Text(KeswaTheme.strings.scanToAddColourByColour) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = { onEvent(ReceivingUiEvent.Scanned(state.scanEntry)) },
        ),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
}

@Composable
private fun Lines(
    state: ReceivingUiState,
    onEvent: (ReceivingUiEvent) -> Unit,
    onPrintLineLabels: (ReceiptLineUiModel) -> Unit,
    modifier: Modifier,
) {
    if (state.lines.isEmpty()) {
        if (state.receipt == null) {
            EmptyState(
                title = KeswaTheme.strings.startADelivery,
                hint = KeswaTheme.strings.startADeliveryHint,
                modifier = modifier,
            )
        } else {
            EmptyState(
                title = KeswaTheme.strings.scanFirstCarton,
                hint = KeswaTheme.strings.scanFirstCartonHint,
                modifier = modifier,
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
                        localisedName(line.description, line.descriptionAr).ifBlank { line.sku },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val barcodeText = if (line.barcode != null) " · 🏷️ ${line.barcode}" else ""
                    val newPriceText = line.sellPrice?.let { newPrice ->
                        val arrow = line.livePrice?.let { "${it.format()} → " }.orEmpty()
                        " · $arrow${KeswaTheme.strings.newPriceOnPosting(newPrice.format())}"
                    }.orEmpty()
                    Text(
                        "${line.sku}$barcodeText · ${line.quantity} × ${line.unitCost.format()}$newPriceText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(line.lineTotal.format(), style = KeswaTheme.figure)
                if (line.barcode == null) {
                    TextButton(onClick = { onEvent(ReceivingUiEvent.GenerateBarcode(line.variantId)) }) {
                        Text(KeswaTheme.strings.generateBarcode)
                    }
                } else {
                    TextButton(onClick = { onPrintLineLabels(line) }) {
                        Text(KeswaTheme.strings.printLabels)
                    }
                }
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
                receipt.supplierName.ifBlank { KeswaTheme.strings.noSupplier },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(KeswaTheme.strings.piecesLabel, style = MaterialTheme.typography.bodyMedium)
            Text("${state.pieceCount}", style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(KeswaTheme.strings.cost, style = MaterialTheme.typography.titleMedium)
            Text(state.totalCost.format(), style = KeswaTheme.figureLarge)
        }

        if (state.isDraft) {
            Button(
                onClick = { onEvent(ReceivingUiEvent.Post) },
                enabled = state.canPost,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text(KeswaTheme.strings.postDelivery) }
            OutlinedButton(
                onClick = { onEvent(ReceivingUiEvent.Discard) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) { Text(KeswaTheme.strings.discard) }
        } else if (state.receipt != null) {
            OutlinedButton(
                onClick = { onEvent(ReceivingUiEvent.PrintTags) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text(KeswaTheme.strings.printHangTags) }
        }
    }
}

@Composable
private fun CostChanges(changes: List<CostChange>) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(KeswaTheme.strings.costMoved, style = MaterialTheme.typography.titleSmall)
            Text(
                KeswaTheme.strings.weightedAverageNote,
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
        Text(KeswaTheme.strings.recentDeliveries, style = MaterialTheme.typography.titleSmall)
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
                    if (receipt.isDraft) KeswaTheme.strings.draft else receipt.totalCost.format(),
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
        title = { Text(localisedName(item.description, item.descriptionAr)) },
        text = {
            Column {
                Text(item.sku, style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(
                    value = state.quantityEntry,
                    onValueChange = { onEvent(ReceivingUiEvent.QuantityChanged(it)) },
                    label = { Text(KeswaTheme.strings.quantity) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = state.costEntry,
                    onValueChange = { onEvent(ReceivingUiEvent.CostChanged(it)) },
                    label = { Text(KeswaTheme.strings.unitCost) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = state.priceEntry,
                    onValueChange = { onEvent(ReceivingUiEvent.PriceChanged(it)) },
                    label = { Text(KeswaTheme.strings.price) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onEvent(ReceivingUiEvent.ConfirmLine) }) { Text(KeswaTheme.strings.add) }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ReceivingUiEvent.CancelLine) }) { Text(KeswaTheme.strings.cancel) }
        },
    )
}

@Composable
private fun PrintLineLabelsDialog(
    line: ReceiptLineUiModel,
    copiesEntry: String,
    onCopiesChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(KeswaTheme.strings.printLabels) },
        text = {
            Column {
                Text(
                    localisedName(line.description, line.descriptionAr).ifBlank { line.sku },
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = copiesEntry,
                    onValueChange = onCopiesChanged,
                    label = { Text(KeswaTheme.strings.copies) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text(KeswaTheme.strings.print) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(KeswaTheme.strings.cancel) }
        },
    )
}

private val WIDE_BREAKPOINT = 900.dp
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
        descriptionAr = "تيشيرت رقبة دائرية — كحلي",
        quantity = 20,
        unitCost = Money.ofPounds(120),
        lineTotal = Money.ofPounds(2_400),
        sellPrice = Money.ofPounds(250),
        livePrice = Money.ofPounds(230),
        barcode = "6221000000018",
    ),
    ReceiptLineUiModel(
        lineId = "l2",
        variantId = "v2",
        sku = "KSW-TSH-022-WH",
        description = "Round-neck t-shirt — White",
        descriptionAr = "تيشيرت رقبة دائرية — أبيض",
        quantity = 30,
        unitCost = Money.ofPounds(118),
        lineTotal = Money.ofPounds(3_540),
        barcode = "6221000000025",
    ),
)

private val previewItemNavy = SellableItem(
    variantId = "v1",
    productId = "p1",
    sku = "KSW-TSH-022-NV",
    name = "Round-neck t-shirt",
    nameAr = "تيشيرت رقبة دائرية",
    colourName = "Navy",
    colourNameAr = "كحلي",
    cost = Money.ofPounds(120),
    price = Money.ofPounds(230),
    onHand = 12,
)

private val previewItemWhite = SellableItem(
    variantId = "v2",
    productId = "p1",
    sku = "KSW-TSH-022-WH",
    name = "Round-neck t-shirt",
    nameAr = "تيشيرت رقبة دائرية",
    colourName = "White",
    colourNameAr = "أبيض",
    cost = Money.ofPounds(118),
    price = null,
    onHand = 0,
)

@Preview
@Composable
private fun ReceivingTopLevelPickerPreview() {
    KeswaTheme {
        ReceivingContent(
            state = ReceivingUiState(
                receipt = previewReceipt,
                lines = previewLines,
                subCategories = listOf(
                    Category(
                        id = "c-men",
                        name = "Men",
                        nameAr = "رجالي",
                        parentId = null,
                        path = "/c-men/",
                        depth = 0,
                        sortOrder = 0,
                        isActive = true,
                    ),
                    Category(
                        id = "c-women",
                        name = "Women",
                        nameAr = "حريمي",
                        parentId = null,
                        path = "/c-women/",
                        depth = 0,
                        sortOrder = 1,
                        isActive = true,
                    ),
                ),
            ),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun ReceivingCategoryPickerWithTypedAndProblemPreview() {
    KeswaTheme {
        ReceivingContent(
            state = ReceivingUiState(
                receipt = previewReceipt,
                lines = previewLines,
                crumbs = listOf(
                    CategoryCrumb("c-men", "Men", "رجالي"),
                    CategoryCrumb("c-shirts", "Shirts", "قمصان"),
                ),
                levelItems = listOf(previewItemNavy, previewItemWhite),
                typed = mapOf(
                    "v1" to TypedEntry(previewItemNavy, "20", "120.00", "250.00"),
                    "v2" to TypedEntry(previewItemWhite, "15", "118.00", ""),
                ),
                problems = mapOf("v2" to EntryProblem.PRICE_REQUIRED),
            ),
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
