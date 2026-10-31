package app.passage.feature.editor.generator

import java.security.SecureRandom
import kotlin.math.log2

data class PasswordConfig(
    val length: Int = 20,
    val includeUppercase: Boolean = true,
    val includeLowercase: Boolean = true,
    val includeDigits: Boolean = true,
    val includeSymbols: Boolean = true,
    val avoidAmbiguous: Boolean = true
)

enum class PasswordStrength(val label: String) {
    VERY_WEAK("Muito Fraca"),
    WEAK("Fraca"),
    FAIR("Razoável"),
    STRONG("Forte"),
    VERY_STRONG("Muito Forte")
}

object PasswordGenerator {

    private val random = SecureRandom()

    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val UPPER_ALL = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val LOWER_ALL = "abcdefghijklmnopqrstuvwxyz"

    private const val DIGITS = "23456789"
    private const val DIGITS_ALL = "0123456789"

    private const val SYMBOLS = "!@#$%^&*()-_=+[]{}|;:,.<>?"

    fun generate(config: PasswordConfig): String {
        val pools = mutableListOf<String>()

        val upperSet = if (config.avoidAmbiguous) UPPER else UPPER_ALL
        val lowerSet = if (config.avoidAmbiguous) LOWER else LOWER_ALL
        val digitSet = if (config.avoidAmbiguous) DIGITS else DIGITS_ALL

        if (config.includeUppercase) pools.add(upperSet)
        if (config.includeLowercase) pools.add(lowerSet)
        if (config.includeDigits) pools.add(digitSet)
        if (config.includeSymbols) pools.add(SYMBOLS)

        if (pools.isEmpty()) return ""

        val result = mutableListOf<Char>()

        // Guarantee at least one char from each selected pool
        for (pool in pools) {
            result.add(pool[random.nextInt(pool.length)])
        }

        // Fill remaining length from combined pool
        val combined = pools.joinToString("")
        while (result.size < config.length) {
            result.add(combined[random.nextInt(combined.length)])
        }

        // Shuffle securely
        result.shuffle(random)
        return result.joinToString("")
    }

    fun calculateStrength(password: String): Pair<Double, PasswordStrength> {
        if (password.isEmpty()) return 0.0 to PasswordStrength.VERY_WEAK

        var poolSize = 0
        if (password.any { it.isUpperCase() }) poolSize += 26
        if (password.any { it.isLowerCase() }) poolSize += 26
        if (password.any { it.isDigit() }) poolSize += 10
        if (password.any { !it.isLetterOrDigit() }) poolSize += 32

        if (poolSize == 0) return 0.0 to PasswordStrength.VERY_WEAK

        val entropy = password.length * log2(poolSize.toDouble())

        val strength = when {
            entropy < 35 -> PasswordStrength.VERY_WEAK
            entropy < 50 -> PasswordStrength.WEAK
            entropy < 65 -> PasswordStrength.FAIR
            entropy < 80 -> PasswordStrength.STRONG
            else -> PasswordStrength.VERY_STRONG
        }

        return entropy to strength
    }
}
