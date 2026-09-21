package com.alsoug.keswa.core.database

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The third of the append-only guards, alongside [LedgerIsAppendOnlyTest] for stock and
 * [SalesAreAppendOnlyTest] for money.
 *
 * A refund is money leaving the shop. It is corrected by a void that leaves the original standing,
 * never by an edit — and read from the source, because a behavioural test cannot prove the absence
 * of an API.
 */
class ReturnsAreAppendOnlyTest {

    private val source: String by lazy {
        val file = File("src/commonMain/kotlin/com/alsoug/keswa/core/database/dao/SaleReturnDao.kt")
        if (!file.exists()) {
            fail("expected to find ${file.absolutePath}; has the DAO moved, or the working directory changed?")
        }
        file.readText()
    }

    @Test
    fun `no return or return line can be deleted`() {
        listOf("sale_return", "sale_return_line").forEach { table ->
            val forbidden = Regex("""DELETE\s+FROM\s+$table\b""", RegexOption.IGNORE_CASE)
            assertTrue(
                forbidden.find(source) == null,
                "$table is append-only — a refund in error is voided, never deleted",
            )
        }
    }

    @Test
    fun `the only updates are the void and the exchange link`() {
        val updates = Regex("""UPDATE\s+(sale_return\w*)""", RegexOption.IGNORE_CASE)
            .findAll(source)
            .map { it.groupValues[1].lowercase() }
            .toList()

        assertTrue(
            updates.size == 2 && updates.all { it == "sale_return" },
            "expected exactly two UPDATEs — the void and the exchange link — but found: $updates",
        )
        assertTrue(
            "status = 'VOIDED'" in source && "AND status = 'COMPLETED'" in source,
            "the void must be guarded on the current status, or a double submit voids twice",
        )
        // The line is set once when the replacement sale is rung up; nothing else writes it.
        assertTrue("SET exchangeSaleId" in source)
    }

    @Test
    fun `no Delete or Update annotation targets a return`() {
        assertTrue("@Delete" !in source, "@Delete must not appear on the returns DAO")
        assertTrue("@Update" !in source, "@Update must not appear on the returns DAO")
    }

    @Test
    fun `the returned-so-far guard counts only completed returns`() {
        // A voided return must stop blocking the goods it was about, or a refund made in error
        // would permanently consume the customer's right to return.
        val guard = source.substringAfter("fun alreadyReturned").let { source.substringBefore(it) }
        assertTrue("r.status = 'COMPLETED'" in source, "alreadyReturned must ignore voided returns")
        assertTrue(guard.isNotEmpty())
    }
}
