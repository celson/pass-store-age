package app.passage.feature.detail.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.passage.core.domain.model.SecretDetail
import app.passage.core.domain.usecase.DeleteSecretUseCase
import app.passage.core.domain.usecase.GetSecretDetailUseCase
import app.passage.core.security.SecureClipboardManager
import app.passage.core.security.totp.TotpGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SecretDetailUiState {
    data object Loading : SecretDetailUiState()
    data class Success(val secret: SecretDetail) : SecretDetailUiState()
    data class Error(val message: String) : SecretDetailUiState()
}

class SecretDetailViewModel(
    private val getSecretDetailUseCase: GetSecretDetailUseCase,
    private val deleteSecretUseCase: DeleteSecretUseCase,
    private val clipboardManager: SecureClipboardManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<SecretDetailUiState>(SecretDetailUiState.Loading)
    val uiState: StateFlow<SecretDetailUiState> = _uiState.asStateFlow()

    fun loadSecret(relativePath: String) {
        viewModelScope.launch {
            _uiState.value = SecretDetailUiState.Loading
            getSecretDetailUseCase(relativePath).fold(
                onSuccess = { secret ->
                    _uiState.value = SecretDetailUiState.Success(secret)
                },
                onFailure = { error ->
                    _uiState.value = SecretDetailUiState.Error(error.message ?: "Falha ao descriptografar segredo")
                }
            )
        }
    }

    fun copyPassword(password: String) {
        clipboardManager.copySensitiveText(
            label = "Senha",
            sensitiveText = password,
            autoClearSeconds = 30
        )
    }

    fun copyAttribute(label: String, value: String) {
        clipboardManager.copySensitiveText(
            label = label,
            sensitiveText = value,
            autoClearSeconds = 60
        )
    }

    fun deleteSecret(relativePath: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            deleteSecretUseCase(relativePath).onSuccess {
                onDeleted()
            }
        }
    }
}

