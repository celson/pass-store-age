package app.passage.core.domain.repository

import app.passage.core.domain.model.SecretDetail
import app.passage.core.domain.model.SecretItem
import app.passage.core.domain.model.UserIdentity
import app.passage.core.domain.model.VaultFolder
import app.passage.core.git.model.GitAuth
import app.passage.core.git.model.GitRepoStatus
import app.passage.core.git.model.SyncResult
import kotlinx.coroutines.flow.Flow

interface SecretRepository {
    fun getVaultTree(): Flow<VaultFolder>
    suspend fun getSecretDetail(relativePath: String): Result<SecretDetail>
    suspend fun saveSecret(
        relativePath: String,
        secret: SecretDetail,
        recipientsOverride: List<String>? = null
    ): Result<Unit>
    suspend fun deleteSecret(relativePath: String): Result<Unit>
    suspend fun searchSecrets(query: String): List<SecretItem>
}

interface GitSyncRepository {
    val syncState: Flow<SyncResult>
    suspend fun cloneVault(remoteUrl: String, auth: GitAuth): Result<Unit>
    suspend fun pullVault(auth: GitAuth): SyncResult
    suspend fun pushVault(auth: GitAuth): SyncResult
    suspend fun getStatus(): Result<GitRepoStatus>
}

interface IdentityRepository {
    fun getIdentities(): Flow<List<UserIdentity>>
    suspend fun getActiveIdentitySecretKey(): Result<String>
    suspend fun saveIdentity(label: String, secretKey: String): Result<UserIdentity>
    suspend fun deleteIdentity(id: String): Result<Unit>
}
