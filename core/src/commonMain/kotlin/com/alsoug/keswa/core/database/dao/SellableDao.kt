package com.alsoug.keswa.core.database.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * Everything the till needs about one scannable item, in one row.
 *
 * A projection rather than four repository calls: a scan happens with a customer waiting, and
 * chasing variant → product → colour → price → on-hand separately is four round trips to answer
 * one question. Every join here is on an indexed column.
 */
data class SellableRow(
    val variantId: String,
    val productId: String,
    val sku: String,
    val name: String,
    val nameAr: String,
    val colourName: String,
    val colourNameAr: String,
    val costPiastres: Long,
    /** Null when nobody has priced this variant yet — the till refuses to guess. */
    val pricePiastres: Long?,
    val onHand: Int,
)

@Dao
interface SellableDao {

    @Query(
        """
        SELECT v.id AS variantId, v.productId AS productId, v.sku AS sku,
               p.name AS name, p.nameAr AS nameAr,
               c.name AS colourName, c.nameAr AS colourNameAr,
               v.costPiastres AS costPiastres,
               (SELECT pr.pricePiastres FROM price pr
                 WHERE pr.variantId = v.id AND pr.priceListId = :priceListId
                   AND pr.validFrom <= :at AND (pr.validTo IS NULL OR pr.validTo > :at)
                 ORDER BY pr.validFrom DESC LIMIT 1) AS pricePiastres,
               COALESCE((SELECT soh.quantity FROM stock_on_hand soh
                          WHERE soh.variantId = v.id AND soh.locationId = :locationId), 0) AS onHand
        FROM variant v
        JOIN product p ON p.id = v.productId
        JOIN colour c ON c.id = v.colourId
        JOIN variant_barcode b ON b.variantId = v.id
        WHERE b.barcode = :barcode AND v.isActive = 1
        LIMIT 1
        """,
    )
    suspend fun findByBarcode(
        barcode: String,
        priceListId: String,
        locationId: String,
        at: Long,
    ): SellableRow?

    @Query(
        """
        SELECT v.id AS variantId, v.productId AS productId, v.sku AS sku,
               p.name AS name, p.nameAr AS nameAr,
               c.name AS colourName, c.nameAr AS colourNameAr,
               v.costPiastres AS costPiastres,
               (SELECT pr.pricePiastres FROM price pr
                 WHERE pr.variantId = v.id AND pr.priceListId = :priceListId
                   AND pr.validFrom <= :at AND (pr.validTo IS NULL OR pr.validTo > :at)
                 ORDER BY pr.validFrom DESC LIMIT 1) AS pricePiastres,
               COALESCE((SELECT soh.quantity FROM stock_on_hand soh
                          WHERE soh.variantId = v.id AND soh.locationId = :locationId), 0) AS onHand
        FROM variant v
        JOIN product p ON p.id = v.productId
        JOIN colour c ON c.id = v.colourId
        WHERE v.isActive = 1
          AND (v.sku LIKE '%' || :term || '%'
               OR p.name LIKE '%' || :term || '%'
               OR p.nameAr LIKE '%' || :term || '%')
        ORDER BY p.name, c.name
        LIMIT :limit
        """,
    )
    suspend fun search(
        term: String,
        priceListId: String,
        locationId: String,
        at: Long,
        limit: Int,
    ): List<SellableRow>

    @Query(
        """
        SELECT v.id AS variantId, v.productId AS productId, v.sku AS sku,
               p.name AS name, p.nameAr AS nameAr,
               c.name AS colourName, c.nameAr AS colourNameAr,
               v.costPiastres AS costPiastres,
               (SELECT pr.pricePiastres FROM price pr
                 WHERE pr.variantId = v.id AND pr.priceListId = :priceListId
                   AND pr.validFrom <= :at AND (pr.validTo IS NULL OR pr.validTo > :at)
                 ORDER BY pr.validFrom DESC LIMIT 1) AS pricePiastres,
               COALESCE((SELECT soh.quantity FROM stock_on_hand soh
                          WHERE soh.variantId = v.id AND soh.locationId = :locationId), 0) AS onHand
        FROM variant v
        JOIN product p ON p.id = v.productId
        JOIN colour c ON c.id = v.colourId
        WHERE v.id = :variantId
        """,
    )
    suspend fun byVariantId(
        variantId: String,
        priceListId: String,
        locationId: String,
        at: Long,
    ): SellableRow?
}
