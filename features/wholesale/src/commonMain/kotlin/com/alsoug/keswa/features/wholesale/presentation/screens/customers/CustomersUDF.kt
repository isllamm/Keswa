package com.alsoug.keswa.features.wholesale.presentation.screens.customers

import com.alsoug.keswa.core.designsystem.Message
import com.alsoug.keswa.core.domain.model.Ageing
import com.alsoug.keswa.core.domain.model.Customer
import com.alsoug.keswa.core.domain.model.LedgerEntry
import com.alsoug.keswa.core.domain.money.Money

/** A customer row, with what they owe. The balance is a sum; there is no stored figure. */
data class CustomerRowUiModel(
    val customer: Customer,
    val balance: Money,
    val available: Money,
    val isOverdue: Boolean,
)

data class CustomersUiState(
    val isLoading: Boolean = false,
    val customers: List<CustomerRowUiModel> = emptyList(),
    val query: String = "",
    val selected: Customer? = null,
    val balance: Money = Money.ZERO,
    val available: Money = Money.ZERO,
    val ageing: Ageing = Ageing.NOTHING,
    val entries: List<LedgerEntry> = emptyList(),
    val paymentEntry: String = "",
    val paymentNote: String = "",
    val isCreating: Boolean = false,
    val newName: String = "",
    val newPhone: String = "",
    val newLimit: String = "",
    val newTerms: String = "30",
) {
    val hasSelection: Boolean get() = selected != null

    val canTakePayment: Boolean get() = hasSelection && paymentEntry.isNotBlank()

    /** Zero means cash only — the default, because trust should be granted deliberately. */
    val isCashOnly: Boolean get() = selected?.sellsOnAccount == false
}

sealed interface CustomersUiEvent {
    data object Load : CustomersUiEvent
    data class QueryChanged(val value: String) : CustomersUiEvent
    data object Search : CustomersUiEvent
    data class Select(val customerId: String) : CustomersUiEvent
    data object Deselect : CustomersUiEvent
    data class PaymentChanged(val value: String) : CustomersUiEvent
    data class PaymentNoteChanged(val value: String) : CustomersUiEvent
    data object TakePayment : CustomersUiEvent
    data object StartCreating : CustomersUiEvent
    data object CancelCreating : CustomersUiEvent
    data class NewNameChanged(val value: String) : CustomersUiEvent
    data class NewPhoneChanged(val value: String) : CustomersUiEvent
    data class NewLimitChanged(val value: String) : CustomersUiEvent
    data class NewTermsChanged(val value: String) : CustomersUiEvent
    data object Create : CustomersUiEvent
}

sealed interface CustomersNavigation {
    data object Back : CustomersNavigation
}

sealed interface CustomersUiEffect {
    data class ShowError(val message: Message) : CustomersUiEffect
    data class ShowMessage(val message: Message) : CustomersUiEffect
}
