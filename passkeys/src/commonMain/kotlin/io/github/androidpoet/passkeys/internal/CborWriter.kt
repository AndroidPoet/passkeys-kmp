package io.github.androidpoet.passkeys.internal

/**
 * Minimal CBOR (RFC 8949) encoder — just enough to assemble a WebAuthn
 * `attestationObject` from the pieces libfido2 returns (a 3-entry map of a text
 * `fmt`, a `attStmt` map, and the raw `authData` byte string). Not a general
 * CBOR library.
 */
internal class CborWriter {
    private val out = ArrayList<Byte>()

    fun mapHeader(entries: Int): CborWriter = writeTypeAndLength(MAJOR_MAP, entries.toLong())

    fun arrayHeader(items: Int): CborWriter = writeTypeAndLength(MAJOR_ARRAY, items.toLong())

    fun textString(value: String): CborWriter {
        val bytes = value.encodeToByteArray()
        writeTypeAndLength(MAJOR_TEXT, bytes.size.toLong())
        out.addAll(bytes.asList())
        return this
    }

    fun byteString(value: ByteArray): CborWriter {
        writeTypeAndLength(MAJOR_BYTES, value.size.toLong())
        out.addAll(value.asList())
        return this
    }

    /** Writes a CBOR integer (unsigned or negative), e.g. the COSE alg `-7`. */
    fun integer(value: Long): CborWriter =
        if (value >= 0) {
            writeTypeAndLength(MAJOR_UNSIGNED, value)
        } else {
            writeTypeAndLength(MAJOR_NEGATIVE, -1 - value)
        }

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun writeTypeAndLength(major: Int, length: Long): CborWriter {
        val high = major shl FIVE_BITS
        when {
            length < IMMEDIATE_LIMIT -> out.add((high or length.toInt()).toByte())
            length < ONE_BYTE_LIMIT -> {
                out.add((high or ARG_ONE_BYTE).toByte())
                out.add(length.toByte())
            }
            length < TWO_BYTE_LIMIT -> {
                out.add((high or ARG_TWO_BYTES).toByte())
                out.add((length shr BYTE_BITS).toByte())
                out.add(length.toByte())
            }
            else -> {
                out.add((high or ARG_FOUR_BYTES).toByte())
                for (shift in intArrayOf(THREE_BYTES, TWO_BYTES, BYTE_BITS, 0)) {
                    out.add((length shr shift).toByte())
                }
            }
        }
        return this
    }

    private companion object {
        const val MAJOR_UNSIGNED = 0
        const val MAJOR_NEGATIVE = 1
        const val MAJOR_BYTES = 2
        const val MAJOR_TEXT = 3
        const val MAJOR_ARRAY = 4
        const val MAJOR_MAP = 5

        const val FIVE_BITS = 5
        const val BYTE_BITS = 8
        const val TWO_BYTES = 16
        const val THREE_BYTES = 24

        const val IMMEDIATE_LIMIT = 24L
        const val ONE_BYTE_LIMIT = 256L
        const val TWO_BYTE_LIMIT = 65536L

        const val ARG_ONE_BYTE = 24
        const val ARG_TWO_BYTES = 25
        const val ARG_FOUR_BYTES = 26
    }
}
