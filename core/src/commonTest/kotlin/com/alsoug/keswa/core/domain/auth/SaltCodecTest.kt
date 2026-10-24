package com.alsoug.keswa.core.domain.auth

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SaltCodecTest {

    @Test
    fun `encodes known byte sequences to exact lowercase hex`() {
        val bytes = byteArrayOf(0x00, 0x01, 0x0a, 0x0f, 0x10, 0x7f, -0x80, -0x01)
        // -0x80 is 0x80 (128), -0x01 is 0xff (255)
        assertEquals("00010a0f107f80ff", bytes.encodeSalt())
    }

    @Test
    fun `decodes hex strings back to exact byte arrays`() {
        val hex = "00010a0f107f80ff"
        val expected = byteArrayOf(0x00, 0x01, 0x0a, 0x0f, 0x10, 0x7f, -0x80, -0x01)
        assertContentEquals(expected, hex.decodeSalt())
    }

    @Test
    fun `round trips random 16-byte and 32-byte salts`() {
        val random = Random(42)
        repeat(50) {
            val length = if (it % 2 == 0) 16 else 32
            val original = random.nextBytes(length)
            val encoded = original.encodeSalt()
            val decoded = encoded.decodeSalt()

            assertEquals(length * 2, encoded.length)
            assertContentEquals(original, decoded)
        }
    }

    @Test
    fun `empty byte array encodes to empty string and decodes to empty array`() {
        assertEquals("", byteArrayOf().encodeSalt())
        assertContentEquals(byteArrayOf(), "".decodeSalt())
    }
}
