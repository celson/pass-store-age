# age-mobile

Motor de criptografia **age** para Android com suporte a compatibilidade com o **gopass**, desenvolvido em **Rust** e exportado para **Kotlin** via **UniFFI**.

---

## 🚀 Funcionalidades

- **Criptografia & Descriptografia age:**
  - Suporte completo ao formato nativo do `age` (v1) e **ASCII-Armored** (`-----BEGIN AGE ENCRYPTED FILE-----`).
  - Suporte a múltiplos destinatários (*recipients*) em uma única criptografia.
  - Suporte a descriptografia tanto via chaves privadas (*identities*) quanto via *passphrase* (scrypt).
- **Destinatários (Recipients) Suportados:**
  - Chaves nativas X25519 do age (`age1...`).
  - Chaves públicas SSH (`ssh-ed25519 ...` e `ssh-rsa ...`).
  - Parser de arquivos `.age-recipients` e `.gopass-recipients` (ignora comentários `#` e espaços).
- **Identidades (Chaves Privadas):**
  - Chaves nativas X25519 (`AGE-SECRET-KEY-1...`).
  - Geração local de novos pares de chaves X25519.
  - Derivação da chave pública (`age1...`) a partir da chave privada.
  - Chaves privadas SSH (com suporte a passphrase).
- **Compatibilidade Gopass:**
  - `parse_gopass_secret`: Converte o texto descriptografado em um objeto estruturado contendo a senha primária (linha 1), atributos chave-valor (`login`, `url`, `otpauth`) e notas livres.
  - `serialize_gopass_secret`: Serializa o objeto estruturado de volta ao formato padrão do gopass de forma determinística.

---

## 📦 Estrutura do Módulo

```text
age-mobile/
├── Cargo.toml            # Dependências (age, uniffi, thiserror, zeroize)
├── uniffi.toml           # Configuração de pacote Kotlin (app.passage.crypto)
├── build-android.sh      # Script de compilação NDK para arm64, armeabi, x86_64
├── src/
│   ├── lib.rs            # Exports UniFFI e testes de integração
│   ├── engine.rs         # Lógica de criptografia e descriptografia (armored + binary)
│   ├── error.rs          # Erros tipados exportados para Kotlin como AgeException
│   ├── identities.rs     # Geração e parsing de chaves privadas age e SSH
│   ├── recipients.rs     # Parsing de chaves públicas e arquivos .age-recipients
│   ├── gopass.rs         # Parser e serializador de formato de segredos do gopass
│   └── bin/
│       └── uniffi-bindgen.rs # Gerador de bindings para linguagens externas
└── generated/kotlin/     # Código Kotlin gerado pelo UniFFI
```

---

## 🧪 Testes Automatizados

O motor possui testes unitários completos que rodam diretamente na máquina host via Cargo:

```bash
cargo test
```

Testes cobertos:
1. `test_full_roundtrip_encryption_decryption_armored`: Criptografia com múltiplos destinatários em ASCII armor e descriptografia individual.
2. `test_full_roundtrip_binary`: Criptografia e descriptografia de payloads binários.
3. `test_gopass_parser_and_serializer_roundtrip`: Parsing e serialização de segredos do gopass preservando campos e notas.
4. `test_recipients_file_parsing`: Extração correta de chaves de arquivos `.age-recipients` com comentários e linhas vazias.
5. `test_invalid_key_error_handling`: Tratamento e tipagem de erros em chaves inválidas.

---

## 📱 Uso no Kotlin (Android)

```kotlin
import app.passage.crypto.*

// 1. Gerar novo par de chaves age no dispositivo
val keypair = generateKeypair()
println("Sua chave pública (recipient): ${keypair.publicRecipient}")
// Guardar keypair.secretIdentity com segurança no Android Keystore!

// 2. Criptografar um segredo no formato gopass
val secret = GopassSecret(
    password = "SuperSafePassword123!",
    attributes = mapOf("login" to "user@domain.com", "url" to "https://github.com"),
    notes = "Backup codes: 1234, 5678"
)
val rawText = serializeGopassSecret(secret)

val recipients = listOf(keypair.publicRecipient)
val encryptedBytes = encryptBytes(
    plaintext = rawText.toByteArray(Charsets.UTF_8),
    recipients = recipients,
    armor = true // Salva como .age compatível com gopass
)

// 3. Descriptografar e ler os campos
val decryptedBytes = decryptBytes(
    ciphertext = encryptedBytes,
    identities = listOf(keypair.secretIdentity),
    passphrase = null
)
val parsedSecret = parseGopassSecret(decryptedBytes.toString(Charsets.UTF_8))
println("Senha: ${parsedSecret.password}")
println("Login: ${parsedSecret.attributes["login"]}")
```
