package app.passage.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Keeps the age secret keys (`AGE-SECRET-KEY-1...`) encrypted at rest with an Android Keystore master key.
 * The passphrase that protected the original identities file is never stored.
 */
class IdentityStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getIdentities(): List<String> =
        prefs.getString(KEY_IDENTITIES, null)
            ?.lineSequence()
            ?.filter { it.isNotBlank() }
            ?.toList()
            .orEmpty()

    fun saveIdentities(identities: List<String>) {
        prefs.edit().putString(KEY_IDENTITIES, identities.joinToString("\n")).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_IDENTITIES).apply()
    }

    private companion object {
        const val FILE_NAME = "passage_identities"
        const val KEY_IDENTITIES = "age_identities"
    }
}
