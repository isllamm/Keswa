package com.alsoug.keswa.core.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.alsoug.keswa.core.domain.model.ReturnCondition
import com.alsoug.keswa.core.domain.model.SaleStatus
import com.alsoug.keswa.core.domain.model.TenderMethod

/**
 * Goods coming back, as its own document.
 *
 * Not a negative `sale`, though that would have made net revenue fall out of one `SUM`. A return
 * carries fields a sale has no use for — condition per line, the line it reverses, who authorised
 * it going through outside the policy — and the original sale has to keep its own figures intact.
 * Someone who bought three and returned one still bought three, and a receipt reprinted next year
 * must still say so.
 *
 * [originalSaleId] is null for a no-receipt return, which is the case the pricing rule in
 * `LowestSoldPriceUseCase` exists for.
 *
 * [exchangeSaleId] links to the replacement purchase. An exchange is a return *and* a sale, not a
 * third kind of document — and keeping the link is what lets analytics tell a swap from a walk-out
 * refund, which are very different events for a shop.
 */
@Entity(
    tableName = "sale_return",
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["id"],
            childColumns = ["originalSaleId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["returnNumber"], unique = true),
        Index("originalSaleId"),
        Index("occurredAt"),
        Index("locationId"),
        Index("shiftId"),
    ],
)
data class SaleReturnEntity(
    @PrimaryKey val id: String,
    val returnNumber: Long,
    val originalSaleId: String?,
    val locationId: String,
    val userId: String,
    val shiftId: String?,
    val status: SaleStatus,
    val reason: String,
    val refundMethod: TenderMethod,
    val refundAmountPiastres: Long,
    val subtotalPiastres: Long,
    val taxPiastres: Long,
    val occurredAt: Long,
    val exchangeSaleId: String?,
    /** Set when the return needed `REFUND_ANY` — outside the window, or with no receipt. */
    val authorisedByUserId: String?,
    val voidedAt: Long?,
    val voidedByUserId: String?,
    val voidReason: String?,
)

/**
 * One garment coming back.
 *
 * [unitCostPiastres] is copied from the sale line it reverses, not read fresh: the garment returns
 * at the cost it left at, so the moving average does not move because somebody changed their mind.
 */
@Entity(
    tableName = "sale_return_line",
    foreignKeys = [
        ForeignKey(
            entity = SaleReturnEntity::class,
            parentColumns = ["id"],
            childColumns = ["returnId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = VariantEntity::class,
            parentColumns = ["id"],
            childColumns = ["variantId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("returnId"), Index("variantId"), Index("saleLineId")],
)
data class SaleReturnLineEntity(
    @PrimaryKey val id: String,
    val returnId: String,
    val lineNumber: Int,
    /** Null for a no-receipt return — there is no line to reverse. */
    val saleLineId: String?,
    val variantId: String,
    val description: String,
    val quantity: Int,
    val unitRefundPiastres: Long,
    val lineRefundPiastres: Long,
    val condition: ReturnCondition,
    val unitCostPiastres: Long,
)
