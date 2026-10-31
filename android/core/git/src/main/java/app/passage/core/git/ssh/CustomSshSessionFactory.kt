package app.passage.core.git.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import org.eclipse.jgit.transport.JschConfigSessionFactory
import org.eclipse.jgit.transport.OpenSshConfig
import org.eclipse.jgit.util.FS

/**
 * Custom JGit SSH Session Factory that loads the private key from memory
 * and configures SSH parameters for mobile usage.
 */
class CustomSshSessionFactory(
    private val privateKeyContent: String,
    private val passphrase: String? = null
) : JschConfigSessionFactory() {

    override fun configure(hc: OpenSshConfig.Host?, session: Session?) {
        // Mobile git clients generally bypass interactive terminal known_hosts prompts
        // For production security, fingerprint pinning or known_hosts file can be injected here.
        session?.setConfig("StrictHostKeyChecking", "no")
    }

    override fun createDefaultJSch(fs: FS?): JSch {
        val jsch = super.createDefaultJSch(fs)
        val privateKeyBytes = privateKeyContent.toByteArray(Charsets.UTF_8)
        val passphraseBytes = passphrase?.toByteArray(Charsets.UTF_8)

        jsch.removeAllIdentity()
        jsch.addIdentity(
            "passage-id",
            privateKeyBytes,
            null, // Public key is derived by JSch
            passphraseBytes
        )

        return jsch
    }
}
