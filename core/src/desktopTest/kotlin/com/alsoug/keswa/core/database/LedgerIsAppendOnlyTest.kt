package com.alsoug.keswa.core.database

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Guards the single most important invariant in the schema by reading the DAO source.
 *
 * A behavioural test cannot prove the *absence* of an API, and Room's annotations are not retained
 * at runtime, so this asserts on the source text instead. Crude, but it fails the build the moment
 * someone adds `deleteMovement(...)` — which is exactly when it needs to.
 */
class LedgerIsAppendOnlyTest {

    private val source: String by lazy {
        val file = File("src/commonMain/kotlin/com/alsoug/keswa/core/database/dao/StockDaos.kt")
        if (!file.exists()) {
            fail("expected to find ${file.absolutePath}; has the DAO moved, or the test working directory changed?")
        }
        file.readText()
    }

    @Test
    fun `the ledger exposes no way to delete or update a movement`() {
        val forbidden = Regex("""(DELETE\s+FROM|UPDATE)\s+stock_movement""", RegexOption.IGNORE_CASE)
        val offending = forbidden.find(source)
        assertTrue(
            offending == null,
            "stock_movement is append-only (KD-002, architecture plan D3), but found: ${offending?.value}",
        )
    }

    @Test
    fun `no Delete or Update annotation targets the ledger`() {
        // @Delete and @Update take an entity, so their presence anywhere in this file would mean a
        // movement can be mutated.
        assertTrue("@Delete" !in source, "@Delete must not appear on the stock ledger DAO")
        assertTrue(
            "@Update" !in source,
            "@Update must not appear on the stock ledger DAO — corrections are compensating movements",
        )
    }

    @Test
    fun `the projection remains rebuildable`() {
        // The escape hatch that makes a drift recoverable rather than corrupt.
        assertTrue("rebuildProjection" in source)
        assertTrue("stock_on_hand" in source)
    }
}
