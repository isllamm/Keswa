package com.alsoug.keswa.features.wholesale.presentation.screens.customers

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alsoug.keswa.core.designsystem.EmptyState
import com.alsoug.keswa.core.designsystem.KeswaTheme
import com.alsoug.keswa.core.designsystem.ScreenHeader
import com.alsoug.keswa.core.designsystem.resolve
import com.alsoug.keswa.core.domain.model.Ageing
import com.alsoug.keswa.core.domain.model.Customer
import com.alsoug.keswa.core.domain.model.LedgerEntry
import com.alsoug.keswa.core.domain.model.LedgerEntryType
import com.alsoug.keswa.core.domain.money.Money
import org.jetbrains.compose.ui.tooling.preview.Preview

/** ADR-012 — a humble view: renders state, forwards events, owns no business logic. */
@Composable
fun CustomersScreen(
    viewModel: CustomersViewModel,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val strings = KeswaTheme.strings

    LaunchedEffect(Unit) { viewModel.onEvent(CustomersUiEvent.Load) }

    LaunchedEffect(Unit) {
        viewModel.navigation.collect { navigation ->
            when (navigation) {
                CustomersNavigation.Back -> onBack()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is CustomersUiEffect.ShowError -> onMessage(effect.message.resolve(strings))
                is CustomersUiEffect.ShowMessage -> onMessage(effect.message.resolve(strings))
            }
        }
    }

    CustomersContent(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
internal fun CustomersContent(
    state: CustomersUiState,
    onEvent: (CustomersUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        ScreenHeader(
            title = KeswaTheme.strings.customers,
            subtitle = KeswaTheme.strings.customersSubtitle,
        )

        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = { onEvent(CustomersUiEvent.QueryChanged(it)) },
                        label = { Text(KeswaTheme.strings.nameOrPhone) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onEvent(CustomersUiEvent.Search) }),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onEvent(CustomersUiEvent.StartCreating) }) { Text(KeswaTheme.strings.newShort) }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                CustomerList(state, onEvent, modifier = Modifier.weight(1f))
            }

            VerticalDivider()

            Column(modifier = Modifier.width(360.dp).padding(12.dp)) {
                if (state.selected == null) {
                    EmptyState(
                        title = KeswaTheme.strings.pickACustomer,
                        hint = KeswaTheme.strings.pickACustomerHint,
                    )
                } else {
                    Account(state, onEvent)
                }
            }
        }
    }

    if (state.isCreating) NewCustomerDialog(state, onEvent)
}

