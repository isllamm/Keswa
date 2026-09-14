package com.alsoug.keswa.features.catalog.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Ean13Test {

    @Test
    fun `check digit matches known-good barcodes`() {
        // Given real EAN-13s with their published check digits
        assertEquals(7, Ean13.checkDigit("590123412345"))
        assertEquals(1, Ean13.checkDigit("400638133393"))
        assertEquals(4, Ean13.checkDigit("978020137962")) // ISBN 978-0-201-37962-4
    }

    @Test
    fun `complete appends the check digit`() {
        assertEquals("5901234123457", Ean13.complete("590123412345"))
        assertEquals("4006381333931", Ean13.complete("400638133393"))
    }

    @Test
    fun `validation accepts real codes and rejects corrupted ones`() {
        assertTrue(Ean13.isValid("5901234123457"))

        // A single transposed digit must fail — that is what the check digit is for
        assertFalse(Ean13.isValid("5901234123458"))
        assertFalse(Ean13.isValid("590123412345"))   // too short
        assertFalse(Ean13.isValid("59012341234571")) // too long
        assertFalse(Ean13.isValid("59012341234X7")) // not digits
    }

    @Test
    fun `in-store codes sit in the reserved 20-29 range`() {
        // Given a sequence of shop-printed codes
        val first = Ean13.inStore(1)
        val later = Ean13.inStore(4321)

        // Then each is a valid EAN-13 the shop is entitled to mint
        assertEquals("2000000000015", first)
        assertTrue(Ean13.isValid(first))
        assertTrue(Ean13.isValid(later))
        assertTrue(Ean13.isInStore(first))
        assertTrue(Ean13.isInStore(later))

        // And a manufacturer's code is not mistaken for one of ours
        assertFalse(Ean13.isInStore("5901234123457"))
    }

    @Test
    fun `malformed input is refused rather than silently corrected`() {
        assertFailsWith<IllegalArgumentException> { Ean13.checkDigit("12345") }
        assertFailsWith<IllegalArgumentException> { Ean13.checkDigit("abcdefghijkl") }
        assertFailsWith<IllegalArgumentException> { Ean13.inStore(1, prefix = "30") }
        assertFailsWith<IllegalArgumentException> { Ean13.inStore(999_999_999_999) }
    }
}

class SkuTest {

    @Test
    fun `sku is readable and derived from both names`() {
        assertEquals("OXF-NAV", Sku.of("Oxford shirt", "Navy"))
        assertEquals("ROU-RED", Sku.of("Round-neck t-shirt", "Red"))
    }

    @Test
    fun `unusable names fall back so the shape stays constant`() {
        assertEquals("TEE-RED", Sku.of("Tee", "Red"))
        assertEquals("XXX-BLU", Sku.of("!!!", "Blue"))
    }

    @Test
    fun `disambiguation only kicks in past the first attempt`() {
        assertEquals("OXF-NAV", Sku.disambiguate("OXF-NAV", 1))
        assertEquals("OXF-NAV-2", Sku.disambiguate("OXF-NAV", 2))
    }
}
