package keswa.data

import keswa.core.common.Clock
import keswa.core.common.CurrencyCode
import keswa.core.common.MonotonicUlidFactory
import keswa.data.db.KeswaDatabase
import keswa.domain.auth.PasswordHasher

/**
 * First-run bootstrap: one tenant, one store, one sales-floor location, the default currency,
 * OWNER permissions, and a single OWNER user. Phase 0 has exactly one store — see
 * docs/data-model.md §3 and ADR-005.
 */
class Seeder(
    private val database: KeswaDatabase,
    private val clock: Clock,
    private val idFactory: MonotonicUlidFactory,
    private val hasher: PasswordHasher,
) {
    /** ASSUMPTION: default currency EGP and initial OWNER PIN "1234" — both change on first run. */
    fun seedIfEmpty(deviceId: String, ownerPin: String = "1234") {
        if (database.tenantQueries.selectFirst().executeAsOneOrNull() != null) return

        val now = clock.nowEpochMillis()
        val tenantId = idFactory.next()
        val storeId = idFactory.next()

        database.tenantQueries.insert(tenantId, "Keswa", CurrencyCode.EGP.iso, now, now, deviceId)
        database.currencyQueries.insert(CurrencyCode.EGP.iso, 2, "ج.م", "EGP")
        database.storeQueries.insert(storeId, tenantId, "ST01", "المتجر الرئيسي", "Africa/Cairo", now, now, deviceId)
        database.locationQueries.insert(
            idFactory.next(), tenantId, storeId, "SF01", "صالة العرض", "SALES_FLOOR", now, now, deviceId,
        )

        for (permission in OWNER_PERMISSIONS) {
            database.rolePermissionQueries.insert("OWNER", permission)
        }

        val hashed = hasher.hash(ownerPin)
        database.appUserQueries.insert(
            idFactory.next(), tenantId, storeId, "المالك", "owner",
            hashed.hash, hashed.salt, "OWNER", now, now, deviceId,
        )
    }

    private companion object {
        val OWNER_PERMISSIONS = listOf(
            "SALE_CREATE", "SALE_VOID", "RETURN_CREATE", "DISCOUNT_APPLY", "DISCOUNT_OVER_LIMIT",
            "PRICE_OVERRIDE", "STOCK_ADJUST", "PO_CREATE", "PO_RECEIVE", "CUSTOMER_CREDIT_GRANT",
            "SHIFT_CLOSE", "REPORT_VIEW_FINANCIAL", "USER_MANAGE", "SETTINGS_EDIT", "BACKUP_RESTORE",
        )
    }
}
