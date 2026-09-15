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
