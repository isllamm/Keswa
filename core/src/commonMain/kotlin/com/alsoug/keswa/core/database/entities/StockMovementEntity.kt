package com.alsoug.keswa.core.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.alsoug.keswa.core.domain.model.MovementReason

/**
 * One immutable change in stock. **Append-only — there is no update or delete.**
 *
 * A mistake is corrected by writing a compensating `ADJUSTMENT`, never by editing history. Three
 * things follow from that:
 *
 * 1. The audit trail is trustworthy — "where did these four shirts go?" is a query.
 * 2. Offline tills merge without conflict resolution, because appends are commutative.
 * 3. Cost, and therefore margin, is derivable rather than guessed.
 *
 * [id] is client-generated so a later sync can upsert idempotently — `cashi_pax` defect F2 was a
 * non-idempotent id creating duplicates on double submit.
 *
 * [quantity] is signed: -2 sold, +10 received.
 */
@Entity(
    tableName = "stock_movement",
    foreignKeys = [
        ForeignKey(
            entity = VariantEntity::class,
            parentColumns = ["id"],
            childColumns = ["variantId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("variantId", "locationId"),
        Index("occurredAt"),
        Index("refType", "refId"),
        Index("locationId"),
    ],
)
data class StockMovementEntity(
    @PrimaryKey val id: String,
    val variantId: String,
    val locationId: String,
    val quantity: Int,
    val reason: MovementReason,
    val refType: String?,
    val refId: String?,
    val occurredAt: Long,
    val userId: String,
)
