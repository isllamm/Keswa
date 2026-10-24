package com.alsoug.keswa.core.database.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * The numbers an owner opens the app for, read straight off the ledger.
 *
 * **Every figure here is a query, not a subsystem** — which is the payoff of the Phase 1
 * append-only design. Every sale, receipt and return is already an immutable row with a reason
 * code and a timestamp, so sell-through and colour performance are `GROUP BY`s rather than a
 * second set of tables to keep in step.
 *
 * Days are bucketed in **local time** (`'unixepoch', 'localtime'`). A shop's Thursday runs to
 * midnight where the shop is, not in UTC — and in Egypt that is a two-hour shift that would move
 * every evening's takings into the next day.
 */
data class DailyPointRow(val day: String, val amountPiastres: Long, val count: Int)

data class ColourBucketRow(
    val colourId: String,
    val colourName: String,
    val colourNameAr: String,
    val hex: String,
    val soldQuantity: Int,
    val onHandQuantity: Int,
)

data class SellThroughRow(
    val categoryId: String,
    val categoryName: String,
    val categoryNameAr: String,
    val soldQuantity: Int,
    val receivedQuantity: Int,
)

data class HourBucketRow(val dayOfWeek: Int, val hour: Int, val transactions: Int, val amountPiastres: Long)

data class MoverRow(
    val variantId: String,
    val sku: String,
    val productName: String,
    val colourName: String,
    val soldQuantity: Int,
    val revenuePiastres: Long,
    val cogsPiastres: Long,
    val onHandQuantity: Int,
    val receivedQuantity: Int,
    val productNameAr: String? = null,
    val colourNameAr: String? = null,
)

data class HeadlineRow(
    val revenuePiastres: Long,
    val cogsPiastres: Long,
    val transactions: Int,
    val units: Int,
)

@Dao
interface AnalyticsDao {

    @Query(
        """
        SELECT COALESCE(SUM(s.totalPiastres), 0) AS revenuePiastres,
               COALESCE(SUM(l.unitCostPiastres * l.quantity), 0) AS cogsPiastres,
               COUNT(DISTINCT s.id) AS transactions,
               COALESCE(SUM(l.quantity), 0) AS units
        FROM sale s
        JOIN sale_line l ON l.saleId = s.id
        WHERE s.status = 'COMPLETED' AND s.occurredAt >= :from AND s.occurredAt < :to
        """,
    )
    suspend fun headline(from: Long, to: Long): HeadlineRow?

    /** Units that came back, which is what makes a return rate rather than a return count. */
    @Query(
        """
        SELECT COALESCE(SUM(l.quantity), 0) FROM sale_return r
        JOIN sale_return_line l ON l.returnId = r.id
        WHERE r.status = 'COMPLETED' AND r.occurredAt >= :from AND r.occurredAt < :to
        """,
    )
    suspend fun returnedUnits(from: Long, to: Long): Int

    @Query(
        """
        SELECT COALESCE(SUM(refundAmountPiastres), 0) FROM sale_return
        WHERE status = 'COMPLETED' AND occurredAt >= :from AND occurredAt < :to
        """,
    )
    suspend fun refunded(from: Long, to: Long): Long

    @Query(
        """
        SELECT date(occurredAt / 1000, 'unixepoch', 'localtime') AS day,
               COALESCE(SUM(totalPiastres), 0) AS amountPiastres,
               COUNT(*) AS count
        FROM sale
        WHERE status = 'COMPLETED' AND occurredAt >= :from AND occurredAt < :to
        GROUP BY day
        ORDER BY day
        """,
    )
    suspend fun revenueByDay(from: Long, to: Long): List<DailyPointRow>

    /**
     * The chart this product exists for.
     *
     * Sold against still-on-hand, by colour: beige and green over-bought while navy sold out is
     * cash tied up in the wrong colours, and it is next season's buying decision. No generic
     * dashboard shows it.
     */
    @Query(
        """
        SELECT c.id AS colourId, c.name AS colourName, c.nameAr AS colourNameAr, c.hex AS hex,
               COALESCE((
                   SELECT SUM(l.quantity) FROM sale_line l
                   JOIN sale s ON s.id = l.saleId
                   JOIN variant v2 ON v2.id = l.variantId
                   WHERE v2.colourId = c.id AND s.status = 'COMPLETED'
                     AND s.occurredAt >= :from AND s.occurredAt < :to
               ), 0) AS soldQuantity,
               COALESCE((
                   SELECT SUM(soh.quantity) FROM stock_on_hand soh
                   JOIN variant v3 ON v3.id = soh.variantId
                   WHERE v3.colourId = c.id
               ), 0) AS onHandQuantity
        FROM colour c
        WHERE c.isActive = 1
        ORDER BY soldQuantity DESC, c.sortOrder
        """,
    )
    suspend fun colourPerformance(from: Long, to: Long): List<ColourBucketRow>

