package app.passage.core.git.ssh

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import com.jcraft.jsch.JSch
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
 *
 * Only RSA is generated: Ed25519 is unavailable in the Android JCA before API 33 and
 * JSch cannot read Ed25519 keys in PKCS#8, which made every clone fail with "invalid privatekey".
 */
object SshKeyManager {

    private const val COMMENT = "passage-android@device"
    private const val RSA_KEY_SIZE = 3072

    /**
     * Generates an RSA SSH keypair; the private key is PKCS#8 PEM (`BEGIN PRIVATE KEY`),
     * the only RSA PEM layout JSch parses for generated keys.
     */
    fun generateKeyPair(keySize: Int = RSA_KEY_SIZE): GeneratedSshKeyPair {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(keySize, SecureRandom())
        return formatRsaKeyPair(kpg.generateKeyPair())
    }

    /**
     * Loads a stored private key with JSch and rebuilds its public key.
     * Returns null when JSch cannot parse it, so callers can regenerate it.
     */
    fun loadKeyPair(privateKeyPem: String): GeneratedSshKeyPair? {
        return try {
            val parsed = com.jcraft.jsch.KeyPair.load(JSch(), privateKeyPem.toByteArray(Charsets.UTF_8), null)
            val keyType = when (parsed.keyType) {
                com.jcraft.jsch.KeyPair.RSA -> "ssh-rsa"
                else -> return null
            }
            val base64Pub = Base64.getEncoder().encodeToString(parsed.publicKeyBlob)
            GeneratedSshKeyPair("$keyType $base64Pub $COMMENT", privateKeyPem)
        } catch (e: Exception) {
            null
        }
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

        val privateKeyPem = encodePrivateKeyToPkcs8Pem(keyPair.private.encoded)

        return GeneratedSshKeyPair(
            publicKeyOpenSsh = publicKeyOpenSsh,
            privateKeyPkcs8Pem = privateKeyPem
        )
    }

    private fun encodePrivateKeyToPkcs8Pem(privateKeyBytes: ByteArray): String {
        val base64 = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(privateKeyBytes)
        return "-----BEGIN PRIVATE KEY-----\n$base64\n-----END PRIVATE KEY-----\n"
    }
}
