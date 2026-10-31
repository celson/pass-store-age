package app.passage.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Clean Kotlin facade wrapping the native UniFFI age engine.
 */
class AgeCryptoManager {

    /**
     * Generates a new random X25519 age keypair.
     */
    suspend fun generateKeyPair(): AgeKeyPair = withContext(Dispatchers.Default) {
        generateKeypair()
    }

    /**
     * Derives the public recipient (`age1...`) from an age secret key (`AGE-SECRET-KEY-1...`).
     */
    suspend fun deriveRecipient(secretIdentity: String): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            identityToRecipient(secretIdentity)
        }
    }

    /**
     * Criptografa o texto/segredo para os destinatários (recipients) usando formato ASCII-armored (padrão gopass).
     */
    suspend fun encryptSecret(
        secret: GopassSecret,
        recipients: List<String>,
        armor: Boolean = true
    ): Result<ByteArray> = withContext(Dispatchers.Default) {
        runCatching {
            val rawText = serializeGopassSecret(secret)
            val bytes = rawText.toByteArray(Charsets.UTF_8)
            encryptBytes(bytes, recipients, armor)
        }
    }

    /**
     * Descriptografa o conteúdo de um arquivo `.age` e retorna o segredo gopass estruturado.
     */
    suspend fun decryptSecret(
        encryptedBytes: ByteArray,
        identities: List<String>,
        passphrase: String? = null
    ): Result<GopassSecret> = withContext(Dispatchers.Default) {
        runCatching {
            val decryptedBytes = decryptBytes(encryptedBytes, identities, passphrase)
            val text = decryptedBytes.toString(Charsets.UTF_8)
            parseGopassSecret(text)
        }
    }

    /**
     * Extrai os destinatários de um arquivo `.age-recipients`.
     */
    fun parseRecipients(fileContent: String): List<String> {
        return parseRecipientsFile(fileContent)
    }
}
