package keswa.domain

/**
 * Who is performing an action, right now. Every use case takes one; nothing reads a global
 * "current user". Phase 1: produced by a local PIN unlock. Phase 4: produced from a server JWT.
 * Call sites never change — see ADR-008.
 */
data class Principal(
    val userId: String,
    val tenantId: String,
    val storeId: String,
    val deviceId: String,
    val roleCode: String,
    val permissions: Set<String>,
) {
    fun has(permission: String): Boolean = permission in permissions
}
