use std::collections::HashMap;

/// A parsed secret in gopass format.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct GopassSecret {
    /// The primary password (first line of the secret file)
    pub password: String,
    /// Key-value attributes (e.g. "login", "url", "otpauth", "recovery_keys")
    pub attributes: HashMap<String, String>,
    /// Freeform notes or comments
    pub notes: String,
}

impl GopassSecret {
    pub fn new(password: String) -> Self {
        Self {
            password,
            attributes: HashMap::new(),
            notes: String::new(),
        }
    }
}

/// Parses raw decrypted text into a structured GopassSecret.
pub fn parse_gopass_secret_internal(raw: &str) -> GopassSecret {
    let normalized = raw.replace("\r\n", "\n");
    let mut lines = normalized.lines();

    // Line 1 is the password
    let password = lines.next().unwrap_or("").to_string();

    let mut attributes = HashMap::new();
    let mut notes_lines: Vec<&str> = Vec::new();
    let mut in_notes_section = false;

    for line in lines {
        if in_notes_section {
            notes_lines.push(line);
            continue;
        }

        // Check for common gopass delimiter "--"
        if line.trim() == "--" {
            in_notes_section = true;
            continue;
        }

        // Try parsing "key: value"
        if let Some((key, value)) = line.split_once(':') {
            let trimmed_key = key.trim();
            // Validate key looks like an attribute identifier (no spaces in standard keys)
            if !trimmed_key.is_empty() && !trimmed_key.contains(' ') {
                attributes.insert(trimmed_key.to_string(), value.trim().to_string());
                continue;
            }
        }

        // Otherwise, it's part of notes
        notes_lines.push(line);
    }

    let notes = notes_lines.join("\n").trim().to_string();

    GopassSecret {
        password,
        attributes,
        notes,
    }
}

/// Serializes a GopassSecret back into standard gopass text format.
pub fn serialize_gopass_secret_internal(secret: &GopassSecret) -> String {
    let mut output = String::new();
    output.push_str(&secret.password);
    output.push('\n');

    // Sort keys for deterministic output
    let mut keys: Vec<&String> = secret.attributes.keys().collect();
    keys.sort();

    for key in keys {
        if let Some(val) = secret.attributes.get(key) {
            output.push_str(key);
            output.push_str(": ");
            output.push_str(val);
            output.push('\n');
        }
    }

    if !secret.notes.is_empty() {
        if !secret.attributes.is_empty() {
            output.push_str("--\n");
        }
        output.push_str(&secret.notes);
        output.push('\n');
    }

    output
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_and_serialize_gopass_secret() {
        let raw = "SecretP@ssw0rd!123\nlogin: admin\nurl: https://example.com\n--\nImportant account note";
        let parsed = parse_gopass_secret_internal(raw);

        assert_eq!(parsed.password, "SecretP@ssw0rd!123");
        assert_eq!(parsed.attributes.get("login").unwrap(), "admin");
        assert_eq!(parsed.attributes.get("url").unwrap(), "https://example.com");
        assert_eq!(parsed.notes, "Important account note");

        let serialized = serialize_gopass_secret_internal(&parsed);
        let reparsed = parse_gopass_secret_internal(&serialized);
        assert_eq!(parsed, reparsed);
    }

    #[test]
    fn test_parse_simple_password_only() {
        let raw = "just_a_simple_password";
        let parsed = parse_gopass_secret_internal(raw);

        assert_eq!(parsed.password, "just_a_simple_password");
        assert!(parsed.attributes.is_empty());
        assert!(parsed.notes.is_empty());
    }
}
