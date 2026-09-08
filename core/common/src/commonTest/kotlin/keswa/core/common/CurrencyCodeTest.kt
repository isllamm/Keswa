package keswa.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CurrencyCodeTest {

    @Test
    fun `known currencies resolve their minor unit exponent`() {
        assertEquals(2, CurrencyCode("EGP").minorUnitExponent)
        assertEquals(3, CurrencyCode("KWD").minorUnitExponent)
        assertEquals(0, CurrencyCode("JPY").minorUnitExponent)
    }

    @Test
    fun `unknown currency defaults to two decimals`() {
        assertEquals(2, CurrencyCode("XYZ").minorUnitExponent)
    }

    @Test
    fun `invalid codes are rejected`() {
        assertFailsWith<IllegalArgumentException> { CurrencyCode("eg") }
        assertFailsWith<IllegalArgumentException> { CurrencyCode("EGYP") }
        assertFailsWith<IllegalArgumentException> { CurrencyCode("E1P") }
    }
}
