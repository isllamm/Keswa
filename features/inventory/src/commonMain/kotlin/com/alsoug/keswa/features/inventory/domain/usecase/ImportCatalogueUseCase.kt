package com.alsoug.keswa.features.inventory.domain.usecase

import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.ICategoryRepository
import com.alsoug.keswa.core.domain.repository.IColourRepository
import com.alsoug.keswa.core.domain.repository.IPriceRepository
import com.alsoug.keswa.core.domain.repository.IProductRepository
import com.alsoug.keswa.core.domain.repository.IVariantRepository
import com.alsoug.keswa.core.session.ISessionManager
import com.alsoug.keswa.core.session.require

/** One row of a supplier's spreadsheet, once it has survived validation. */
data class ImportRow(
    val lineNumber: Int,
    val productName: String,
    val productNameAr: String,
    val colourName: String,
    val sku: String,
    val cost: Money,
    val price: Money,
    val quantity: Int,
)

/** A row that did not survive, and why — always with the line number, or it cannot be fixed. */
data class ImportProblem(
    val lineNumber: Int,
    val message: String,
)

sealed interface ImportResult {
    data class Parsed(val rows: List<ImportRow>) : ImportResult
    data class Rejected(val problems: List<ImportProblem>) : ImportResult
}

data class ImportSummary(
    val productsCreated: Int,
    val variantsCreated: Int,
    val piecesReceived: Int,
)

/**
 * Turns a supplier's spreadsheet into a catalogue.
 *
 * **Validated as a whole, then applied as a whole.** A file with one bad row does not import 199
 * products and leave the operator guessing which one failed — the parse returns either every row or
 * every problem, each naming its line.
 *
 * Expected columns, with a header row that is skipped if present:
 *
 * ```
 * product, productAr, colour, sku, cost, price, quantity
 * ```
 *
 * Quantity is optional and defaults to zero, because the first thing a shop imports is usually a
 * price list rather than a delivery.
 */
class ParseCatalogueImportUseCase {

    operator fun invoke(text: String): ImportResult {
        val lines = text.lineSequence()
            .map { it.trim() }
            .withIndex()
            .filter { (_, line) -> line.isNotEmpty() }
            .toList()

        if (lines.isEmpty()) {
            return ImportResult.Rejected(listOf(ImportProblem(0, "nothing to import")))
        }

        // A header is recognised by its first cell rather than by position, so a file that does not
        // have one still imports.
        val body = lines.filterNot { (_, line) ->
            line.substringBefore(',').trim().equals("product", ignoreCase = true)
        }

        val rows = mutableListOf<ImportRow>()
        val problems = mutableListOf<ImportProblem>()

        body.forEach { (index, line) ->
            val lineNumber = index + 1
            val cells = line.split(',').map { it.trim() }

            if (cells.size < MINIMUM_COLUMNS) {
                problems += ImportProblem(lineNumber, "expected $MINIMUM_COLUMNS columns, found ${cells.size}")
                return@forEach
            }

            val (productName, productNameAr, colourName, sku) = cells
            val cost = Money.parse(cells[4])
            val price = Money.parse(cells[5])
            val quantity = cells.getOrNull(6)?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0

            when {
                productName.isBlank() -> problems += ImportProblem(lineNumber, "product name is empty")
                colourName.isBlank() -> problems += ImportProblem(lineNumber, "colour is empty")
                sku.isBlank() -> problems += ImportProblem(lineNumber, "SKU is empty")
                cost == null -> problems += ImportProblem(lineNumber, "cost '${cells[4]}' is not an amount")
                price == null -> problems += ImportProblem(lineNumber, "price '${cells[5]}' is not an amount")
                quantity < 0 -> problems += ImportProblem(lineNumber, "quantity cannot be negative")
                else -> rows += ImportRow(
                    lineNumber = lineNumber,
                    productName = productName,
                    productNameAr = productNameAr.ifBlank { productName },
                    colourName = colourName,
                    sku = sku,
                    cost = cost,
                    price = price,
                    quantity = quantity,
                )
            }
        }

        // Caught here rather than by the unique index, because "row 41 repeats row 12's SKU" is
        // fixable and "UNIQUE constraint failed" is not.
        rows.groupBy { it.sku }.filterValues { it.size > 1 }.forEach { (sku, duplicates) ->
            duplicates.drop(1).forEach { row ->
                problems += ImportProblem(row.lineNumber, "SKU $sku already appears on line ${duplicates.first().lineNumber}")
            }
        }

        return if (problems.isEmpty()) ImportResult.Parsed(rows) else ImportResult.Rejected(problems)
    }

