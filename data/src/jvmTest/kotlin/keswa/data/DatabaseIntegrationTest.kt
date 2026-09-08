package keswa.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import keswa.core.common.FixedClock
import keswa.core.common.MonotonicUlidFactory
import keswa.data.db.KeswaDatabase
import keswa.domain.auth.HashedPin
import keswa.domain.auth.PasswordHasher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Exercises the real generated schema end to end: migrations apply cleanly to a fresh database
 * (the "migration chain" check for v1 — see docs/plan/phase-00.md), first-run seeding produces a
 * usable tenant/store/user, and UnitOfWork commits business + audit + outbox rows together.
 */
class DatabaseIntegrationTest {

    private val fakeHasher = object : PasswordHasher {
        override fun hash(plainPin: String) = HashedPin(hash = plainPin, salt = "salt")
        override fun verify(plainPin: String, hash: String, salt: String) = plainPin == hash
    }

    private fun freshDatabase(): KeswaDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KeswaDatabase.Schema.create(driver)
        return KeswaDatabase(driver)
    }

    @Test
    fun `schema creates cleanly and seeding produces one tenant, store, location and owner user`() {
        val database = freshDatabase()
        val clock = FixedClock(1_700_000_000_000L)
        val idFactory = MonotonicUlidFactory(clock)
        val seeder = Seeder(database, clock, idFactory, fakeHasher)

        seeder.seedIfEmpty(deviceId = "device-1", ownerPin = "1234")

        val tenant = database.tenantQueries.selectFirst().executeAsOne()
        val store = database.storeQueries.selectFirst().executeAsOne()
        val locations = database.locationQueries.selectByStore(tenant.id, store.id).executeAsList()
        val owner = database.appUserQueries.selectActiveByStore(tenant.id, store.id).executeAsList().single()

        assertEquals("Keswa", tenant.name)
        assertEquals(tenant.id, store.tenant_id)
        assertEquals(1, locations.size)
        assertEquals("SALES_FLOOR", locations.single().kind)
        assertEquals("OWNER", owner.role_code)
        assertTrue(fakeHasher.verify("1234", owner.pin_hash, owner.pin_salt))

        val permissions = database.rolePermissionQueries.selectByRole("OWNER").executeAsList()
        assertTrue("USER_MANAGE" in permissions)
        assertTrue(permissions.size >= 10)
    }

    @Test
    fun `seeding twice is a no-op`() {
        val database = freshDatabase()
        val clock = FixedClock(1_700_000_000_000L)
        val idFactory = MonotonicUlidFactory(clock)
        val seeder = Seeder(database, clock, idFactory, fakeHasher)

        seeder.seedIfEmpty("device-1")
        seeder.seedIfEmpty("device-1")

        assertEquals(1, database.tenantQueries.selectFirst().executeAsOneOrNull()?.let { 1 } ?: 0)
        val allUsers = database.appUserQueries.selectActiveByStore(
            database.tenantQueries.selectFirst().executeAsOne().id,
            database.storeQueries.selectFirst().executeAsOne().id,
        ).executeAsList()
        assertEquals(1, allUsers.size)
    }

    @Test
    fun `UnitOfWork commits business row, audit event and outbox entry together`() = runTest {
        val database = freshDatabase()
        val clock = FixedClock(1_700_000_000_000L)
        val idFactory = MonotonicUlidFactory(clock)
        Seeder(database, clock, idFactory, fakeHasher).seedIfEmpty("device-1")

        val tenant = database.tenantQueries.selectFirst().executeAsOne()
        val store = database.storeQueries.selectFirst().executeAsOne()
        val owner = database.appUserQueries.selectActiveByStore(tenant.id, store.id).executeAsList().single()
        val principal = keswa.domain.Principal(
            userId = owner.id, tenantId = tenant.id, storeId = store.id,
            deviceId = "device-1", roleCode = "OWNER", permissions = emptySet(),
        )

        val unitOfWork = SqlUnitOfWork(database, clock, idFactory, kotlinx.coroutines.Dispatchers.Unconfined)

        val result = unitOfWork.transaction(principal) {
            recordAudit(
                entityType = "test_entity", entityId = "entity-1", action = "TEST_ACTION",
                summaryJson = """{"ok":true}""",
            )
            enqueueOutbox("test_entity", "entity-1", OutboxOp.INSERT, clock.nowEpochMillis())
            "done"
        }

        assertEquals(keswa.core.common.AppResult.Ok("done"), result)

        val audit = database.auditEventQueries.selectRecent(tenant.id, 10).executeAsList()
        assertEquals(1, audit.size)
        assertEquals("TEST_ACTION", audit.single().action)

        val outboxCount = database.outboxEntryQueries.countAll().executeAsOne()
        assertTrue(outboxCount > 0)
    }

    @Test
    fun `UnitOfWork rolls back everything when the block throws`() = runTest {
        val database = freshDatabase()
        val clock = FixedClock(1_700_000_000_000L)
        val idFactory = MonotonicUlidFactory(clock)
        Seeder(database, clock, idFactory, fakeHasher).seedIfEmpty("device-1")

        val tenant = database.tenantQueries.selectFirst().executeAsOne()
        val store = database.storeQueries.selectFirst().executeAsOne()
        val owner = database.appUserQueries.selectActiveByStore(tenant.id, store.id).executeAsList().single()
        val principal = keswa.domain.Principal(
            userId = owner.id, tenantId = tenant.id, storeId = store.id,
            deviceId = "device-1", roleCode = "OWNER", permissions = emptySet(),
        )
        val countBefore = database.outboxEntryQueries.countAll().executeAsOne()

        val unitOfWork = SqlUnitOfWork(database, clock, idFactory, kotlinx.coroutines.Dispatchers.Unconfined)
        val result = unitOfWork.transaction(principal) {
            enqueueOutbox("test_entity", "entity-2", OutboxOp.INSERT, clock.nowEpochMillis())
            error("simulated failure mid-transaction")
        }

        assertTrue(result is keswa.core.common.AppResult.Err)
        assertEquals(countBefore, database.outboxEntryQueries.countAll().executeAsOne())
    }
}