    /**
     * Sell-through by the admin's own category tree.
     *
     * Rolls up through the materialised `path`, so selecting "T-shirts" includes everything under
     * Round neck, V-neck and Polo. The failure mode this avoids is a rollup that silently counts
     * only directly-assigned products: it under-reports every parent, and nobody notices until the
     * number is used for a buying decision.
     */
    @Query(
        """
        SELECT parent.id AS categoryId, parent.name AS categoryName, parent.nameAr AS categoryNameAr,
               COALESCE((
                   SELECT SUM(l.quantity) FROM sale_line l
                   JOIN sale s ON s.id = l.saleId
                   JOIN variant v ON v.id = l.variantId
                   JOIN product p ON p.id = v.productId
                   JOIN category c ON c.id = p.categoryId
                   WHERE c.path LIKE parent.path || '%' AND s.status = 'COMPLETED'
                     AND s.occurredAt >= :from AND s.occurredAt < :to
               ), 0) AS soldQuantity,
               COALESCE((
                   SELECT SUM(m.quantity) FROM stock_movement m
                   JOIN variant v ON v.id = m.variantId
                   JOIN product p ON p.id = v.productId
                   JOIN category c ON c.id = p.categoryId
                   WHERE c.path LIKE parent.path || '%' AND m.reason = 'RECEIPT'
                     AND m.occurredAt < :to
               ), 0) AS receivedQuantity
        FROM category parent
        WHERE parent.isActive = 1 AND parent.depth = 0
        ORDER BY parent.sortOrder, parent.name
        """,
    )
    suspend fun sellThroughByCategory(from: Long, to: Long): List<SellThroughRow>

    /**
     * When the shop is busy, by weekday and hour.
     *
     * The only panel here about people rather than stock: it is what staffing and delivery
     * scheduling get decided on, and in Egypt it shows a Thursday-to-Saturday week.
     */
    @Query(
        """
        SELECT CAST(strftime('%w', occurredAt / 1000, 'unixepoch', 'localtime') AS INTEGER) AS dayOfWeek,
               CAST(strftime('%H', occurredAt / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour,
               COUNT(*) AS transactions,
               COALESCE(SUM(totalPiastres), 0) AS amountPiastres
        FROM sale
        WHERE status = 'COMPLETED' AND occurredAt >= :from AND occurredAt < :to
        GROUP BY dayOfWeek, hour
        """,
    )
    suspend fun busyHours(from: Long, to: Long): List<HourBucketRow>

    @Query(
        """
        SELECT v.id AS variantId, v.sku AS sku, p.name AS productName, c.name AS colourName,
               p.nameAr AS productNameAr, c.nameAr AS colourNameAr,
               COALESCE(SUM(l.quantity), 0) AS soldQuantity,
               COALESCE(SUM(l.lineTotalPiastres), 0) AS revenuePiastres,
               COALESCE(SUM(l.unitCostPiastres * l.quantity), 0) AS cogsPiastres,
               COALESCE((
                   SELECT SUM(soh.quantity) FROM stock_on_hand soh WHERE soh.variantId = v.id
               ), 0) AS onHandQuantity,
               COALESCE((
                   SELECT SUM(m.quantity) FROM stock_movement m
                   WHERE m.variantId = v.id AND m.reason = 'RECEIPT' AND m.occurredAt < :to
               ), 0) AS receivedQuantity
        FROM sale_line l
        JOIN sale s ON s.id = l.saleId
        JOIN variant v ON v.id = l.variantId
        JOIN product p ON p.id = v.productId
        JOIN colour c ON c.id = v.colourId
        WHERE s.status = 'COMPLETED' AND s.occurredAt >= :from AND s.occurredAt < :to
        GROUP BY v.id
        ORDER BY soldQuantity DESC
        LIMIT :limit
        """,
    )
    suspend fun topMovers(from: Long, to: Long, limit: Int): List<MoverRow>
}
