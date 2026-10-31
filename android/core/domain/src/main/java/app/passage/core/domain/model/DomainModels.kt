package app.passage.core.domain.model

data class SecretItem(
    val name: String,
    val relativePath: String,
    val parentFolder: String,
    val lastModifiedMs: Long
)

data class SecretDetail(
    val name: String,
    val relativePath: String,
    val password: String,
    val attributes: Map<String, String>,
    val notes: String,
    val totpCode: String? = null
)

data class VaultFolder(
    val name: String,
    val relativePath: String,
    val subfolders: List<VaultFolder>,
    val secrets: List<SecretItem>
)

data class UserIdentity(
    val id: String,
    val label: String,
    val publicRecipient: String,
    val isDefault: Boolean
)
