package com.alsoug.keswa.server

import androidx.room.Room
import com.alsoug.keswa.core.coroutines.DefaultDispatcherProvider
import com.alsoug.keswa.core.database.KeswaDatabase
import com.alsoug.keswa.core.database.getKeswaDatabase
import com.alsoug.keswa.core.domain.UuidIdGenerator
import com.alsoug.keswa.core.sync.LogApplier
import com.alsoug.keswa.core.sync.SyncRow
import com.alsoug.keswa.core.sync.SyncValue
import com.alsoug.keswa.core.sync.SyncValueType
import java.io.File
import java.nio.file.Files

const val CLOCK = 1_757_000_000_000L

fun temporaryLog(): Pair<LogStore, File> {
    val directory = Files.createTempDirectory("keswa-server").toFile()
    return LogStore(directory.resolve("sync-log.db").absolutePath) to directory
}

fun inMemoryShop(): KeswaDatabase =
    getKeswaDatabase(Room.inMemoryDatabaseBuilder<KeswaDatabase>(), DefaultDispatcherProvider())

fun materialiserFor(log: LogStore, shop: KeswaDatabase) =
    Materialiser(log, LogApplier(shop, UuidIdGenerator(), { CLOCK }))

private fun text(value: String) = SyncValue(SyncValueType.TEXT, value)
private fun integer(value: Long) = SyncValue(SyncValueType.INTEGER, value.toString())
private val absent = SyncValue(SyncValueType.NULL)

/**
 * The smallest catalogue a stock movement can hang from, as wire rows.
 *
 * Hand-built rather than read out of a database, so the test says plainly what the server is being
 * asked to accept — including, in [orphanMovement], something it cannot use yet.
 */
fun catalogueRows(): List<SyncRow> = listOf(
    SyncRow(
        "location", "loc-1",
        mapOf(
            "id" to text("loc-1"), "name" to text("Downtown"), "nameAr" to text("وسط البلد"),
            "type" to text("SHOP"), "isDefault" to integer(1), "isActive" to integer(1),
        ),
    ),
    SyncRow(
        "category", "cat-1",
        mapOf(
            "id" to text("cat-1"), "parentId" to absent, "name" to text("T-shirts"),
            "nameAr" to text("تيشيرتات"), "path" to text("/cat-1/"), "depth" to integer(0),
            "sortOrder" to integer(0), "isActive" to integer(1), "createdAt" to integer(0),
            "updatedAt" to integer(0),
        ),
    ),
    SyncRow(
        "colour", "col-1",
        mapOf(
            "id" to text("col-1"), "name" to text("Navy"), "nameAr" to text("كحلي"),
            "hex" to text("#20304f"), "sortOrder" to integer(0), "isActive" to integer(1),
        ),
    ),
    SyncRow(
        "product", "prod-1",
        mapOf(
            "id" to text("prod-1"), "name" to text("Round-neck t-shirt"), "nameAr" to text("تيشيرت"),
            "categoryId" to text("cat-1"), "brandId" to absent, "supplierId" to absent,
            "season" to absent, "isActive" to integer(1), "createdAt" to integer(0),
            "updatedAt" to integer(0),
        ),
    ),
    SyncRow(
        "variant", "var-1",
        mapOf(
            "id" to text("var-1"), "productId" to text("prod-1"), "colourId" to text("col-1"),
            "sku" to text("ROU-NAV"), "costPiastres" to integer(12_000), "isActive" to integer(1),
            "createdAt" to integer(0), "updatedAt" to integer(0),
        ),
    ),
)

fun movement(id: String, quantity: Int, variantId: String = "var-1"): SyncRow = SyncRow(
    "stock_movement", id,
    mapOf(
        "id" to text(id), "variantId" to text(variantId), "locationId" to text("loc-1"),
        "quantity" to integer(quantity.toLong()), "reason" to text("RECEIPT"),
        "refType" to absent, "refId" to absent, "occurredAt" to integer(CLOCK),
        "userId" to text("usr-1"), "unitCostPiastres" to absent, "note" to absent,
    ),
)

fun orphanMovement(): SyncRow = movement("mov-orphan", 3, variantId = "var-2")

/** The colour and variant `orphanMovement` is waiting for, which arrive in a later batch. */
fun lateCatalogueRows(): List<SyncRow> = listOf(
    SyncRow(
        "colour", "col-2",
        mapOf(
            "id" to text("col-2"), "name" to text("Beige"), "nameAr" to text("بيج"),
            "hex" to text("#d8c9a3"), "sortOrder" to integer(1), "isActive" to integer(1),
        ),
    ),
    SyncRow(
        "variant", "var-2",
        mapOf(
            "id" to text("var-2"), "productId" to text("prod-1"), "colourId" to text("col-2"),
            "sku" to text("ROU-BEI"), "costPiastres" to integer(12_000), "isActive" to integer(1),
            "createdAt" to integer(0), "updatedAt" to integer(0),
        ),
    ),
)
