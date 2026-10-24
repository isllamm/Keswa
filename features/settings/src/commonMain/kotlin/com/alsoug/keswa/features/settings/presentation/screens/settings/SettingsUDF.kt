package com.alsoug.keswa.features.settings.presentation.screens.settings

import com.alsoug.keswa.core.designsystem.Message
import com.alsoug.keswa.core.domain.model.ShopSettings
import com.alsoug.keswa.core.domain.model.SyncDeviceStatus

data class SettingsUiState(
    val isLoading: Boolean = false,
    val isBusy: Boolean = false,
    val isSyncing: Boolean = false,
    val settings: ShopSettings = ShopSettings(),
    val syncStatus: SyncDeviceStatus? = null,
    val syncServerUrlInput: String = "",
    val syncDeviceNameInput: String = "",
    val syncCodeInput: String = "",
) {
    val canTestReceipt: Boolean get() = settings.hasReceiptPrinter && !isBusy
    val canTestLabel: Boolean get() = settings.hasLabelPrinter && !isBusy
    val canEnrol: Boolean get() = syncServerUrlInput.isNotBlank() && syncCodeInput.isNotBlank() && !isSyncing
    val canSyncNow: Boolean get() = syncStatus?.isEnrolled == true && !isSyncing
}

sealed interface SettingsUiEvent {
    data object Load : SettingsUiEvent
    data class Edit(val settings: ShopSettings) : SettingsUiEvent
    data object Save : SettingsUiEvent
    data object TestReceipt : SettingsUiEvent
    data object TestLabel : SettingsUiEvent
    data class EditSyncServerUrl(val url: String) : SettingsUiEvent
    data class EditSyncDeviceName(val name: String) : SettingsUiEvent
    data class EditSyncCode(val code: String) : SettingsUiEvent
    data object EnrolDevice : SettingsUiEvent
    data object SyncNow : SettingsUiEvent
    data object Back : SettingsUiEvent
}

sealed interface SettingsNavigation {
    data object Back : SettingsNavigation
}

sealed interface SettingsUiEffect {
    data class ShowMessage(val message: Message) : SettingsUiEffect
    data class ShowError(val message: Message) : SettingsUiEffect
}
