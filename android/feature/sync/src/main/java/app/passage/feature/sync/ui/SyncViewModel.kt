package app.passage.feature.sync.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.passage.core.git.model.ConflictStrategy
import app.passage.core.git.model.GitAuth
import app.passage.core.git.model.GitRepoStatus
import app.passage.core.git.model.SyncResult
import app.passage.core.git.ssh.GeneratedSshKeyPair
import app.passage.core.git.ssh.SshKeyManager
import app.passage.core.git.sync.GitSyncEngine
import app.passage.core.security.SecureClipboardManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class SyncUiOperation {
    data object Idle : SyncUiOperation()
    data class Cloning(val task: String, val percent: Float) : SyncUiOperation()
    data object Pulling : SyncUiOperation()
    data object Pushing : SyncUiOperation()
    data class Success(val message: String) : SyncUiOperation()
    data class Error(val message: String) : SyncUiOperation()
    data class Conflict(val conflictedFiles: List<String>) : SyncUiOperation()
}

class SyncViewModel(
    private val gitSyncEngine: GitSyncEngine,
    private val clipboardManager: SecureClipboardManager,
    private val vaultDir: File,
    private val sshKeyFile: File
) : ViewModel() {

    val remoteUrl = MutableStateFlow("")
    val sshPublicKey = MutableStateFlow("")
    private var cachedKeyPair: GeneratedSshKeyPair? = null

    private val _operation = MutableStateFlow<SyncUiOperation>(SyncUiOperation.Idle)
    val operation: StateFlow<SyncUiOperation> = _operation.asStateFlow()

    private val _repoStatus = MutableStateFlow<GitRepoStatus?>(null)
    val repoStatus: StateFlow<GitRepoStatus?> = _repoStatus.asStateFlow()

    val isCloned: Boolean
        get() = File(vaultDir, ".git").exists()

    init {
        loadOrCreateSshKey()
        refreshStatus()
    }

    fun loadOrCreateSshKey() {
        // Keys JSch cannot parse (saved by versions before 1.0.2) are replaced by a fresh one
        val stored = if (sshKeyFile.exists()) {
            SshKeyManager.loadKeyPair(sshKeyFile.readText(Charsets.UTF_8))
        } else {
            null
        }
        if (stored != null) {
            cachedKeyPair = stored
            sshPublicKey.value = stored.publicKeyOpenSsh
        } else {
            generateNewSshKey()
        }
    }

    fun generateNewSshKey() {
        val newKey = SshKeyManager.generateKeyPair()
        cachedKeyPair = newKey
        sshPublicKey.value = newKey.publicKeyOpenSsh
        sshKeyFile.parentFile?.mkdirs()
        sshKeyFile.writeText(newKey.privateKeyPem, Charsets.UTF_8)
    }

    fun copySshPublicKey() {
        if (sshPublicKey.value.isNotEmpty()) {
            clipboardManager.copySensitiveText("Chave Pública SSH", sshPublicKey.value, autoClearSeconds = 120)
        }
    }

    fun refreshStatus() {
        if (!isCloned) return
        viewModelScope.launch {
            gitSyncEngine.getStatus(vaultDir).onSuccess {
                _repoStatus.value = it
            }
        }
    }

    fun cloneVault(url: String, onSuccess: () -> Unit) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty()) {
            _operation.value = SyncUiOperation.Error("A URL do repositório Git é obrigatória")
            return
        }

        viewModelScope.launch {
            _operation.value = SyncUiOperation.Cloning("Iniciando clone...", 0f)
            val auth = getAuth()

            gitSyncEngine.clone(
                remoteUrl = trimmedUrl,
                destinationDir = vaultDir,
                auth = auth,
                onProgress = { task, percent ->
                    _operation.value = SyncUiOperation.Cloning(task, percent)
                }
            ).fold(
                onSuccess = {
                    _operation.value = SyncUiOperation.Success("Repositório clonado com sucesso!")
                    refreshStatus()
                    onSuccess()
                },
                onFailure = { error ->
                    _operation.value = SyncUiOperation.Error(error.message ?: "Erro ao clonar repositório")
                }
            )
        }
    }

    fun pull() {
        if (!isCloned) return
        viewModelScope.launch {
            _operation.value = SyncUiOperation.Pulling
            val result = gitSyncEngine.pull(vaultDir, getAuth())
            handleSyncResult(result)
        }
    }

    fun push() {
        if (!isCloned) return
        viewModelScope.launch {
            _operation.value = SyncUiOperation.Pushing
            val result = gitSyncEngine.commitAndPush(
                repoDir = vaultDir,
                message = "Sync from Passage Android",
                auth = getAuth()
            )
            handleSyncResult(result)
        }
    }

    fun resolveConflict(relativePath: String, strategy: ConflictStrategy) {
        viewModelScope.launch {
            gitSyncEngine.resolveConflict(vaultDir, relativePath, strategy).onSuccess {
                refreshStatus()
                _operation.value = SyncUiOperation.Success("Conflito em $relativePath resolvido")
            }
        }
    }

    private fun handleSyncResult(result: SyncResult) {
        when (result) {
            is SyncResult.Success -> {
                _operation.value = SyncUiOperation.Success(result.message)
                refreshStatus()
            }
            is SyncResult.HasConflicts -> {
                _operation.value = SyncUiOperation.Conflict(result.conflictedFiles)
                refreshStatus()
            }
            is SyncResult.Failure -> {
                _operation.value = SyncUiOperation.Error(result.error.message ?: "Falha na sincronização")
            }
        }
    }

    private fun getAuth(): GitAuth {
        val pem = cachedKeyPair?.privateKeyPem
            ?: if (sshKeyFile.exists()) sshKeyFile.readText() else ""
        return if (pem.isNotEmpty()) {
            GitAuth.SshKey(privateKeyContent = pem)
        } else {
            GitAuth.None
        }
    }
}
