package com.alsoug.keswa.core.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.alsoug.keswa.core.domain.model.DocumentStatus

/**
 * A delivery being unpacked, and then the record that it arrived.
 *
 * `DRAFT` moves no stock. Unpacking boxes takes twenty minutes and a phone call, and an app that
 * half-received a delivery because somebody walked away mid-carton is worse than one that received
 * nothing. One `POST` writes every movement and every cost change together.
 */
@Entity(
    tableName = "stock_receipt",
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("locationId"), Index("createdAt"), Index("status")],
)
data class StockReceiptEntity(
    @PrimaryKey val id: String,
    val reference: String,
    val supplierName: String,
    val locationId: String,
    val status: DocumentStatus,
    val note: String?,
    val createdAt: Long,
    val createdByUserId: String,
    val postedAt: Long?,
    val postedByUserId: String?,
    val totalCostPiastres: Long,
)

/**
 * One variant on a delivery, at what it cost.
 *
 * `onDelete = CASCADE` from the receipt: a draft that is abandoned should take its lines with it.
 * A *posted* receipt is never deleted — its movements reference it — so the cascade only ever
 * fires on drafts.
 */
@Entity(
    tableName = "stock_receipt_line",
    foreignKeys = [
        ForeignKey(
            entity = StockReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["receiptId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = VariantEntity::class,
            parentColumns = ["id"],
            childColumns = ["variantId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("receiptId"), Index("variantId")],
)
data class StockReceiptLineEntity(
    @PrimaryKey val id: String,
    val receiptId: String,
    val lineNumber: Int,
    val variantId: String,
    val quantity: Int,
    val unitCostPiastres: Long,
    val lineTotalPiastres: Long,
)

/**
 * A stock count in progress.
 *
 * Blind by construction — see [StockCountLineEntity.expectedQuantity].
 */
@Entity(
    tableName = "stock_count",
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("locationId"), Index("startedAt"), Index("status")],
)
data class StockCountEntity(
    @PrimaryKey val id: String,
    val locationId: String,
    val status: DocumentStatus,
    val note: String?,
    val startedAt: Long,
    val startedByUserId: String,
    val postedAt: Long?,
    val postedByUserId: String?,
)

/**
 * One counted variant.
 *
 * **[expectedQuantity] is null until the count is posted, and that is the entire feature.** A
 * counter who can see that the system expects twelve will count until they get twelve — not
 * dishonestly, but because the eye finds what it is told to look for — and the discrepancy that
 * would have told the owner something disappears. There is nothing here for a screen to leak.
 */
@Entity(
    tableName = "stock_count_line",
    foreignKeys = [
        ForeignKey(
            entity = StockCountEntity::class,
            parentColumns = ["id"],
            childColumns = ["countId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = VariantEntity::class,
            parentColumns = ["id"],
            childColumns = ["variantId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("countId"), Index("variantId"), Index(value = ["countId", "variantId"], unique = true)],
)
data class StockCountLineEntity(
    @PrimaryKey val id: String,
    val countId: String,
    val lineNumber: Int,
    val variantId: String,
    val countedQuantity: Int,
    val expectedQuantity: Int?,
    val varianceQuantity: Int?,
)
