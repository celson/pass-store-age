package app.passage.feature.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.passage.feature.editor.generator.PasswordConfig
import app.passage.feature.editor.generator.PasswordStrength
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SecretEditorScreen(
    folder: String,
    existingSecretPath: String?,
    onCloseClick: () -> Unit,
    onSavedSuccess: (savedPath: String) -> Unit,
    viewModel: SecretEditorViewModel
) {
    val name by viewModel.name.collectAsState()
    val password by viewModel.password.collectAsState()
    val attributes by viewModel.attributes.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val saveState by viewModel.saveState.collectAsState()
    val (entropy, strength) = viewModel.passwordStrength.collectAsState().value

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var isPasswordVisible by remember { mutableStateOf(false) }
    var showGeneratorSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.initialize(folder, existingSecretPath)
    }

    LaunchedEffect(saveState) {
        if (saveState is EditorSaveState.Error) {
            snackbarHostState.showSnackbar((saveState as EditorSaveState.Error).message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditMode) "Editar Segredo" else "Novo Segredo") },
                navigationIcon = {
                    IconButton(onClick = onCloseClick) {
                        Icon(Icons.Default.Close, contentDescription = "Cancelar")
                    }
                },
                actions = {
                    if (saveState is EditorSaveState.Saving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 16.dp)
                        )
                    } else {
                        Button(
                            onClick = { viewModel.save(onSavedSuccess) },
                            enabled = name.isNotBlank() && password.isNotBlank(),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Salvar")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Folder path indicator
            if (viewModel.folder.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "Pasta: ${viewModel.folder}/",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Name Field
            OutlinedTextField(
                value = name,
                onValueChange = { viewModel.name.value = it },
                label = { Text("Nome do Segredo (ex: github.com)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password Field
            OutlinedTextField(
                value = password,
                onValueChange = { viewModel.onPasswordChanged(it) },
                label = { Text("Senha") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isPasswordVisible) "Ocultar" else "Revelar"
                            )
                        }
                        IconButton(onClick = { showGeneratorSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Gerador de Senhas",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )

            // Password Strength Indicator
            if (password.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                val progress = (entropy / 100.0).coerceIn(0.1, 1.0).toFloat()
                val color = when (strength) {
                    PasswordStrength.VERY_WEAK, PasswordStrength.WEAK -> MaterialTheme.colorScheme.error
                    PasswordStrength.FAIR -> MaterialTheme.colorScheme.tertiary
                    PasswordStrength.STRONG, PasswordStrength.VERY_STRONG -> MaterialTheme.colorScheme.primary
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = color,
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = strength.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Attributes Header & Quick Add Chips
            Text(
                text = "Atributos Gopass",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AssistChip(
                    onClick = { viewModel.addAttribute("login", "") },
                    label = { Text("+ login") }
                )
                AssistChip(
                    onClick = { viewModel.addAttribute("url", "https://") },
                    label = { Text("+ url") }
                )
                AssistChip(
                    onClick = { viewModel.addAttribute("email", "") },
                    label = { Text("+ email") }
                )
                AssistChip(
                    onClick = { viewModel.addAttribute("otpauth", "otpauth://totp/") },
                    label = { Text("+ otpauth") }
                )
                AssistChip(
                    onClick = { viewModel.addAttribute("", "") },
                    label = { Text("+ Outro") },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Dynamic Attribute Rows
            attributes.forEachIndexed { index, (key, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { viewModel.updateAttribute(index, it, value) },
                        label = { Text("Chave") },
                        modifier = Modifier.weight(0.4f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = value,
                        onValueChange = { viewModel.updateAttribute(index, key, it) },
                        label = { Text("Valor") },
                        modifier = Modifier.weight(0.6f),
                        singleLine = true
                    )
                    IconButton(onClick = { viewModel.removeAttribute(index) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remover atributo",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Notes Section
            Text(
                text = "Notas / Comentários",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = notes,
                onValueChange = { viewModel.notes.value = it },
                placeholder = { Text("Instruções de recuperação, PINs, observações...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Password Generator Modal BottomSheet
    if (showGeneratorSheet) {
        PasswordGeneratorBottomSheet(
            onDismiss = { showGeneratorSheet = false },
            onApplyPassword = { generated ->
                viewModel.onPasswordChanged(generated)
                showGeneratorSheet = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordGeneratorBottomSheet(
    onDismiss: () -> Unit,
    onApplyPassword: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var length by remember { mutableFloatStateOf(20f) }
    var uppercase by remember { mutableStateOf(true) }
    var lowercase by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var avoidAmbiguous by remember { mutableStateOf(true) }

    fun buildConfig() = PasswordConfig(
        length = length.toInt(),
        includeUppercase = uppercase,
        includeLowercase = lowercase,
        includeDigits = digits,
        includeSymbols = symbols,
        avoidAmbiguous = avoidAmbiguous
    )

    var previewPassword by remember {
        mutableStateOf(app.passage.feature.editor.generator.PasswordGenerator.generate(buildConfig()))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Gerador de Senha Forte",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Generated Preview Box
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = previewPassword,
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        previewPassword = app.passage.feature.editor.generator.PasswordGenerator.generate(buildConfig())
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Regerar")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Length Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Tamanho da senha:", style = MaterialTheme.typography.bodyMedium)
                Text("${length.toInt()} caracteres", fontWeight = FontWeight.Bold)
            }
            Slider(
                value = length,
                onValueChange = {
                    length = it
                    previewPassword = app.passage.feature.editor.generator.PasswordGenerator.generate(buildConfig())
                },
                valueRange = 8f..64f,
                steps = 55
            )

            // Checkbox options
            GeneratorOptionRow("Letras Maiúsculas (A-Z)", uppercase) {
                uppercase = it
                previewPassword = app.passage.feature.editor.generator.PasswordGenerator.generate(buildConfig())
            }
            GeneratorOptionRow("Letras Minúsculas (a-z)", lowercase) {
                lowercase = it
                previewPassword = app.passage.feature.editor.generator.PasswordGenerator.generate(buildConfig())
            }
            GeneratorOptionRow("Números (0-9)", digits) {
                digits = it
                previewPassword = app.passage.feature.editor.generator.PasswordGenerator.generate(buildConfig())
            }
            GeneratorOptionRow("Símbolos (!@#$...)", symbols) {
                symbols = it
                previewPassword = app.passage.feature.editor.generator.PasswordGenerator.generate(buildConfig())
            }
            GeneratorOptionRow("Evitar ambíguos (0/O, 1/l, I)", avoidAmbiguous) {
                avoidAmbiguous = it
                previewPassword = app.passage.feature.editor.generator.PasswordGenerator.generate(buildConfig())
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onApplyPassword(previewPassword) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Usar esta Senha")
            }
        }
    }
}

@Composable
fun GeneratorOptionRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