/** Fixed-length mask, so a hidden value does not leak its length. */
private const val MASKED_VALUE = "••••••••••••"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecretDetailScreen(
    relativePath: String,
    onBackClick: () -> Unit,
    onEditClick: (relativePath: String) -> Unit,
    viewModel: SecretDetailViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var isPasswordVisible by remember { mutableStateOf(false) }
    var revealedAttributes by remember { mutableStateOf(emptySet<String>()) }
    var areNotesVisible by remember { mutableStateOf(false) }

    LaunchedEffect(relativePath) {
        viewModel.loadSecret(relativePath)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(relativePath.substringAfterLast("/").removeSuffix(".age")) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { onEditClick(relativePath) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar")
                    }
                    IconButton(onClick = {
                        viewModel.deleteSecret(relativePath, onDeleted = onBackClick)
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Excluir")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is SecretDetailUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is SecretDetailUiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is SecretDetailUiState.Success -> {
                    val secret = state.secret
                    val otpEntry = secret.attributes.entries.firstOrNull {
                        it.key.equals("otpauth", ignoreCase = true) || it.value.startsWith("otpauth://", ignoreCase = true)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SecretFieldCard(
                            label = "Senha",
                            contentName = "Senha",
                            value = secret.password,
                            isRevealed = isPasswordVisible,
                            onToggleReveal = { isPasswordVisible = !isPasswordVisible },
                            onCopy = {
                                viewModel.copyPassword(secret.password)
                                scope.launch { snackbarHostState.showSnackbar("Senha copiada! Limpeza em 30s") }
                            },
                            valueStyle = MaterialTheme.typography.titleLarge,
                            monospace = true
                        )

                        if (otpEntry != null) {
                            TotpCard(
                                otpAuthUri = otpEntry.value,
                                onCopyTotp = { code ->
                                    viewModel.copyPassword(code)
                                    scope.launch { snackbarHostState.showSnackbar("Código 2FA copiado! Limpeza em 30s") }
                                }
                            )
                        }

                        // Attributes (login, url, etc.)
                        secret.attributes.filter { it.key != otpEntry?.key }.forEach { (key, value) ->
                            SecretFieldCard(
                                label = key.replaceFirstChar { it.uppercase() },
                                contentName = key,
                                value = value,
                                isRevealed = key in revealedAttributes,
                                onToggleReveal = {
                                    revealedAttributes = if (key in revealedAttributes) {
                                        revealedAttributes - key
                                    } else {
                                        revealedAttributes + key
                                    }
                                },
                                onCopy = {
                                    viewModel.copyAttribute(key, value)
                                    scope.launch { snackbarHostState.showSnackbar("$key copiado!") }
                                }
                            )
                        }

                        if (secret.notes.isNotEmpty()) {
                            SecretFieldCard(
                                label = "Notas",
                                contentName = "notas",
                                value = secret.notes,
                                isRevealed = areNotesVisible,
                                onToggleReveal = { areNotesVisible = !areNotesVisible },
                                onCopy = {
                                    viewModel.copyAttribute("Notas", secret.notes)
                                    scope.launch { snackbarHostState.showSnackbar("Notas copiadas!") }
                                },
                                valueStyle = MaterialTheme.typography.bodyMedium,
                                multiline = true
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One secret value (password, attribute, notes) in a card: masked by default, with the
 * reveal and copy buttons inside the card. Every field uses this so they all look the same.
 */
@Composable
private fun SecretFieldCard(
    label: String,
    contentName: String,
    value: String,
    isRevealed: Boolean,
    onToggleReveal: () -> Unit,
    onCopy: () -> Unit,
    valueStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    monospace: Boolean = false,
    multiline: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically
            ) {
                Text(
                    text = if (isRevealed) value else MASKED_VALUE,
                    style = valueStyle,
                    fontFamily = if (monospace) FontFamily.Monospace else null,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = if (multiline) 12.dp else 0.dp)
                )
                IconButton(onClick = onToggleReveal) {
                    Icon(
                        imageVector = if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (isRevealed) "Ocultar $contentName" else "Revelar $contentName"
                    )
                }
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar $contentName")
                }
            }
        }
    }
}

@Composable
fun TotpCard(
    otpAuthUri: String,
    onCopyTotp: (String) -> Unit
) {
    val config = remember(otpAuthUri) { TotpGenerator.parseUri(otpAuthUri) } ?: return

    var currentCode by remember { mutableStateOf(TotpGenerator.generateCode(config)) }
    var remainingSeconds by remember { mutableStateOf(TotpGenerator.getRemainingSeconds(config.periodSeconds)) }
    var progress by remember { mutableFloatStateOf(TotpGenerator.getProgress(config.periodSeconds)) }
    var isCodeVisible by remember { mutableStateOf(false) }

    LaunchedEffect(config) {
        while (true) {
            val now = System.currentTimeMillis()
            currentCode = TotpGenerator.generateCode(config, now)
            remainingSeconds = TotpGenerator.getRemainingSeconds(config.periodSeconds, now)
            progress = TotpGenerator.getProgress(config.periodSeconds, now)
            delay(500)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(46.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp
                )
                Text(
                    text = "${remainingSeconds}s",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Código 2FA / TOTP" + (config.issuer?.let { " ($it)" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                val formatted = if (currentCode.length == 6) {
                    "${currentCode.substring(0, 3)} ${currentCode.substring(3)}"
                } else currentCode
                Text(
                    text = if (isCodeVisible) formatted else "••• •••",
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { isCodeVisible = !isCodeVisible }) {
                Icon(
                    imageVector = if (isCodeVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (isCodeVisible) "Ocultar Código 2FA" else "Revelar Código 2FA"
                )
            }
            IconButton(onClick = { onCopyTotp(currentCode) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copiar Código 2FA")
            }
        }
    }
}
