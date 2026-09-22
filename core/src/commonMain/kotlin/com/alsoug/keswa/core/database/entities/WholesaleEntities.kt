package com.alsoug.keswa.core.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.alsoug.keswa.core.domain.model.LedgerEntryType

/**
 * A shop that buys from this one.
 *
 * [priceListId] is how trade prices happen: the customer points at a `WHOLESALE` list and the till
 * resolves from it. No per-customer discount percentage layered on top — a second pricing
 * mechanism is how two people end up quoting different prices for the same carton.
 *
 * [creditLimitPiastres] of zero means **cash only**, which is the right default for a customer
 * nobody has decided to trust yet.
 */
@Entity(
    tableName = "customer",
    foreignKeys = [
        ForeignKey(
            entity = PriceListEntity::class,
            parentColumns = ["id"],
            childColumns = ["priceListId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("priceListId"), Index("name"), Index("phone")],
)
data class CustomerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameAr: String,
    val phone: String?,
    val taxId: String?,
    val priceListId: String,
    val creditLimitPiastres: Long,
    val paymentTermsDays: Int,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * One immutable change in what a customer owes. **Append-only — no update, no delete.**
 *
 * The same design as `stock_movement`, because it is the same problem: an invoice is a debit, a
 * payment a credit, and the balance is a plain sum. Three things follow, and they are the three
 * that made the stock ledger worth having:
 *
 * 1. "Why does this customer owe 14,200?" is a query, not a number somebody once typed.
 * 2. Two devices merge by appending, with no conflict resolution — Phase 9 gets this for free.
 * 3. A balance cannot be edited. A mistake is a compensating entry, which is what an accountant
 *    expects and what an auditor asks for.
 *
 * [amountPiastres] is signed: positive is owed, negative is paid.
 *
 * [dueAt] lives on the entry rather than the customer because the entry is the thing that falls
 * due, and ageing reads it directly.
 */
@Entity(
    tableName = "customer_ledger_entry",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("customerId", "occurredAt"),
        Index("refType", "refId"),
        Index("dueAt"),
    ],
)
data class CustomerLedgerEntryEntity(
    @PrimaryKey val id: String,
    val customerId: String,
    val entryType: LedgerEntryType,
    val amountPiastres: Long,
    val refType: String?,
    val refId: String?,
    val occurredAt: Long,
    val dueAt: Long?,
    val userId: String,
    val note: String?,
    /** Set when the entry went through over the customer's limit, and by whom. */
    val authorisedByUserId: String?,
)

/**
 * A carton sold as one thing: "20 navy, 30 white, 10 beige, one price."
 *
 * A catalogue definition, not a kind of stock. Adding one to a basket expands it into ordinary
 * lines — see `ExpandAssortmentPackUseCase` — so `sale_line.variantId` stays non-null and every
 * stock query, analytics rollup and return in the app keeps working unchanged.
 */
@Entity(tableName = "assortment_pack", indices = [Index("name")])
data class AssortmentPackEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameAr: String,
    val pricePiastres: Long,
    val isActive: Boolean,
)

@Entity(
    tableName = "assortment_pack_line",
    foreignKeys = [
        ForeignKey(
            entity = AssortmentPackEntity::class,
            parentColumns = ["id"],
            childColumns = ["packId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = VariantEntity::class,
            parentColumns = ["id"],
            childColumns = ["variantId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("packId"), Index("variantId")],
)
data class AssortmentPackLineEntity(
    @PrimaryKey val id: String,
    val packId: String,
    val variantId: String,
    val quantity: Int,
)
