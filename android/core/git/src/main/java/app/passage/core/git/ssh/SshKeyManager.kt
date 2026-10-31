package app.passage.core.git.ssh

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.util.Base64

data class GeneratedSshKeyPair(
    val publicKeyOpenSsh: String,
    val privateKeyPkcs8Pem: String
)

/**
 * Manages SSH keypair generation and OpenSSH formatting.
 */
object SshKeyManager {

    private const val COMMENT = "passage-android@device"

    /**
     * Generates a modern Ed25519 SSH Keypair.
     * Falls back to RSA 4096 if the Android runtime does not have Ed25519 provider.
     */
    fun generateEd25519KeyPair(): GeneratedSshKeyPair {
        return try {
            val kpg = KeyPairGenerator.getInstance("Ed25519")
            val keyPair = kpg.generateKeyPair()
            formatEd25519KeyPair(keyPair)
        } catch (e: Exception) {
            // Fallback to RSA 4096 if Ed25519 algorithm is not present on older Android API levels
            generateRsaKeyPair(4096)
        }
    }

    /**
     * Generates an RSA SSH Keypair of specified bit length (e.g. 4096).
     */
    fun generateRsaKeyPair(keySize: Int = 4096): GeneratedSshKeyPair {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(keySize, SecureRandom())
        val keyPair = kpg.generateKeyPair()
        return formatRsaKeyPair(keyPair)
    }

    private fun formatEd25519KeyPair(keyPair: KeyPair): GeneratedSshKeyPair {
        val pubBytes = keyPair.public.encoded
        // Extract raw 32-byte public key from X.509 SubjectPublicKeyInfo
        val rawPub = pubBytes.takeLast(32).toByteArray()

        val out = ByteArrayOutputStream()
        val dataOut = DataOutputStream(out)

        val keyType = "ssh-ed25519".toByteArray(Charsets.US_ASCII)
        dataOut.writeInt(keyType.size)
        dataOut.write(keyType)

        dataOut.writeInt(rawPub.size)
        dataOut.write(rawPub)
        dataOut.flush()

        val base64Pub = Base64.getEncoder().encodeToString(out.toByteArray())
        val publicKeyOpenSsh = "ssh-ed25519 $base64Pub $COMMENT"

        val privateKeyPem = encodePrivateKeyToPkcs8Pem(keyPair.private.encoded, "PRIVATE KEY")

        return GeneratedSshKeyPair(
            publicKeyOpenSsh = publicKeyOpenSsh,
            privateKeyPkcs8Pem = privateKeyPem
        )
    }

    private fun formatRsaKeyPair(keyPair: KeyPair): GeneratedSshKeyPair {
        val rsaPub = keyPair.public as java.security.interfaces.RSAPublicKey
        val out = ByteArrayOutputStream()
        val dataOut = DataOutputStream(out)

        val keyType = "ssh-rsa".toByteArray(Charsets.US_ASCII)
        dataOut.writeInt(keyType.size)
        dataOut.write(keyType)

        val exp = rsaPub.publicExponent.toByteArray()
        dataOut.writeInt(exp.size)
        dataOut.write(exp)

        val mod = rsaPub.modulus.toByteArray()
        dataOut.writeInt(mod.size)
        dataOut.write(mod)
        dataOut.flush()

        val base64Pub = Base64.getEncoder().encodeToString(out.toByteArray())
        val publicKeyOpenSsh = "ssh-rsa $base64Pub $COMMENT"

        val privateKeyPem = encodePrivateKeyToPkcs8Pem(keyPair.private.encoded, "RSA PRIVATE KEY")

        return GeneratedSshKeyPair(
            publicKeyOpenSsh = publicKeyOpenSsh,
            privateKeyPkcs8Pem = privateKeyPem
        )
    }

    private fun encodePrivateKeyToPkcs8Pem(privateKeyBytes: ByteArray, header: String): String {
        val base64 = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(privateKeyBytes)
        return "-----BEGIN $header-----\n$base64\n-----END $header-----\n"
    }
}
