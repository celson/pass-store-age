use thiserror::Error;

#[derive(Debug, Error, uniffi::Error)]
pub enum AgeError {
    #[error("Invalid identity key: {reason}")]
    InvalidIdentity { reason: String },

    #[error("Invalid recipient: {reason}")]
    InvalidRecipient { reason: String },

    #[error("No recipients specified for encryption")]
    NoRecipients,

    #[error("No identities specified for decryption")]
    NoIdentities,

    #[error("Encryption failed: {reason}")]
    EncryptionFailed { reason: String },

    #[error("Decryption failed: {reason}")]
    DecryptionFailed { reason: String },

    #[error("Passphrase required or invalid")]
    PassphraseRequired,

    #[error("Failed to parse gopass secret: {reason}")]
    GopassParseError { reason: String },

    #[error("IO error: {reason}")]
    IoError { reason: String },
}

impl From<std::io::Error> for AgeError {
    fn from(err: std::io::Error) -> Self {
        AgeError::IoError {
            reason: err.to_string(),
        }
    }
}
