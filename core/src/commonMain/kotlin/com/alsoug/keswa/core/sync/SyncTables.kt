package com.alsoug.keswa.core.sync

/**
 * Which tables sync, and by what rule they merge.
 *
 * One registry, read by three things that must never disagree: the triggers that fill the outbox,
 * the applier that writes pulled rows, and the server that materialises its log. Three lists would
 * be three chances for a table to be enqueued and never applied, which fails silently and looks
 * like a sync that works.
 */
enum class SyncKind {
    /**
     * Written once, never touched again. Merges by union on the client-generated id, so two
     * devices cannot conflict — this is the property Phase 1 bought and every phase since kept.
     */
    EVENT,

    /**
     * Written once, then at most one transition to a terminal state: a sale is voided, a shift is
     * closed, a receipt is posted. The transition is guarded on the current state in SQL, which
     * makes it idempotent, and terminal beats non-terminal whichever order the two rows arrive in.
     */
    DOCUMENT,

    /**
     * The shop's configuration, freely editable. The only pile where two devices can genuinely
     * disagree, and the only one that needs last-write-wins.
     */
    RECORD,
}

/** When a row reaches the outbox. */
enum class SyncTrigger {
    /** On insert, and again on every update. */
    ON_WRITE,

    /** Only on the transition to `POSTED`, together with the children named in [SyncTable.posts]. */
    ON_POST,

    /** Never on its own — its parent enqueues it. A draft's lines are nobody else's business. */
    WITH_PARENT,
}

data class SyncTable(
    val name: String,
    val kind: SyncKind,
    val trigger: SyncTrigger = SyncTrigger.ON_WRITE,
    /**
     * The column carrying the row's identity. `id` everywhere except `variant_barcode`, which
     * keys on the barcode itself — the scanner reads a barcode, not a surrogate.
     */
    val idColumn: String = "id",
    /** For [SyncTrigger.ON_POST]: the child tables to enqueue alongside, and the column that links them. */
    val posts: List<Child> = emptyList(),
    /**
     * For [SyncKind.DOCUMENT]: the SQL that says an arriving row is *further along* than the stored
     * one. It becomes the `WHERE` of an upsert's `DO UPDATE`, so a void applied twice, or applied
     * before the sale it voids has even arrived, both settle to the same place.
     */
    val progressWhen: String? = null,
) {
    data class Child(val table: String, val parentColumn: String, val idColumn: String = "id")
}

/**
 * Every syncable table, parents before children.
 *
 * The order is the applier's first guess at a workable insert order; it settles by retry rather
 * than trusting it, but starting in dependency order means one pass instead of several.
 *
 * **Three tables are absent on purpose**, and their absence is a decision rather than an oversight:
 *
 * - `held_sale` / `held_sale_line` — a parked basket is one till's scratchpad. Syncing it would
 *   let two tills resume the same one and sell the same garments twice. It is also the only table
 *   in the schema with a real `DELETE`, so leaving it out takes tombstones out of the design.
 * - `stock_on_hand` — a projection of `stock_movement`, rebuildable locally. Syncing a cache
 *   next to its source is how the two come to disagree.
 * - `app_setting` — holds this machine's printer address and its device id. Pushing one till's
 *   printer onto another is a bug with a physical symptom.
 */
val SYNC_TABLES: List<SyncTable> = listOf(
    // Records — the catalogue and the people, parents first.
    SyncTable("category", SyncKind.RECORD),
    SyncTable("colour", SyncKind.RECORD),
    SyncTable("location", SyncKind.RECORD),
    SyncTable("app_user", SyncKind.RECORD),
    SyncTable("price_list", SyncKind.RECORD),
    SyncTable("product", SyncKind.RECORD),
    SyncTable("variant", SyncKind.RECORD),
    SyncTable("variant_barcode", SyncKind.RECORD, idColumn = "barcode"),
    SyncTable("price", SyncKind.RECORD),
    SyncTable("customer", SyncKind.RECORD),
    SyncTable("assortment_pack", SyncKind.RECORD),
    SyncTable("assortment_pack_line", SyncKind.RECORD),

    // Documents — opened, then closed or voided exactly once.
    SyncTable(
        name = "shift",
        kind = SyncKind.DOCUMENT,
        progressWhen = "shift.closedAt IS NULL AND excluded.closedAt IS NOT NULL",
    ),
    SyncTable(
        name = "sale",
        kind = SyncKind.DOCUMENT,
        progressWhen = "sale.status <> 'VOIDED' AND excluded.status = 'VOIDED'",
    ),
    SyncTable(
        name = "sale_return",
        kind = SyncKind.DOCUMENT,
        // Two transitions, not one: a return can be voided, and an exchange links back to the
        // sale that settled the difference once that sale exists.
        progressWhen = "(sale_return.status <> 'VOIDED' AND excluded.status = 'VOIDED') OR " +
            "(sale_return.exchangeSaleId IS NULL AND excluded.exchangeSaleId IS NOT NULL)",
    ),
    SyncTable(
        name = "stock_receipt",
        kind = SyncKind.DOCUMENT,
        trigger = SyncTrigger.ON_POST,
        posts = listOf(SyncTable.Child("stock_receipt_line", "receiptId")),
        progressWhen = "stock_receipt.status <> 'POSTED' AND excluded.status = 'POSTED'",
    ),
    SyncTable(
        name = "stock_count",
        kind = SyncKind.DOCUMENT,
        trigger = SyncTrigger.ON_POST,
        posts = listOf(SyncTable.Child("stock_count_line", "countId")),
        progressWhen = "stock_count.status <> 'POSTED' AND excluded.status = 'POSTED'",
    ),

    // Events — the history itself.
    SyncTable("sale_line", SyncKind.EVENT),
    SyncTable("payment", SyncKind.EVENT),
    SyncTable("sale_return_line", SyncKind.EVENT),
    SyncTable("customer_ledger_entry", SyncKind.EVENT),
    SyncTable("stock_receipt_line", SyncKind.EVENT, SyncTrigger.WITH_PARENT),
    SyncTable("stock_count_line", SyncKind.EVENT, SyncTrigger.WITH_PARENT),
    SyncTable("stock_movement", SyncKind.EVENT),
)

private val byName: Map<String, SyncTable> = SYNC_TABLES.associateBy { it.name }

fun syncTable(name: String): SyncTable? = byName[name]

/**
 * The order the applier attempts, which is the registry order.
 *
 * A row whose parent has not arrived fails on a foreign key and is retried on the next pass rather
 * than dropped, so this is an optimisation and not a correctness requirement.
 */
val SYNC_APPLY_ORDER: Map<String, Int> = SYNC_TABLES.withIndex().associate { (index, t) -> t.name to index }
