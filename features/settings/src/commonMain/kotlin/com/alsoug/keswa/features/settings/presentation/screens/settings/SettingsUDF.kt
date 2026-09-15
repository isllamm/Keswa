package com.alsoug.keswa.features.settings.presentation.screens.settings

import com.alsoug.keswa.core.domain.model.ShopSettings

data class SettingsUiState(
    val isLoading: Boolean = false,
    val isBusy: Boolean = false,
    val settings: ShopSettings = ShopSettings(),
) {
    val canTestReceipt: Boolean get() = settings.hasReceiptPrinter && !isBusy
    val canTestLabel: Boolean get() = settings.hasLabelPrinter && !isBusy
}

sealed interface SettingsUiEvent {
    data object Load : SettingsUiEvent
    data class Edit(val settings: ShopSettings) : SettingsUiEvent
    data object Save : SettingsUiEvent
    data object TestReceipt : SettingsUiEvent
    data object TestLabel : SettingsUiEvent
    data object Back : SettingsUiEvent
}

sealed interface SettingsNavigation {
    data object Back : SettingsNavigation
}

sealed interface SettingsUiEffect {
    data class ShowMessage(val message: String) : SettingsUiEffect
    data class ShowError(val message: String) : SettingsUiEffect
}
