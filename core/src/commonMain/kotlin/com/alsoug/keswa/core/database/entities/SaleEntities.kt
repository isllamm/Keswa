package com.alsoug.keswa.core.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.alsoug.keswa.core.domain.model.SaleStatus
import com.alsoug.keswa.core.domain.model.TenderMethod

/**
 * The header of a completed transaction. **Never deleted, never re-priced.**
 *
 * A mistake becomes a void: the status changes and compensating stock movements are written, so
 * the shop's takings carry the same audit guarantee as its stock.
 *
 * [id] is a client-generated UUID and is the identity — the key a later sync upserts on
 * (`cashi_pax` defect F2). [receiptNumber] is the human-facing sequence, unique so that two tills
 * sharing one database in Phase 9 collide loudly rather than quietly issuing the same number twice.
 */
@Entity(
    tableName = "sale",
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["receiptNumber"], unique = true),
        Index("occurredAt"),
        Index("shiftId"),
        Index("userId"),
        Index("locationId"),
    ],
)
data class SaleEntity(
    @PrimaryKey val id: String,
    val receiptNumber: Long,
    val locationId: String,
    val priceListId: String,
    val userId: String,
    val shiftId: String?,
    val status: SaleStatus,
    val subtotalPiastres: Long,
    val discountPiastres: Long,
    val taxPiastres: Long,
    val totalPiastres: Long,
    val tenderedPiastres: Long,
    val changePiastres: Long,
    val occurredAt: Long,
    val voidedAt: Long?,
    val voidedByUserId: String?,
    val voidReason: String?,
)

/**
 * One line of a sale, with everything needed to reprint or explain it years later.
 *
 * [description] is denormalised on purpose: a receipt reprinted after the product was renamed must
 * say what the customer was actually sold.
 *
 * [unitCostPiastres] is a snapshot, so margin reporting does not move when a supplier's price does.
 * *Which* cost — moving average or FIFO — is Phase 6's decision; snapshotting now is what keeps it
 * available.
 */
@Entity(
    tableName = "sale_line",
    foreignKeys = [
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["id"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = VariantEntity::class,
            parentColumns = ["id"],
            childColumns = ["variantId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("saleId"), Index("variantId")],
)
data class SaleLineEntity(
    @PrimaryKey val id: String,
    val saleId: String,
    val lineNumber: Int,
    val variantId: String,
    val description: String,
    val descriptionAr: String,
    val quantity: Int,
    val unitPricePiastres: Long,
    val lineDiscountPiastres: Long,
    val orderDiscountPiastres: Long,
    val lineTotalPiastres: Long,
    val taxPiastres: Long,
    val unitCostPiastres: Long,
    val authorisedByUserId: String?,
)

/**
 * One tender against a sale.
 *
 * Its own table rather than two columns on the header, from day one: split payment is an ordinary
 * Tuesday, and retrofitting it touches every payment path in the app.
 */
@Entity(
    tableName = "payment",
    foreignKeys = [
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["id"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("saleId")],
)
data class PaymentEntity(
    @PrimaryKey val id: String,
    val saleId: String,
    val method: TenderMethod,
    val amountPiastres: Long,
    val tenderedPiastres: Long,
    val reference: String?,
    val occurredAt: Long,
)

/**
 * One person's stint at the till.
 *
 * [expectedCashPiastres] is written at close rather than derived when a report is opened, so a
 * Z-report reads the same next month as it did on the night.
 */
@Entity(
    tableName = "shift",
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("locationId"), Index("openedByUserId"), Index("openedAt")],
)
data class ShiftEntity(
    @PrimaryKey val id: String,
    val locationId: String,
    val openedByUserId: String,
    val openedAt: Long,
    val openingFloatPiastres: Long,
    val closedAt: Long?,
    val closedByUserId: String?,
    val countedCashPiastres: Long?,
    val expectedCashPiastres: Long?,
    val note: String?,
)

/**
 * A parked cart, and the one thing in this schema that is genuinely local to a till.
 *
 * `onDelete = CASCADE` here, alone in the schema: unlike a sale, a held cart is meant to be thrown
 * away, and its lines have no meaning without it.
 */
@Entity(
    tableName = "held_sale",
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("locationId"), Index("heldAt")],
)
data class HeldSaleEntity(
    @PrimaryKey val id: String,
    val label: String,
    val locationId: String,
    val userId: String,
    val heldAt: Long,
)

@Entity(
    tableName = "held_sale_line",
    foreignKeys = [
        ForeignKey(
            entity = HeldSaleEntity::class,
            parentColumns = ["id"],
            childColumns = ["heldSaleId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = VariantEntity::class,
            parentColumns = ["id"],
            childColumns = ["variantId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("heldSaleId"), Index("variantId")],
)
data class HeldSaleLineEntity(
    @PrimaryKey val id: String,
    val heldSaleId: String,
    val lineNumber: Int,
    val variantId: String,
    val quantity: Int,
    val unitPricePiastres: Long,
    val lineDiscountPiastres: Long,
    val authorisedByUserId: String?,
)
