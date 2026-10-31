package app.passage.core.security.totp

import java.io.ByteArrayOutputStream

/**
 * Clean Base32 decoder compliant with RFC 4648.
 */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun decode(encoded: String): ByteArray {
        val clean = encoded.trim()
            .replace("=", "")
            .replace(" ", "")
            .replace("-", "")
            .uppercase()

        var buffer = 0
        var bitsLeft = 0
        val out = ByteArrayOutputStream()

        for (c in clean) {
            val valChar = ALPHABET.indexOf(c)
            if (valChar < 0) continue

            buffer = (buffer shl 5) or valChar
            bitsLeft += 5

            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out.write((buffer shr bitsLeft) and 0xFF)
            }
        }

        return out.toByteArray()
    }
}
