package app.passage.crypto

/**
 * Message for the user: the UniFFI exceptions expose their text in `reason`, which
 * `Throwable.message` does not reliably carry (it is blank for variants without fields).
 */
fun Throwable.ageUserMessage(): String = when (this) {
    is AgeException.NoIdentities ->
        "Nenhuma chave age importada. Importe em Sincronizar → Chave age."
    is AgeException.PassphraseRequired -> "Senha ausente ou incorreta."
    is AgeException.DecryptionFailed -> "Falha ao descriptografar: $reason"
    is AgeException.InvalidIdentity -> "Chave age inválida: $reason"
    is AgeException.InvalidRecipient -> "Destinatário age inválido: $reason"
    is AgeException.NoRecipients -> "Nenhum destinatário age definido para este caminho."
    is AgeException.EncryptionFailed -> "Falha ao criptografar: $reason"
    is AgeException.GopassParseException -> "Segredo em formato inválido: $reason"
    is AgeException.IoException -> "Erro de E/S: $reason"
    else -> message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
}
