package com.alsoug.keswa.core.domain.model

import com.alsoug.keswa.core.domain.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CreditPolicyTest {

    @Test
    fun `isCashOnly reports true if and only if credit limit is zero`() {
        assertTrue(CreditPolicy.isCashOnly(Money.ZERO))
        assertTrue(CreditPolicy.isCashOnly(Money.ofPiastres(0)))
        assertFalse(CreditPolicy.isCashOnly(Money.ofPounds(1)))
        assertFalse(CreditPolicy.isCashOnly(Money.ofPounds(50_000)))
    }

    @Test
    fun `wouldExceed accurately checks balance plus pending sale against limit`() {
        val limit = Money.ofPounds(10_000)
        val balance = Money.ofPounds(6_000)

        // Under limit (6k + 3k = 9k <= 10k)
        assertFalse(CreditPolicy.wouldExceed(balance, limit, Money.ofPounds(3_000)))

        // Exact limit (6k + 4k = 10k <= 10k)
        assertFalse(CreditPolicy.wouldExceed(balance, limit, Money.ofPounds(4_000)))

        // Over limit (6k + 4.01k > 10k)
        assertTrue(CreditPolicy.wouldExceed(balance, limit, Money.ofPounds(4_001)))
        assertTrue(CreditPolicy.wouldExceed(balance, limit, Money.ofPounds(5_000)))
    }

    @Test
    fun `excess calculates exact overflow amount or zero when within limit`() {
        val limit = Money.ofPounds(10_000)
        val balance = Money.ofPounds(7_000)

        // 7k + 2k = 9k <= 10k -> excess 0
        assertEquals(Money.ZERO, CreditPolicy.excess(balance, limit, Money.ofPounds(2_000)))

        // 7k + 3k = 10k == 10k -> excess 0
        assertEquals(Money.ZERO, CreditPolicy.excess(balance, limit, Money.ofPounds(3_000)))

        // 7k + 5k = 12k > 10k -> excess 2k
        assertEquals(Money.ofPounds(2_000), CreditPolicy.excess(balance, limit, Money.ofPounds(5_000)))

        // 7k + 3.5k = 10.5k > 10k -> excess 500
        assertEquals(Money.ofPounds(500), CreditPolicy.excess(balance, limit, Money.ofPounds(3_500)))
    }
}
