package app.passage.feature.vault.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.passage.core.domain.model.SecretItem
import app.passage.core.domain.model.VaultFolder
import app.passage.core.domain.usecase.GetVaultTreeUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class VaultUiState {
    data object Loading : VaultUiState()
    data class Success(val rootFolder: VaultFolder) : VaultUiState()
    data class Error(val message: String) : VaultUiState()
}

class VaultViewModel(
    private val getVaultTreeUseCase: GetVaultTreeUseCase
) : ViewModel() {

    val uiState: StateFlow<VaultUiState> = getVaultTreeUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = VaultUiState.Loading
        ).let { flow ->
            val mapped = MutableStateFlow<VaultUiState>(VaultUiState.Loading)
            viewModelScope.launch {
                flow.collect { folder ->
                    mapped.value = VaultUiState.Success(folder)
                }
            }
            mapped.asStateFlow()
        }

    private val _currentPath = MutableStateFlow<List<String>>(emptyList())
    val currentPath: StateFlow<List<String>> = _currentPath.asStateFlow()

    fun navigateToFolder(folderName: String) {
        _currentPath.value = _currentPath.value + folderName
    }

    fun navigateBack(): Boolean {
        if (_currentPath.value.isNotEmpty()) {
            _currentPath.value = _currentPath.value.dropLast(1)
            return true
        }
        return false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onSecretClick: (relativePath: String) -> Unit,
    onAddSecretClick: (currentFolder: String) -> Unit,
    onSyncClick: () -> Unit,
    viewModel: VaultViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar segredos...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    } else {
                        Text(if (currentPath.isEmpty()) "Passage Vault" else currentPath.joinToString(" / "))
                    }
                },
                navigationIcon = {
                    if (currentPath.isNotEmpty()) {
                        IconButton(onClick = { viewModel.navigateBack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { isSearching = !isSearching }) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar")
                    }
                    IconButton(onClick = onSyncClick) {
                        Icon(Icons.Default.Sync, contentDescription = "Sincronizar Git")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAddSecretClick(currentPath.joinToString("/")) }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Novo Segredo")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is VaultUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is VaultUiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is VaultUiState.Success -> {
                    // Navigate to current folder in the tree
                    var activeFolder = state.rootFolder
                    for (segment in currentPath) {
                        activeFolder = activeFolder.subfolders.find { it.name == segment } ?: activeFolder
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        // Subfolders
                        items(activeFolder.subfolders) { subfolder ->
                            FolderRow(
                                folder = subfolder,
                                onClick = { viewModel.navigateToFolder(subfolder.name) }
                            )
                        }

                        // Secrets
                        items(activeFolder.secrets) { secret ->
                            SecretRow(
                                secret = secret,
                                onClick = { onSecretClick(secret.relativePath) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FolderRow(folder: VaultFolder, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = folder.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SecretRow(secret: SecretItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Key,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = secret.name,
                style = MaterialTheme.typography.bodyLarge
            )
            if (secret.parentFolder.isNotEmpty()) {
                Text(
                    text = secret.parentFolder,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
