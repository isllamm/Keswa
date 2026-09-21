package com.alsoug.keswa.core.data.repository

import com.alsoug.keswa.core.coroutines.runCatchingCancellable
import com.alsoug.keswa.core.database.dao.SettingDao
import com.alsoug.keswa.core.database.entities.AppSettingEntity
import com.alsoug.keswa.core.domain.model.ShopSettings
import com.alsoug.keswa.core.domain.repository.ISettingsRepository

/**
 * Reads and writes [ShopSettings] over the generic key/value table.
 *
 * Every read falls back to the default rather than failing, so a missing or corrupted row leaves
 * the till usable — a mistyped port should not stop someone selling.
 */
class SettingsRepositoryImpl(
    private val dao: SettingDao,
) : ISettingsRepository {

    override suspend fun get(): Result<ShopSettings> = runCatchingCancellable {
        val stored = dao.getAll().associate { it.key to it.value }
        val defaults = ShopSettings()
        ShopSettings(
            shopName = stored[SHOP_NAME] ?: defaults.shopName,
            shopNameAr = stored[SHOP_NAME_AR] ?: defaults.shopNameAr,
            addressLine = stored[SHOP_ADDRESS] ?: defaults.addressLine,
            vatBasisPoints = stored[VAT_BASIS_POINTS]?.toIntOrNull() ?: defaults.vatBasisPoints,
            returnWindowDays = stored[RETURN_WINDOW]?.toIntOrNull() ?: defaults.returnWindowDays,
            allowNoReceiptReturns = stored[NO_RECEIPT_RETURNS]?.toBooleanStrictOrNull()
                ?: defaults.allowNoReceiptReturns,
            receiptHost = stored[RECEIPT_HOST] ?: defaults.receiptHost,
            receiptPort = stored[RECEIPT_PORT]?.toIntOrNull() ?: defaults.receiptPort,
            paperWidthDots = stored[PAPER_WIDTH]?.toIntOrNull() ?: defaults.paperWidthDots,
            labelHost = stored[LABEL_HOST] ?: defaults.labelHost,
            labelPort = stored[LABEL_PORT]?.toIntOrNull() ?: defaults.labelPort,
            labelWidthMm = stored[LABEL_WIDTH]?.toIntOrNull() ?: defaults.labelWidthMm,
            labelHeightMm = stored[LABEL_HEIGHT]?.toIntOrNull() ?: defaults.labelHeightMm,
            labelGapMm = stored[LABEL_GAP]?.toIntOrNull() ?: defaults.labelGapMm,
            scanMaxGapMillis = stored[SCAN_GAP]?.toLongOrNull() ?: defaults.scanMaxGapMillis,
        )
    }

    override suspend fun save(settings: ShopSettings): Result<Unit> = runCatchingCancellable {
        dao.putAll(
            listOf(
                AppSettingEntity(SHOP_NAME, settings.shopName),
                AppSettingEntity(SHOP_NAME_AR, settings.shopNameAr),
                AppSettingEntity(SHOP_ADDRESS, settings.addressLine),
                AppSettingEntity(VAT_BASIS_POINTS, settings.vatBasisPoints.toString()),
                AppSettingEntity(RETURN_WINDOW, settings.returnWindowDays.toString()),
                AppSettingEntity(NO_RECEIPT_RETURNS, settings.allowNoReceiptReturns.toString()),
                AppSettingEntity(RECEIPT_HOST, settings.receiptHost),
                AppSettingEntity(RECEIPT_PORT, settings.receiptPort.toString()),
                AppSettingEntity(PAPER_WIDTH, settings.paperWidthDots.toString()),
                AppSettingEntity(LABEL_HOST, settings.labelHost),
                AppSettingEntity(LABEL_PORT, settings.labelPort.toString()),
                AppSettingEntity(LABEL_WIDTH, settings.labelWidthMm.toString()),
                AppSettingEntity(LABEL_HEIGHT, settings.labelHeightMm.toString()),
                AppSettingEntity(LABEL_GAP, settings.labelGapMm.toString()),
                AppSettingEntity(SCAN_GAP, settings.scanMaxGapMillis.toString()),
            ),
        )
    }

    private companion object {
        const val SHOP_NAME = "shop.name"
        const val SHOP_NAME_AR = "shop.nameAr"
        const val SHOP_ADDRESS = "shop.address"
        const val VAT_BASIS_POINTS = "shop.vatBasisPoints"
        const val RETURN_WINDOW = "shop.returnWindowDays"
        const val NO_RECEIPT_RETURNS = "shop.allowNoReceiptReturns"
        const val RECEIPT_HOST = "printer.receipt.host"
        const val RECEIPT_PORT = "printer.receipt.port"
        const val PAPER_WIDTH = "printer.receipt.widthDots"
        const val LABEL_HOST = "printer.label.host"
        const val LABEL_PORT = "printer.label.port"
        const val LABEL_WIDTH = "printer.label.widthMm"
        const val LABEL_HEIGHT = "printer.label.heightMm"
        const val LABEL_GAP = "printer.label.gapMm"
        const val SCAN_GAP = "scanner.maxGapMillis"
    }
}
