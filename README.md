<p align="center">
  <img src="assets/icon.svg" alt="Passage" width="128" height="128">
</p>

# Passage: Gopass & age Password Store para Android

[![Build](https://img.shields.io/github/actions/workflow/status/celson/pass-store-age/build.yml?branch=main&logo=github&label=build)](https://github.com/celson/pass-store-age/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/celson/pass-store-age?logo=github&label=release)](https://github.com/celson/pass-store-age/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/celson/pass-store-age/total?logo=android&label=downloads)](https://github.com/celson/pass-store-age/releases)
[![License: MIT](https://img.shields.io/github/license/celson/pass-store-age)](LICENSE)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)
![Rust](https://img.shields.io/badge/Rust-age%20engine-000000?logo=rust&logoColor=white)
![gopass compatible](https://img.shields.io/badge/gopass-compat%C3%ADvel-0E7C86)

Aplicativo moderno para Android que lê, gerencia e sincroniza segredos compatíveis com o **gopass** criptografados com o **age** em repositórios **Git** remotos.

---

## 🏗️ Arquitetura do Sistema

```text
├── age-mobile/                          # Motor criptográfico nativo em Rust (UniFFI)
│   ├── src/                             # Criptografia age (ASCII/binário), gopass parser, chaves SSH/X25519
│   ├── build-android.sh                 # Compilação cross-target para Android ABIs
│   └── generated/kotlin/                # Código Kotlin gerado pelo UniFFI (app.passage.crypto)
│
└── android/                             # Projeto Android Gradle Multi-Módulo
    ├── app/                             # Application, MainActivity (FLAG_SECURE), DI (Koin), Navigation
    ├── core/
    │   ├── crypto/                      # Facade Kotlin com JNI e bindings do age-mobile
    │   ├── git/                         # Engine Git (JGit), SSH Keypair Manager, Resolução de Conflitos
    │   ├── domain/                      # Modelos de Domínio, UseCases e Contratos de Repositório
    │   ├── security/                    # Keystore, Biometria, Clipboard Auto-Clear e TOTP (RFC 6238)
    │   └── database/                    # Room Database (índice local de metadados para busca rápida)
    └── feature/
        ├── vault/                       # Interface Compose: Árvore de pastas, Busca e FAB
        ├── secret-detail/               # Interface: Revelar/Copiar senha, Atributos, Notas e Card 2FA/TOTP
        ├── secret-editor/               # Criação/Edição de segredos + Gerador de Senhas com Entropia
        ├── sync/                        # Dashboard Git, Exportação de chave SSH pública e Conflitos
        └── autofill/                    # Android Autofill Service (preenchimento automático no Chrome/Apps)
```

---

## 🔒 Recursos de Segurança e Usabilidade

1. **Zero-Knowledge Local:** Nenhum segredo é gravado descriptografado em disco. O clone Git mantém os arquivos `.age` intactos.
2. **Proteção de Janela (`FLAG_SECURE`):** Impede screenshots, gravação de tela e esconde dados confidenciais no alternador de apps do Android.
3. **Área de Transferência Segura:** Cópias de senha utilizam `EXTRA_IS_SENSITIVE` (Android 13+) e são automaticamente apagadas após 30 segundos.
4. **Gerador de Tokens 2FA / TOTP Integrado:**
   - Detecta atributos `otpauth: otpauth://totp/...` compatíveis com o gopass.
   - Gera códigos de 6 dígitos em tempo real com anel circular de contagem regressiva de 30 segundos e botão de cópia rápida.
5. **Serviço de Preenchimento Automático (Android Autofill Framework):**
   - Intercepta acessos a sites no Chrome/navegadores e formulários de login em aplicativos nativos.
   - Correlaciona o domínio web ou nome do pacote com o índice local e preenche usuário e senha com um toque.
6. **Gerador de Senhas Fortes:**
   - Tamanho configurável (8 a 64 caracteres), filtros para maiúsculas, minúsculas, dígitos e símbolos, com opção de evitar caracteres ambíguos (`0/O`, `1/l/I`).
   - Cálculo em tempo real da entropia em bits e classificação de força.

---

## ⚡ Como Compilar

### 1. Testar e Compilar o Motor `age-mobile` (Rust)
```bash
cd age-mobile
cargo test
./build-android.sh
```

### 2. Abrir o Projeto Android
Abra a pasta `android/` no **Android Studio** (Koala / Ladybug ou superior). O Gradle sincronizará automaticamente todas as dependências catalogadas em `gradle/libs.versions.toml`.
