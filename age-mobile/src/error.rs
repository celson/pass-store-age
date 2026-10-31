use thiserror::Error;

#[derive(Debug, Error, uniffi::Error)]
pub enum AgeError {
    #[error("Invalid identity key: {message}")]
    InvalidIdentity { message: String },

    #[error("Invalid recipient: {message}")]
    InvalidRecipient { message: String },

    #[error("No recipients specified for encryption")]
    NoRecipients,

    #[error("No identities specified for decryption")]
    NoIdentities,

    #[error("Encryption failed: {message}")]
    EncryptionFailed { message: String },

    #[error("Decryption failed: {message}")]
    DecryptionFailed { message: String },

    #[error("Passphrase required or invalid")]
    PassphraseRequired,

    #[error("Failed to parse gopass secret: {message}")]
    GopassParseError { message: String },

    #[error("IO error: {message}")]
    IoError { message: String },
}

impl From<std::io::Error> for AgeError {
    fn from(err: std::io::Error) -> Self {
        AgeError::IoError {
            message: err.to_string(),
        }
    }
}
