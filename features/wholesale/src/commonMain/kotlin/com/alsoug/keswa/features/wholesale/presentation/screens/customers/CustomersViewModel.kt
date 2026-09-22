package com.alsoug.keswa.features.wholesale.presentation.screens.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.domain.model.Customer
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.IReceivablesRepository
import com.alsoug.keswa.features.wholesale.domain.usecase.CreateCustomerUseCase
import com.alsoug.keswa.features.wholesale.domain.usecase.GetCustomerAccountUseCase
import com.alsoug.keswa.features.wholesale.domain.usecase.ListCustomersUseCase
import com.alsoug.keswa.features.wholesale.domain.usecase.SearchCustomersUseCase
import com.alsoug.keswa.features.wholesale.domain.usecase.TakePaymentOnAccountUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CustomersViewModel(
    private val listCustomers: ListCustomersUseCase,
    private val searchCustomers: SearchCustomersUseCase,
    private val account: GetCustomerAccountUseCase,
    private val createCustomer: CreateCustomerUseCase,
    private val takePayment: TakePaymentOnAccountUseCase,
    private val receivables: IReceivablesRepository,
    private val now: () -> Long,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(CustomersUiState())
    val state: StateFlow<CustomersUiState> = _state.asStateFlow()

    private val _navigation = MutableSharedFlow<CustomersNavigation>(replay = 0, extraBufferCapacity = 1)
    val navigation = _navigation.asSharedFlow()

    private val _effect = MutableSharedFlow<CustomersUiEffect>(replay = 0, extraBufferCapacity = 1)
    val effect = _effect.asSharedFlow()

    fun onEvent(event: CustomersUiEvent) {
        when (event) {
            CustomersUiEvent.Load -> load()
            is CustomersUiEvent.QueryChanged -> _state.update { it.copy(query = event.value) }
            CustomersUiEvent.Search -> search()
            is CustomersUiEvent.Select -> select(event.customerId)
            CustomersUiEvent.Deselect -> _state.update {
                it.copy(selected = null, entries = emptyList(), paymentEntry = "", paymentNote = "")
            }
            is CustomersUiEvent.PaymentChanged -> _state.update { it.copy(paymentEntry = event.value) }
            is CustomersUiEvent.PaymentNoteChanged -> _state.update { it.copy(paymentNote = event.value) }
            CustomersUiEvent.TakePayment -> pay()
            CustomersUiEvent.StartCreating -> _state.update { it.copy(isCreating = true) }
            CustomersUiEvent.CancelCreating -> _state.update { it.copy(isCreating = false) }
            is CustomersUiEvent.NewNameChanged -> _state.update { it.copy(newName = event.value) }
            is CustomersUiEvent.NewPhoneChanged -> _state.update { it.copy(newPhone = event.value) }
            is CustomersUiEvent.NewLimitChanged -> _state.update { it.copy(newLimit = event.value) }
            is CustomersUiEvent.NewTermsChanged -> _state.update { it.copy(newTerms = event.value) }
            CustomersUiEvent.Create -> create()
        }
    }

    private fun load() {
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isLoading = true) }
            listCustomers().fold(
                onSuccess = { customers ->
                    val rows = customers.map { row(it) }
                    _state.update { it.copy(isLoading = false, customers = rows) }
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun search() {
        val term = _state.value.query
        viewModelScope.launch(dispatchers.io) {
            val result = if (term.isBlank()) listCustomers() else searchCustomers(term)
            result.fold(
                onSuccess = { customers ->
                    val rows = customers.map { row(it) }
                    _state.update { it.copy(customers = rows) }
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun select(customerId: String) {
        viewModelScope.launch(dispatchers.io) {
            account(customerId).fold(
                onSuccess = { found ->
                    if (found == null) return@fold
                    // Read before updating: `update` re-runs its block on contention.
                    val entries = receivables
                        .statement(customerId, 0, Long.MAX_VALUE)
                        .getOrNull()
                        ?.entries
                        .orEmpty()
                    _state.update {
                        it.copy(
                            selected = found.customer,
                            balance = found.balance,
                            available = found.available,
                            ageing = found.ageing,
                            entries = entries.asReversed(),
                        )
                    }
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun pay() {
        val customer = _state.value.selected ?: return
        val amount = Money.parse(_state.value.paymentEntry) ?: return reject("That is not an amount")

        viewModelScope.launch(dispatchers.io) {
            takePayment(customer.id, amount, _state.value.paymentNote).fold(
                onSuccess = {
                    _state.update { it.copy(paymentEntry = "", paymentNote = "") }
                    _effect.emit(CustomersUiEffect.ShowMessage("Received ${amount.format()}"))
                    select(customer.id)
                    load()
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun create() {
        val current = _state.value
        val limit = Money.parse(current.newLimit.ifBlank { "0" })
            ?: return reject("That is not an amount")
        val terms = current.newTerms.toIntOrNull() ?: return reject("That is not a number of days")

        viewModelScope.launch(dispatchers.io) {
            createCustomer(
                name = current.newName,
                nameAr = current.newName,
                phone = current.newPhone.ifBlank { null },
                creditLimit = limit,
                paymentTermsDays = terms,
            ).fold(
                onSuccess = {
                    _state.update {
                        it.copy(isCreating = false, newName = "", newPhone = "", newLimit = "")
                    }
                    load()
                },
                onFailure = { fail(it) },
            )
        }
    }

    private suspend fun row(customer: Customer): CustomerRowUiModel {
        val balance = receivables.balance(customer.id).getOrElse { Money.ZERO }
        val ageing = receivables.ageing(customer.id, now()).getOrElse { com.alsoug.keswa.core.domain.model.Ageing.NOTHING }
        return CustomerRowUiModel(
            customer = customer,
            balance = balance,
            available = customer.availableCredit(balance),
            isOverdue = !ageing.overdue.isZero,
        )
    }

    private fun reject(message: String) {
        viewModelScope.launch { _effect.emit(CustomersUiEffect.ShowError(message)) }
    }

    private suspend fun fail(cause: Throwable) {
        _state.update { it.copy(isLoading = false) }
        _effect.emit(CustomersUiEffect.ShowError(cause.message ?: "Something went wrong"))
    }
}
