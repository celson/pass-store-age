package app.passage.core.git.model

import java.io.File

/**
 * Authentication methods supported for Git operations.
 */
sealed class GitAuth {
    /**
     * SSH key authentication with optional passphrase.
     * @param privateKeyContent PEM or OpenSSH format private key string.
     * @param passphrase Optional passphrase if the private key is encrypted.
     */
    data class SshKey(
        val privateKeyContent: String,
        val passphrase: String? = null
    ) : GitAuth()

    /**
     * SSH key authentication using a key file stored on disk.
     */
    data class SshKeyFile(
        val keyFile: File,
        val passphrase: String? = null
    ) : GitAuth()

    /**
     * HTTPS authentication using Personal Access Token (PAT) or username/password.
     */
    data class HttpToken(
        val username: String = "git",
        val token: String
    ) : GitAuth()

    /**
     * Anonymous access (read-only for public repositories).
     */
    data object None : GitAuth()
}
