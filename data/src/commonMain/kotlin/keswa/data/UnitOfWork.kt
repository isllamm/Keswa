package keswa.data

import keswa.core.common.AppResult
import keswa.domain.Principal

/**
 * The single choke point for every mutation — see docs/architecture.md §4 and ADR-007. A
 * transaction either commits business rows, its audit event, and its outbox entry together, or
 * none of them happen at all.
 */
interface UnitOfWork {
    suspend fun <T> transaction(principal: Principal, block: UnitOfWorkScope.() -> T): AppResult<T>
}

interface UnitOfWorkScope {
    fun recordAudit(
        entityType: String,
        entityId: String,
        action: String,
        summaryJson: String,
        beforeJson: String? = null,
        afterJson: String? = null,
        reason: String? = null,
    )

    fun enqueueOutbox(table: String, entityId: String, op: OutboxOp, entityUpdatedAt: Long)
}

enum class OutboxOp { INSERT, UPDATE, DELETE }
