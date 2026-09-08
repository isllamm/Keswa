package keswa.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyInputTest {

    @Test
    fun `parses a plain decimal into minor units`() {
        assertEquals(2550L, parseMoneyInput("25.50"))
        assertEquals(2550L, parseMoneyInput("25.5"))
        assertEquals(100L, parseMoneyInput("1"))
        assertEquals(0L, parseMoneyInput("0"))
    }

    @Test
    fun `accepts a comma as a decimal separator`() {
        assertEquals(2550L, parseMoneyInput("25,50"))
    }

    @Test
    fun `respects a different minor unit exponent`() {
        assertEquals(25500L, parseMoneyInput("25.5", minorUnitExponent = 3)) // KWD-style
        assertEquals(26L, parseMoneyInput("26", minorUnitExponent = 0)) // JPY-style
    }

    @Test
    fun `blank or non-numeric input is rejected`() {
        assertNull(parseMoneyInput(""))
        assertNull(parseMoneyInput("   "))
        assertNull(parseMoneyInput("abc"))
        assertNull(parseMoneyInput("12.34.56"))
    }

    @Test
    fun `negative amounts are rejected`() {
        assertNull(parseMoneyInput("-5"))
    }

    @Test
    fun `rounds to the nearest minor unit`() {
        assertEquals(1235L, parseMoneyInput("12.346"))
    }
}
