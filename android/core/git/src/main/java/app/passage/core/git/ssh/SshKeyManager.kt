package app.passage.core.git.ssh

import com.jcraft.jsch.JSch
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.util.Base64

data class GeneratedSshKeyPair(
    val publicKeyOpenSsh: String,
    val privateKeyPem: String
)

/**
 * Manages SSH keypair generation and OpenSSH formatting.
 *
 * Ed25519 keys are generated with BouncyCastle (the Android JCA only has Ed25519 from API 33)
 * and stored in the OpenSSH format, the only Ed25519 layout JSch parses. RSA is kept as a
 * fallback and so that keys saved by earlier versions keep working.
 */
object SshKeyManager {

    private const val COMMENT = "passage-android@device"
    private const val RSA_KEY_SIZE = 3072

    /**
     * Routes JSch's Ed25519 (key parsing, signing, host key verification) to its
     * BouncyCastle implementation. Safe to call repeatedly.
     */
    fun installEd25519Provider() {
        JSch.setConfig("ssh-ed25519", "com.jcraft.jsch.bc.SignatureEd25519")
        JSch.setConfig("keypairgen.eddsa", "com.jcraft.jsch.bc.KeyPairGenEdDSA")
        JSch.setConfig("keypairgen_fromprivate.eddsa", "com.jcraft.jsch.bc.KeyPairGenEdDSA")
    }

    /**
     * Generates an Ed25519 SSH keypair, falling back to RSA if BouncyCastle cannot be used
     * on this device.
     */
    fun generateKeyPair(): GeneratedSshKeyPair {
        return try {
            generateEd25519KeyPair()
        } catch (e: Exception) {
            generateRsaKeyPair()
        } catch (e: LinkageError) {
            generateRsaKeyPair()
        }
    }

    /**
     * Loads a stored private key with JSch and rebuilds its public key.
     * Returns null when JSch cannot parse it, so callers can regenerate it.
     */
    fun loadKeyPair(privateKeyPem: String): GeneratedSshKeyPair? {
        return try {
            installEd25519Provider()
            val parsed = com.jcraft.jsch.KeyPair.load(JSch(), privateKeyPem.toByteArray(Charsets.UTF_8), null)
            val keyType = when (parsed.keyType) {
                com.jcraft.jsch.KeyPair.ED25519 -> "ssh-ed25519"
                com.jcraft.jsch.KeyPair.RSA -> "ssh-rsa"
                else -> return null
            }
            val base64Pub = Base64.getEncoder().encodeToString(parsed.publicKeyBlob)
            GeneratedSshKeyPair("$keyType $base64Pub $COMMENT", privateKeyPem)
        } catch (e: Exception) {
            null
        } catch (e: LinkageError) {
            null
        }
    }

    private fun generateEd25519KeyPair(): GeneratedSshKeyPair {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val keyPair = generator.generateKeyPair()
        val seed = (keyPair.private as Ed25519PrivateKeyParameters).encoded
        val rawPub = (keyPair.public as Ed25519PublicKeyParameters).encoded

        val publicBlob = sshString("ssh-ed25519".toByteArray(Charsets.US_ASCII)) + sshString(rawPub)
        val base64Pub = Base64.getEncoder().encodeToString(publicBlob)

        return GeneratedSshKeyPair(
            publicKeyOpenSsh = "ssh-ed25519 $base64Pub $COMMENT",
            privateKeyPem = encodeOpenSshPrivateKey(seed, rawPub, publicBlob)
        )
    }

    /**
     * Serializes an unencrypted `openssh-key-v1` private key (PROTOCOL.key in OpenSSH).
     */
    private fun encodeOpenSshPrivateKey(seed: ByteArray, rawPub: ByteArray, publicBlob: ByteArray): String {
        val check = SecureRandom().nextInt()
        val section = ByteArrayOutputStream()
        DataOutputStream(section).run {
            writeInt(check)
            writeInt(check)
            write(sshString("ssh-ed25519".toByteArray(Charsets.US_ASCII)))
            write(sshString(rawPub))
            write(sshString(seed + rawPub))
            write(sshString(COMMENT.toByteArray(Charsets.UTF_8)))
            var pad = 1
            while (section.size() % 8 != 0) writeByte(pad++)
        }

        val out = ByteArrayOutputStream()
        out.write("openssh-key-v1\u0000".toByteArray(Charsets.US_ASCII))
        DataOutputStream(out).run {
            write(sshString("none".toByteArray(Charsets.US_ASCII)))
            write(sshString("none".toByteArray(Charsets.US_ASCII)))
            write(sshString(ByteArray(0)))
            writeInt(1)
            write(sshString(publicBlob))
            write(sshString(section.toByteArray()))
        }

        val base64 = Base64.getMimeEncoder(70, "\n".toByteArray()).encodeToString(out.toByteArray())
        return "-----BEGIN OPENSSH PRIVATE KEY-----\n$base64\n-----END OPENSSH PRIVATE KEY-----\n"
    }

    private fun sshString(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).run {
            writeInt(bytes.size)
            write(bytes)
        }
        return out.toByteArray()
    }

    /**
     * Generates an RSA SSH keypair; the private key is PKCS#8 PEM (`BEGIN PRIVATE KEY`),
     * the only RSA PEM layout JSch parses for generated keys.
     */
    private fun generateRsaKeyPair(keySize: Int = RSA_KEY_SIZE): GeneratedSshKeyPair {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(keySize, SecureRandom())
        return formatRsaKeyPair(kpg.generateKeyPair())
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
            privateKeyPem = privateKeyPem
        )
    }

    private fun encodePrivateKeyToPkcs8Pem(privateKeyBytes: ByteArray): String {
        val base64 = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(privateKeyBytes)
        return "-----BEGIN PRIVATE KEY-----\n$base64\n-----END PRIVATE KEY-----\n"
    }
}
