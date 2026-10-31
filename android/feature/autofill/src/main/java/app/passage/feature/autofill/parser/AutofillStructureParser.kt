package app.passage.feature.autofill.parser

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId

data class ParsedAutofillForm(
    val webDomain: String? = null,
    val packageName: String? = null,
    val usernameField: AutofillId? = null,
    val passwordField: AutofillId? = null
)

object AutofillStructureParser {

    fun parse(structure: AssistStructure): ParsedAutofillForm {
        var domain: String? = null
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        val pkg = structure.activityComponent?.packageName

        for (i in 0 until structure.windowNodeCount) {
            val rootNode = structure.getWindowNodeAt(i).rootViewNode
            traverse(rootNode) { node ->
                if (domain == null && !node.webDomain.isNullOrEmpty()) {
                    domain = node.webDomain
                }

                if (passwordId == null && isPasswordField(node)) {
                    passwordId = node.autofillId
                }

                if (usernameId == null && isUsernameField(node)) {
                    usernameId = node.autofillId
                }
            }
        }

        return ParsedAutofillForm(
            webDomain = domain,
            packageName = pkg,
            usernameField = usernameId,
            passwordField = passwordId
        )
    }

    private fun traverse(node: AssistStructure.ViewNode, action: (AssistStructure.ViewNode) -> Unit) {
        action(node)
        for (i in 0 until node.childCount) {
            traverse(node.getChildAt(i), action)
        }
    }

    private fun isPasswordField(node: AssistStructure.ViewNode): Boolean {
        // 1. Check autofill hints
        val hints = node.autofillHints ?: emptyArray()
        if (hints.any { it.equals(View.AUTOFILL_HINT_PASSWORD, ignoreCase = true) }) {
            return true
        }

        // 2. Check input type
        val inputType = node.inputType
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val isPasswordClass = (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
        if (isPasswordClass && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)) {
            return true
        }

        // 3. Check idEntry or hint text
        val idEntry = node.idEntry?.lowercase() ?: ""
        val hintText = (node.hint ?: node.text)?.toString()?.lowercase() ?: ""
        return idEntry.contains("password") || idEntry.contains("senha") ||
                hintText.contains("password") || hintText.contains("senha")
    }

    private fun isUsernameField(node: AssistStructure.ViewNode): Boolean {
        // 1. Check autofill hints
        val hints = node.autofillHints ?: emptyArray()
        if (hints.any {
                it.equals(View.AUTOFILL_HINT_USERNAME, ignoreCase = true) ||
                        it.equals(View.AUTOFILL_HINT_EMAIL_ADDRESS, ignoreCase = true)
            }) {
            return true
        }

        // 2. Check input type
        val inputType = node.inputType
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) {
            return true
        }

        // 3. Check idEntry or hint text
        val idEntry = node.idEntry?.lowercase() ?: ""
        val hintText = (node.hint ?: node.text)?.toString()?.lowercase() ?: ""
        return idEntry.contains("user") || idEntry.contains("login") || idEntry.contains("email") ||
                hintText.contains("user") || hintText.contains("login") || hintText.contains("email")
    }
}
