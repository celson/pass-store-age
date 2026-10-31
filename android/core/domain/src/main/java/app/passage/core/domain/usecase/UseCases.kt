package app.passage.core.domain.usecase

import app.passage.core.domain.model.SecretDetail
import app.passage.core.domain.model.VaultFolder
import app.passage.core.domain.repository.SecretRepository
import kotlinx.coroutines.flow.Flow

class GetVaultTreeUseCase(
    private val secretRepository: SecretRepository
) {
    operator fun invoke(): Flow<VaultFolder> = secretRepository.getVaultTree()
}

class GetSecretDetailUseCase(
    private val secretRepository: SecretRepository
) {
    suspend operator fun invoke(relativePath: String): Result<SecretDetail> {
        return secretRepository.getSecretDetail(relativePath)
    }
}

class SaveSecretUseCase(
    private val secretRepository: SecretRepository
) {
    suspend operator fun invoke(
        relativePath: String,
        secret: SecretDetail,
        recipientsOverride: List<String>? = null
    ): Result<Unit> {
        return secretRepository.saveSecret(relativePath, secret, recipientsOverride)
    }
}

class DeleteSecretUseCase(
    private val secretRepository: SecretRepository
) {
    suspend operator fun invoke(relativePath: String): Result<Unit> {
        return secretRepository.deleteSecret(relativePath)
    }
}
