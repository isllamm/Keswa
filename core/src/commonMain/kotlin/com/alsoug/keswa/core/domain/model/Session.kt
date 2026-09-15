package com.alsoug.keswa.core.domain.model

/** Who is operating the till, as the rest of the app needs to know them. Carries no credential. */
data class User(
    val id: String,
    val username: String,
    val displayName: String,
    val displayNameAr: String,
    val role: UserRole,
    val mustChangeSecret: Boolean = false,
)

data class Session(
    val user: User,
    val signedInAtMillis: Long,
) {
    val role: UserRole get() = user.role
}

/**
 * What someone is allowed to do.
 *
 * Deliberately finer-grained than the two roles, because the interesting distinction is not
 * "admin vs seller" but which individual capabilities a role carries — and the one that matters
 * commercially is [VIEW_COST_AND_MARGIN], kept separate from [VIEW_SHOP_ANALYTICS] so a seller can
 * see units and revenue without learning what the shop paid.
 */
enum class Permission {
    SELL,
    REFUND_WITHIN_POLICY,
    REFUND_ANY,
    DISCOUNT_LINE,
    OVERRIDE_PRICE,
    VOID_SALE,
    RECEIVE_STOCK,
    COUNT_STOCK,
    MANAGE_CATALOGUE,
    MANAGE_USERS,
    VIEW_COST_AND_MARGIN,
    VIEW_SHOP_ANALYTICS,
    CHANGE_SETTINGS,
}

val UserRole.permissions: Set<Permission>
    get() = when (this) {
        UserRole.ADMIN -> Permission.entries.toSet()
        UserRole.SELLER -> setOf(
            Permission.SELL,
            Permission.REFUND_WITHIN_POLICY,
            Permission.COUNT_STOCK,
        )
    }

fun Session?.can(permission: Permission): Boolean =
    this != null && permission in role.permissions
