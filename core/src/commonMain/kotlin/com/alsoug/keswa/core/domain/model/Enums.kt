package com.alsoug.keswa.core.domain.model

/**
 * Vocabulary shared by the domain and the persistence layer.
 *
 * These live in `domain` rather than beside the entities so that `domain` keeps zero framework
 * imports (ADR-005). The dependency runs data → domain, never the other way.
 */

enum class LocationType { SHOP, WAREHOUSE }

/**
 * Two roles, because the shop has two kinds of person at the till.
 *
 * Not a permissions engine: two fixed roles do not need one, and an engine nobody configures is
 * just a slower `when`. Custom roles, if ever wanted, are an additive migration.
 */
enum class UserRole { ADMIN, SELLER }

/**
 * What a user signs in with.
 *
 * A seller signs in dozens of times a shift, so they get a PIN on a keypad; an admin signs in
 * rarely and holds real power, so they get a password. Making a cashier type a strong password
 * forty times a day guarantees it ends up on a sticky note under the drawer — the auth model
 * itself would have created the vulnerability.
 */
enum class SecretKind { PIN, PASSWORD }

enum class BarcodeSource { OWN, SUPPLIER }

enum class PriceListType { RETAIL, WHOLESALE }

/**
 * Why stock moved. Every movement carries one, which is what makes the ledger auditable and
 * lets sell-through be computed as sales against receipts.
 */
enum class MovementReason {
    SALE,
    RETURN,
    RECEIPT,
    ADJUSTMENT,
    TRANSFER_IN,
    TRANSFER_OUT,
    COUNT,
    DAMAGE,
}

/**
 * Whether a sale still stands.
 *
 * There is no `HELD`: a parked cart has not happened, and lives in its own table so that every
 * revenue query is a plain read with no status filter to forget.
 */
enum class SaleStatus { COMPLETED, VOIDED }

/**
 * How a customer paid.
 *
 * `CARD` records that a card was used; it drives no terminal. Whether this app ever talks to one is
 * Q2, and until that is answered a record is what the shop needs and all it needs.
 */
enum class TenderMethod {
    CASH,
    CARD,

    /**
     * On account — the wholesale tender.
     *
     * Settles the sale at the till and opens a receivable in the same transaction. The money is
     * owed rather than received, which is exactly what the customer ledger is for.
     */
    CREDIT,
}

/**
 * Where a document that moves stock has got to.
 *
 * A `DRAFT` moves nothing, which is what makes unpacking a delivery interruptible. `POSTED` is
 * terminal — a mistake is corrected with an adjustment, never by reopening.
 */
enum class DocumentStatus { DRAFT, POSTED }

/**
 * What state a returned garment came back in.
 *
 * The only thing that decides whether it goes back on the rail — and a `DAMAGED` return still
 * writes a `RETURN` movement before its `DAMAGE` one, because the shop did take it back.
 */
enum class ReturnCondition { SELLABLE, DAMAGED }

/**
 * What kind of entry moved a customer's balance.
 *
 * Signed amounts throughout: an invoice is positive (they owe more), a payment negative. The
 * balance is `SUM(amount)` and nothing else — the same shape as the stock ledger, and for the same
 * three reasons: it is auditable, it merges without conflict resolution, and it cannot be edited.
 */
enum class LedgerEntryType { INVOICE, PAYMENT, CREDIT_NOTE, ADJUSTMENT }
