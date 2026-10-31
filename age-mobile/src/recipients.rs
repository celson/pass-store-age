use crate::error::AgeError;
use std::str::FromStr;

/// Parses a single recipient string into an age Recipient trait object.
/// Supports both native X25519 age recipients (age1...) and SSH public keys (ssh-ed25519, ssh-rsa).
pub fn parse_recipient(s: &str) -> Result<Box<dyn age::Recipient + Send>, AgeError> {
    let trimmed = s.trim();

    if trimmed.is_empty() {
        return Err(AgeError::InvalidRecipient {
            message: "Recipient cannot be empty".to_string(),
        });
    }

    if trimmed.starts_with("age1") {
        match age::x25519::Recipient::from_str(trimmed) {
            Ok(rec) => Ok(Box::new(rec)),
            Err(e) => Err(AgeError::InvalidRecipient {
                message: format!("Invalid age X25519 recipient: {}", e),
            }),
        }
    } else if trimmed.starts_with("ssh-") {
        match age::ssh::Recipient::from_str(trimmed) {
            Ok(rec) => Ok(Box::new(rec)),
            Err(e) => Err(AgeError::InvalidRecipient {
                message: format!("Invalid SSH recipient: {:?}", e),
            }),
        }
    } else {
        // Fallback: try parsing as X25519 first, then SSH
        if let Ok(rec) = age::x25519::Recipient::from_str(trimmed) {
            return Ok(Box::new(rec));
        }
        if let Ok(rec) = age::ssh::Recipient::from_str(trimmed) {
            return Ok(Box::new(rec));
        }
        Err(AgeError::InvalidRecipient {
            message: format!("Unsupported recipient format: {}", trimmed),
        })
    }
}

/// Parses a `.age-recipients` or `.gopass-recipients` file.
/// Strips comments (#), ignores blank lines, and extracts all clean recipient strings.
pub fn parse_recipients_file_internal(content: &str) -> Vec<String> {
    content
        .lines()
        .map(|line| line.trim())
        .filter(|line| !line.is_empty() && !line.starts_with('#'))
        .map(|s| s.to_string())
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_recipients_file() {
        let file_content = r#"
        # Recipients for my personal store
        age1ql3z7hjy54pw3hyww5ayyfg7zqgvc7w3j2elw8zmrj2kg5sfn9aqmcac8p

        # Backup SSH key
        ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIAG9j1s7Z47a7k8L3fC+mQo4VqI1e5f8H4e5u6m8a9xK backup@mobile
        "#;

        let recipients = parse_recipients_file_internal(file_content);
        assert_eq!(recipients.len(), 2);
        assert_eq!(
            recipients[0],
            "age1ql3z7hjy54pw3hyww5ayyfg7zqgvc7w3j2elw8zmrj2kg5sfn9aqmcac8p"
        );
        assert!(recipients[1].starts_with("ssh-ed25519"));
    }
}