    private companion object {
        const val MINIMUM_COLUMNS = 6
    }
}

/**
 * Applies a parsed import through the ordinary creation paths.
 *
 * Reusing those paths rather than writing a second one is the whole point: an import that bypasses
 * the rules is how a catalogue acquires products with no category, SKUs that collide, and prices
 * nobody agreed.
 *
 * Stock arrives through a receipt, not by writing movements directly, so imported quantities carry
 * a cost basis and appear in the ledger exactly as a delivery would.
 */
class ApplyCatalogueImportUseCase(
    private val products: IProductRepository,
    private val colours: IColourRepository,
    private val categories: ICategoryRepository,
    private val variants: IVariantRepository,
    private val prices: IPriceRepository,
    private val sessions: ISessionManager,
    private val startReceipt: StartReceiptUseCase,
    private val addLine: AddReceiptLineUseCase,
    private val postReceipt: PostReceiptUseCase,
    private val ids: IdGenerator,
    private val now: () -> Long,
) {

    suspend operator fun invoke(rows: List<ImportRow>, locationId: String): Result<ImportSummary> =
        runCatching {
            sessions.require(Permission.MANAGE_CATALOGUE)
            require(rows.isNotEmpty()) { "nothing to import" }

            val palette = colours.getAll().getOrThrow()
            val categoryId = requireNotNull(categories.getTree().getOrThrow().firstOrNull()?.id) {
                "no category to file imported products under"
            }
            val priceListId = requireNotNull(prices.defaultList().getOrThrow()?.id) {
                "no default price list"
            }

            var productsCreated = 0
            var variantsCreated = 0
            val byProduct = rows.groupBy { it.productName to it.productNameAr }

            val created = mutableListOf<Pair<String, ImportRow>>()
            byProduct.forEach { (names, group) ->
                val (name, nameAr) = names
                val product = products
                    .create(ids.newId(), name, nameAr, categoryId)
                    .getOrThrow()
                productsCreated++

                group.forEach { row ->
                    val colour = palette.firstOrNull { it.name.equals(row.colourName, ignoreCase = true) }
                        ?: colours.create(ids.newId(), row.colourName, row.colourName, DEFAULT_HEX)
                            .getOrThrow()

                    val variant = variants
                        .addColour(ids.newId(), product.id, colour.id, row.sku, row.cost)
                        .getOrThrow()
                    variantsCreated++

                    prices.setPrice(ids.newId(), variant.id, priceListId, row.price, now())
                        .getOrThrow()

                    if (row.quantity > 0) created += variant.id to row
                }
            }

            val pieces = created.sumOf { (_, row) -> row.quantity }
            if (created.isNotEmpty()) {
                val receipt = startReceipt("Import", "Imported", locationId).getOrThrow()
                created.forEach { (variantId, row) ->
                    addLine(receipt.id, variantId, row.quantity, row.cost).getOrThrow()
                }
                postReceipt(receipt.id).getOrThrow()
            }

            ImportSummary(productsCreated, variantsCreated, pieces)
        }

    private companion object {
        /** Imported colours have no hex until someone picks one; mid-grey reads as "unset". */
        const val DEFAULT_HEX = "#808080"
    }
}
