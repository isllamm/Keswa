package com.alsoug.keswa.core.domain.model

/**
 * Vocabulary shared by the domain and the persistence layer.
 *
 * These live in `domain` rather than beside the entities so that `domain` keeps zero framework
 * imports (ADR-005). The dependency runs data → domain, never the other way.
 */

enum class LocationType { SHOP, WAREHOUSE }

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
