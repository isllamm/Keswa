package com.alsoug.keswa.features.inventory.domain.model

import com.alsoug.keswa.core.designsystem.Strings
import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.error.ErrorBase

/**
 * Domain errors for inventory operations (ADR-032).
 *
 * Each error maps to an exact localized string representation so UI screens can
 * display clear, actionable feedback rather than generic failure messages.
 */
sealed class InventoryError(
    override val message: String,
    override val cause: Throwable? = null,
    open val code: String? = null,
) : ErrorBase(message, cause) {

    abstract fun resolveMessage(strings: Strings): String

    data class PermissionDenied(val permission: Permission) : InventoryError(
        message = "Missing required permission: ${permission.name}",
        code = "PERMISSION_DENIED",
    ) {
        override fun resolveMessage(strings: Strings): String = strings.permissionNeeded(permission)
    }

    data class ItemNotFound(val term: String) : InventoryError(
        message = "Item not found in catalogue: $term",
        code = "ITEM_NOT_FOUND",
    ) {
        override fun resolveMessage(strings: Strings): String = strings.notInCatalogue(term)
    }

    data class InvalidQuantity(val entry: String) : InventoryError(
        message = "Invalid quantity: $entry",
        code = "INVALID_QUANTITY",
    ) {
        override fun resolveMessage(strings: Strings): String = strings.notAQuantity
    }

    data class InvalidAmount(val entry: String) : InventoryError(
        message = "Invalid monetary amount: $entry",
        code = "INVALID_AMOUNT",
    ) {
        override fun resolveMessage(strings: Strings): String = strings.notAnAmount
    }

    data class ReceiptNotOpen(val receiptId: String) : InventoryError(
        message = "Receipt $receiptId is not in draft status",
        code = "RECEIPT_NOT_OPEN",
    ) {
        override fun resolveMessage(strings: Strings): String = strings.somethingWentWrong
    }

    data class CountNotOpen(val countId: String) : InventoryError(
        message = "Stock count $countId is not open",
        code = "COUNT_NOT_OPEN",
    ) {
        override fun resolveMessage(strings: Strings): String = strings.somethingWentWrong
    }

    data class NoLocation(val reason: String = "No default stock location configured") : InventoryError(
        message = reason,
        code = "NO_LOCATION",
    ) {
        override fun resolveMessage(strings: Strings): String = strings.somethingWentWrong
    }

    data object NoLabelPrinter : InventoryError(
        message = "No label printer configured in shop settings",
        code = "NO_LABEL_PRINTER",
    ) {
        override fun resolveMessage(strings: Strings): String = strings.noLabelPrinterConfigured
    }

    data class PrinterUnreachable(val detail: String) : InventoryError(
        message = "Label printer unreachable: $detail",
        code = "PRINTER_UNREACHABLE",
    ) {
        override fun resolveMessage(strings: Strings): String = strings.labelPrinterSilent
    }

    data class Unknown(val error: Throwable) : InventoryError(
        message = error.message ?: "Unknown inventory error",
        cause = error,
        code = "UNKNOWN",
    ) {
        override fun resolveMessage(strings: Strings): String = strings.somethingWentWrong
    }
}
