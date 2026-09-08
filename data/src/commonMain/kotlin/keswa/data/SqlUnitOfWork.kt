package keswa.data

import keswa.core.common.AppResult
import keswa.core.common.Clock
import keswa.core.common.CommonError
import keswa.core.common.MonotonicUlidFactory
import keswa.data.db.KeswaDatabase
import keswa.domain.Principal
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class SqlUnitOfWork(
    private val database: KeswaDatabase,
    private val clock: Clock,
    private val idFactory: MonotonicUlidFactory,
    private val writerDispatcher: CoroutineDispatcher,
) : UnitOfWork {

    override suspend fun <T> transaction(principal: Principal, block: UnitOfWorkScope.() -> T): AppResult<T> =
        withContext(writerDispatcher) {
            try {
                val scope = SqlUnitOfWorkScope(database, principal, clock, idFactory)
                AppResult.Ok(database.transactionWithResult { scope.block() })
            } catch (e: Exception) {
                AppResult.Err(CommonError.Unexpected(e.message))
            }
        }
}

private class SqlUnitOfWorkScope(
    private val database: KeswaDatabase,
    private val principal: Principal,
    private val clock: Clock,
    private val idFactory: MonotonicUlidFactory,
) : UnitOfWorkScope {

    override fun recordAudit(
        entityType: String,
        entityId: String,
        action: String,
        summaryJson: String,
        beforeJson: String?,
        afterJson: String?,
        reason: String?,
    ) {
        database.auditEventQueries.insert(
            id = idFactory.next(),
            tenant_id = principal.tenantId,
            store_id = principal.storeId,
            occurred_at = clock.nowEpochMillis(),
            user_id = principal.userId,
            device_id = principal.deviceId,
            entity_type = entityType,
            entity_id = entityId,
            action = action,
            summary_json = summaryJson,
            before_json = beforeJson,
            after_json = afterJson,
            reason = reason,
        )
    }

    override fun enqueueOutbox(table: String, entityId: String, op: OutboxOp, entityUpdatedAt: Long) {
        database.outboxEntryQueries.insert(
            id = idFactory.next(),
            tenant_id = principal.tenantId,
            store_id = principal.storeId,
            entity_table = table,
            entity_id = entityId,
            op = op.name,
            entity_updated_at = entityUpdatedAt,
            created_at = clock.nowEpochMillis(),
        )
    }
}
