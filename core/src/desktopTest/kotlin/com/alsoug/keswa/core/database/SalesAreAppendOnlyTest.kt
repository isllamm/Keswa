package com.alsoug.keswa.core.database

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The money half of the ledger's invariant, guarded the same way [LedgerIsAppendOnlyTest] guards
 * the stock half: by reading the DAO source, because a behavioural test cannot prove the absence
 * of an API.
 *
 * A sale is a financial record. It is corrected by a void that leaves the original standing, never
 * by an edit or a delete — and the only `UPDATE` this DAO may carry is the void itself.
 */
class SalesAreAppendOnlyTest {

    private val source: String by lazy {
        val file = File("src/commonMain/kotlin/com/alsoug/keswa/core/database/dao/SaleDaos.kt")
        if (!file.exists()) {
            fail("expected to find ${file.absolutePath}; has the DAO moved, or the working directory changed?")
        }
        file.readText()
    }

    @Test
    fun `no sale, line or tender can be deleted`() {
        listOf("sale", "sale_line", "payment").forEach { table ->
            val forbidden = Regex("""DELETE\s+FROM\s+$table\b""", RegexOption.IGNORE_CASE)
            assertTrue(
                forbidden.find(source) == null,
                "$table is append-only — a mistake is voided, never deleted",
            )
        }
    }

    @Test
    fun `the only update to a sale is the void`() {
        val updates = Regex("""UPDATE\s+(sale|sale_line|payment)\b""", RegexOption.IGNORE_CASE)
            .findAll(source)
            .map { it.value }
            .toList()

        assertTrue(
            updates.size == 1 && updates.single().endsWith("sale"),
            "expected exactly one UPDATE (the void), found: $updates",
        )
        assertTrue(
            "status = 'VOIDED'" in source && "AND status = 'COMPLETED'" in source,
            "the void must be guarded on the current status, or a double submit voids twice",
        )
    }

    @Test
    fun `no Delete or Update annotation targets a sale`() {
        assertTrue("@Delete" !in source.substringBefore("interface ShiftDao"))
        assertTrue("@Update" !in source.substringBefore("interface ShiftDao"))
    }

    @Test
    fun `a held sale can still be thrown away`() {
        // The one exception, and it is deliberate: a parked cart has not happened.
        assertTrue("DELETE FROM held_sale" in source)
    }
}
