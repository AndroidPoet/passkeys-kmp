package io.github.androidpoet.passkeys.internal

import kotlin.test.Test
import kotlin.test.assertEquals

class CborWriterTest {
    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    @Test
    fun test_smallIntegersUseImmediateEncoding() {
        // 0..23 encode in a single byte; -7 (COSE ES256) is major type 1 -> 0x26.
        assertEquals("00", hex(CborWriter().integer(0).toByteArray()))
        assertEquals("17", hex(CborWriter().integer(23).toByteArray()))
        assertEquals("26", hex(CborWriter().integer(-7).toByteArray()))
    }

    @Test
    fun test_integerWidthSelection() {
        assertEquals("1818", hex(CborWriter().integer(24).toByteArray())) // 1-byte arg
        assertEquals("1901f4", hex(CborWriter().integer(500).toByteArray())) // 2-byte arg (500)
        assertEquals("1a00010000", hex(CborWriter().integer(65536).toByteArray())) // 4-byte arg
    }

    @Test
    fun test_textStringHeaderAndPayload() {
        // "packed" -> 0x66 (text, len 6) + ascii bytes.
        assertEquals("667061636b6564", hex(CborWriter().textString("packed").toByteArray()))
    }

    @Test
    fun test_byteStringHeaderAndPayload() {
        // 3 bytes -> 0x43 (byte string, len 3) + bytes.
        assertEquals("43010203", hex(CborWriter().byteString(byteArrayOf(1, 2, 3)).toByteArray()))
    }

    @Test
    fun test_emptyAttStmtMapIsSingleByte() {
        assertEquals("a0", hex(CborWriter().mapHeader(0).toByteArray()))
    }

    @Test
    fun test_attestationObjectShape() {
        // A "none"-format attestationObject: {fmt:"none", attStmt:{}, authData:<2 bytes>}.
        val bytes =
            CborWriter()
                .mapHeader(3)
                .textString("fmt")
                .textString("none")
                .textString("attStmt")
                .mapHeader(0)
                .textString("authData")
                .byteString(byteArrayOf(0xAA.toByte(), 0xBB.toByte()))
                .toByteArray()
        // a3 (map,3) 63 666d74 ("fmt") 64 6e6f6e65 ("none") 67 61747453746d74 ("attStmt") a0 (map,0)
        // 68 6175746844617461 ("authData") 42 aabb (bytes,2)
        assertEquals(
            "a363666d74646e6f6e656761747453746d74a06861757468446174614" + "2aabb",
            hex(bytes),
        )
    }
}