@Composable
private fun CustomerList(
    state: CustomersUiState,
    onEvent: (CustomersUiEvent) -> Unit,
    modifier: Modifier,
) {
    if (state.customers.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                KeswaTheme.strings.noCustomersYet,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(state.customers, key = { it.customer.id }) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEvent(CustomersUiEvent.Select(row.customer.id)) }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.customer.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (row.customer.sellsOnAccount) {
                            "limit ${row.customer.creditLimit.format()} · net ${row.customer.paymentTermsDays}"
                        } else {
                            KeswaTheme.strings.cashOnlyShort
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    row.balance.format(),
                    style = MaterialTheme.typography.bodyMedium,
                    // Overdue is the only thing on this list worth colouring.
                    color = if (row.isOverdue) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun ColumnScope.Account(state: CustomersUiState, onEvent: (CustomersUiEvent) -> Unit) {
    val customer = state.selected ?: return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(customer.name, style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = { onEvent(CustomersUiEvent.Deselect) }) { Text("✕") }
    }

    Text(state.balance.format(), style = KeswaTheme.figureLarge)
    Text(
        if (state.isCashOnly) {
            KeswaTheme.strings.cashOnlyNoLimit
        } else {
            "${state.available.format()} of ${customer.creditLimit.format()} still available"
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    AgeingCard(state.ageing)

    Text(
        KeswaTheme.strings.takeAPayment,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 16.dp),
    )
    Text(
        KeswaTheme.strings.againstAccountNote,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = state.paymentEntry,
        onValueChange = { onEvent(CustomersUiEvent.PaymentChanged(it)) },
        label = { Text(KeswaTheme.strings.amount) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = state.paymentNote,
        onValueChange = { onEvent(CustomersUiEvent.PaymentNoteChanged(it)) },
        label = { Text(KeswaTheme.strings.noteOptional) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    Button(
        onClick = { onEvent(CustomersUiEvent.TakePayment) },
        enabled = state.canTakePayment,
        modifier = Modifier.padding(top = 8.dp),
    ) { Text(KeswaTheme.strings.receivePayment) }

    Text(
        KeswaTheme.strings.accountHistory,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 16.dp),
    )
    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp)) {
        items(state.entries, key = { it.id }) { entry ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entry.type.name.lowercase().replace('_', ' ')
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    entry.note?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(entry.amount.format(), style = KeswaTheme.figure)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun AgeingCard(ageing: Ageing) {
    if (ageing.total.isZero) return

    Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(KeswaTheme.strings.howOverdue, style = MaterialTheme.typography.titleSmall)
            AgeingRow(KeswaTheme.strings.notYetDue, ageing.current)
            AgeingRow("1–30 days", ageing.thirtyDays)
            AgeingRow("31–60 days", ageing.sixtyDays)
            AgeingRow(KeswaTheme.strings.overSixtyDays, ageing.ninetyDaysPlus)
        }
    }
}

@Composable
private fun AgeingRow(label: String, amount: Money) {
    if (amount.isZero) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(amount.format(), style = KeswaTheme.figure)
    }
}

@Composable
private fun NewCustomerDialog(state: CustomersUiState, onEvent: (CustomersUiEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(CustomersUiEvent.CancelCreating) },
        title = { Text(KeswaTheme.strings.newCustomer) },
        text = {
            Column {
                OutlinedTextField(
                    value = state.newName,
                    onValueChange = { onEvent(CustomersUiEvent.NewNameChanged(it)) },
                    label = { Text(KeswaTheme.strings.name) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.newPhone,
                    onValueChange = { onEvent(CustomersUiEvent.NewPhoneChanged(it)) },
                    label = { Text(KeswaTheme.strings.phone) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = state.newLimit,
                    onValueChange = { onEvent(CustomersUiEvent.NewLimitChanged(it)) },
                    // Blank means zero means cash only. Trust should be granted deliberately.
                    label = { Text(KeswaTheme.strings.creditLimitBlankForCash) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = state.newTerms,
                    onValueChange = { onEvent(CustomersUiEvent.NewTermsChanged(it)) },
                    label = { Text(KeswaTheme.strings.paymentTermsDays) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onEvent(CustomersUiEvent.Create) },
                enabled = state.newName.isNotBlank(),
            ) { Text(KeswaTheme.strings.create) }
        },
        dismissButton = {
            OutlinedButton(onClick = { onEvent(CustomersUiEvent.CancelCreating) }) { Text(KeswaTheme.strings.cancel) }
        },
    )
}

private val previewCustomer = Customer(
    id = "cus-1",
    name = "Nasr Textiles",
    nameAr = "نصر للنسيج",
    phone = "0100 000 0000",
    taxId = null,
    priceListId = "pricelist-trade",
    creditLimit = Money.ofPounds(50_000),
    paymentTermsDays = 30,
    isActive = true,
)

private val previewRows = listOf(
    CustomerRowUiModel(previewCustomer, Money.ofPounds(14_200), Money.ofPounds(35_800), isOverdue = true),
    CustomerRowUiModel(
        previewCustomer.copy(id = "cus-2", name = "Downtown Boutique", creditLimit = Money.ZERO),
        Money.ZERO,
        Money.ZERO,
        isOverdue = false,
    ),
)

private val previewEntries = listOf(
    LedgerEntry("e1", "cus-1", LedgerEntryType.INVOICE, Money.ofPounds(18_400), "INVOICE", "s1", 0, 0, "u1", null),
    LedgerEntry("e2", "cus-1", LedgerEntryType.PAYMENT, Money.ofPounds(-4_200), null, null, 1, null, "u1", "cash on account"),
)

@Preview
@Composable
private fun CustomersSuccessPreview() {
    KeswaTheme {
        CustomersContent(
            state = CustomersUiState(
                customers = previewRows,
                selected = previewCustomer,
                balance = Money.ofPounds(14_200),
                available = Money.ofPounds(35_800),
                ageing = Ageing(
                    current = Money.ofPounds(8_000),
                    thirtyDays = Money.ofPounds(6_200),
                    sixtyDays = Money.ZERO,
                    ninetyDaysPlus = Money.ZERO,
                ),
                entries = previewEntries,
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun CustomersEmptyPreview() {
    KeswaTheme { CustomersContent(state = CustomersUiState(), onEvent = {}) }
}

@Preview
@Composable
private fun CustomersLoadingPreview() {
    KeswaTheme {
        CustomersContent(state = CustomersUiState(isLoading = true), onEvent = {})
    }
}
