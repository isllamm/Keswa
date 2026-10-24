package com.alsoug.keswa.core.database

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The fourth append-only guard, after stock, sales and returns.
 *
 * What a customer owes is a financial record, and the balance is a *sum* — there is no stored
 * figure to edit, and there must never be a way to edit the entries either. A mistake is a
 * compensating entry, which is what an accountant expects and what an auditor asks for.
 */
class ReceivablesAreAppendOnlyTest {

    private val source: String by lazy {
        val file = File("src/commonMain/kotlin/com/alsoug/keswa/core/database/dao/WholesaleDaos.kt")
        if (!file.exists()) {
            fail("expected to find ${file.absolutePath}; has the DAO moved, or the working directory changed?")
        }
        file.readText()
    }

    private val ledgerSection: String
        get() = source.substringAfter("interface CustomerLedgerDao").substringBefore("interface AssortmentPackDao")

    @Test
    fun `no ledger entry can be deleted or updated`() {
        val forbidden = Regex(
            """(DELETE\s+FROM|UPDATE)\s+customer_ledger_entry\b""",
            RegexOption.IGNORE_CASE,
        )
        val offending = forbidden.find(source)
        assertTrue(
            offending == null,
            "the receivables ledger is append-only, but found: ${offending?.value}",
        )
    }

    @Test
    fun `the ledger DAO carries no Delete or Update annotation`() {
        assertTrue("@Delete" !in ledgerSection, "@Delete must not appear on the receivables ledger")
        assertTrue("@Update" !in ledgerSection, "@Update must not appear on the receivables ledger")
        assertTrue("@Upsert" !in ledgerSection, "@Upsert would let an entry be rewritten in place")
    }

    @Test
    fun `the balance has exactly one definition`() {
        // A stored balance column is one careless UPDATE away from a number nobody can rebuild.
        assertTrue("SUM(amountPiastres)" in ledgerSection)
        assertTrue(
            "balancePiastres" !in source,
            "there must be no stored balance — the sum is the only definition",
        )
    }
}
