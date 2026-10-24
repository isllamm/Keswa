package com.alsoug.keswa.core.domain.barcode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Ean13Test {

    @Test
    fun `calculates check digits correctly for standard EAN-13 vectors`() {
        assertEquals(1, Ean13.checkDigit("400638133393"))
        assertEquals(4, Ean13.checkDigit("978020137962"))
        assertEquals(5, Ean13.checkDigit("200000000001"))
        assertEquals(2, Ean13.checkDigit("200000000002"))
    }

    @Test
    fun `checkDigit rejects invalid length or non-digits`() {
        assertFailsWith<IllegalArgumentException> {
            Ean13.checkDigit("123")
        }
        assertFailsWith<IllegalArgumentException> {
            Ean13.checkDigit("1234567890123") // 13 digits instead of 12
        }
        assertFailsWith<IllegalArgumentException> {
            Ean13.checkDigit("12345678901a") // non-digit
        }
    }

    @Test
    fun `completes 12-digit payload with check digit`() {
        assertEquals("4006381333931", Ean13.complete("400638133393"))
        assertEquals("2000000000015", Ean13.complete("200000000001"))
    }

    @Test
    fun `isValid accepts valid barcodes and rejects invalid checksums or formats`() {
        assertTrue(Ean13.isValid("4006381333931"))
        assertTrue(Ean13.isValid("2000000000015"))

        assertFalse(Ean13.isValid("4006381333932")) // wrong check digit
        assertFalse(Ean13.isValid("400638133393"))  // 12 digits
        assertFalse(Ean13.isValid("40063813339311")) // 14 digits
        assertFalse(Ean13.isValid("400638133393X"))  // non-digit check digit
    }

    @Test
    fun `isInStore detects reserved 20-29 prefix range`() {
        assertTrue(Ean13.isInStore("2000000000015"))
        assertTrue(Ean13.isInStore(Ean13.inStore(1L, prefix = "29")))

        // External GTIN prefix (e.g. 400 German, 622 Egyptian) must not be considered in-store
        assertTrue(Ean13.isValid("4006381333931"))
        assertFalse(Ean13.isInStore("4006381333931"))

        val egyptianBarcode = Ean13.complete("622123456789")
        assertTrue(Ean13.isValid(egyptianBarcode))
        assertFalse(Ean13.isInStore(egyptianBarcode))

        val outsidePrefix19 = Ean13.complete("190000000001")
        assertTrue(Ean13.isValid(outsidePrefix19))
        assertFalse(Ean13.isInStore(outsidePrefix19))

        val outsidePrefix30 = Ean13.complete("300000000001")
        assertTrue(Ean13.isValid(outsidePrefix30))
        assertFalse(Ean13.isInStore(outsidePrefix30))
    }

    @Test
    fun `inStore generates zero-padded sequence barcode`() {
        val barcode1 = Ean13.inStore(1L)
        assertEquals("2000000000015", barcode1)
        assertTrue(Ean13.isValid(barcode1))
        assertTrue(Ean13.isInStore(barcode1))

        val barcode42 = Ean13.inStore(42L, prefix = "25")
        assertTrue(barcode42.startsWith("250000000042"))
        assertTrue(Ean13.isValid(barcode42))
        assertTrue(Ean13.isInStore(barcode42))
    }

    @Test
    fun `inStore rejects out-of-range prefix or sequence overflow`() {
        assertFailsWith<IllegalArgumentException> {
            Ean13.inStore(1L, prefix = "30") // outside 20..29
        }
        assertFailsWith<IllegalArgumentException> {
            Ean13.inStore(10_000_000_000L) // does not fit in 10 digits
        }
    }
}
