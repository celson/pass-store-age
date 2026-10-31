package app.passage.di

import app.passage.core.database.PassageDatabase
import app.passage.core.domain.model.SecretDetail
import app.passage.core.domain.model.SecretItem
import app.passage.core.domain.model.VaultFolder
import app.passage.core.domain.repository.SecretRepository
import app.passage.core.domain.usecase.DeleteSecretUseCase
import app.passage.core.domain.usecase.GetSecretDetailUseCase
import app.passage.core.domain.usecase.GetVaultTreeUseCase
import app.passage.core.domain.usecase.SaveSecretUseCase
import app.passage.core.git.sync.GitSyncEngine
import app.passage.core.git.tree.GopassTreeWalker
import app.passage.core.git.tree.VaultItem
import app.passage.core.security.BiometricAuthManager
import app.passage.core.security.IdentityStore
import app.passage.core.security.SecureClipboardManager
import app.passage.crypto.AgeCryptoManager
import app.passage.crypto.ageUserMessage
import app.passage.crypto.GopassSecret
import app.passage.feature.detail.ui.SecretDetailViewModel
import app.passage.feature.editor.ui.SecretEditorViewModel
import app.passage.feature.sync.ui.SyncViewModel
import app.passage.feature.vault.ui.VaultViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import java.io.File

val appModule = module {
    // Core Managers
    single { AgeCryptoManager() }
    single { GitSyncEngine() }
    single { GopassTreeWalker() }
    single { IdentityStore(get()) }
    single { SecureClipboardManager(get()) }
    single { BiometricAuthManager(get()) }

    // Database
    single { PassageDatabase.getInstance(get()) }
    single { get<PassageDatabase>().secretDao() }

    // Repositories
    single<SecretRepository> {
        val repoDir = File(get<android.content.Context>().filesDir, "vault")
        object : SecretRepository {
            private val walker = get<GopassTreeWalker>()
            private val crypto = get<AgeCryptoManager>()
            private val identityStore = get<IdentityStore>()

            override fun getVaultTree(): Flow<VaultFolder> = flow {
                val rootItem = walker.buildTree(repoDir)
                emit(mapToVaultFolder(rootItem))
            }

            override suspend fun getSecretDetail(relativePath: String): Result<SecretDetail> = runCatching {
                val file = File(repoDir, relativePath)
                val encryptedBytes = file.readBytes()
                val gopassSecret = crypto.decryptSecret(encryptedBytes, identityStore.getIdentities())
                    .getOrElse { throw IllegalStateException(it.ageUserMessage(), it) }

                SecretDetail(
                    name = file.name.removeSuffix(".age"),
                    relativePath = relativePath,
                    password = gopassSecret.password,
                    attributes = gopassSecret.attributes,
                    notes = gopassSecret.notes
                )
            }

            override suspend fun saveSecret(
                relativePath: String,
                secret: SecretDetail,
                recipientsOverride: List<String>?
            ): Result<Unit> = runCatching {
                val recipients = recipientsOverride ?: walker.findRecipientsForPath(repoDir, relativePath)
                val gopassSecret = GopassSecret(secret.password, secret.attributes, secret.notes)
                val encrypted = crypto.encryptSecret(gopassSecret, recipients).getOrThrow()

                val file = File(repoDir, relativePath)
                file.parentFile?.mkdirs()
                file.writeBytes(encrypted)
            }

            override suspend fun deleteSecret(relativePath: String): Result<Unit> = runCatching {
                val file = File(repoDir, relativePath)
                if (file.exists()) file.delete()
            }

            override suspend fun searchSecrets(query: String): List<SecretItem> {
                return walker.listAllSecrets(repoDir)
                    .filter { it.name.contains(query, ignoreCase = true) || it.relativePath.contains(query, ignoreCase = true) }
                    .map { SecretItem(it.name, it.relativePath, it.parentFolder, it.lastModifiedMs) }
            }

            private fun mapToVaultFolder(folderItem: VaultItem.Folder): VaultFolder {
                val subfolders = folderItem.children.filterIsInstance<VaultItem.Folder>().map { mapToVaultFolder(it) }
                val secrets = folderItem.children.filterIsInstance<VaultItem.SecretFile>().map {
                    SecretItem(it.name, it.relativePath, folderItem.relativePath, it.lastModifiedMs)
                }
                return VaultFolder(folderItem.name, folderItem.relativePath, subfolders, secrets)
            }
        }
    }

    // Use Cases
    single { GetVaultTreeUseCase(get()) }
    single { GetSecretDetailUseCase(get()) }
    single { SaveSecretUseCase(get()) }
    single { DeleteSecretUseCase(get()) }

    // ViewModels
    viewModel { VaultViewModel(get()) }
    viewModel { SecretDetailViewModel(get(), get(), get()) }
    viewModel { SecretEditorViewModel(get(), get()) }
    viewModel {
        val context = get<android.content.Context>()
        SyncViewModel(
            gitSyncEngine = get(),
            clipboardManager = get(),
            cryptoManager = get(),
            identityStore = get(),
            vaultDir = File(context.filesDir, "vault"),
            sshKeyFile = File(context.filesDir, "ssh/id_ed25519")
        )
    }
}
