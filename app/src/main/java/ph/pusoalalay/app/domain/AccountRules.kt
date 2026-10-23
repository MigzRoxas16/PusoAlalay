package ph.pusoalalay.app.domain

enum class SignUpIssue { NAME, USERNAME, PASSWORD, MISMATCH }

object AccountRules {
    private val USERNAME = Regex("^[a-z0-9._]{3,30}$")

    fun normalizeUsername(username: String): String = username.trim().lowercase()

    fun validateSignUp(name: String, username: String, password: String, confirm: String): SignUpIssue? = when {
        name.isBlank() || name.trim().length > 40 -> SignUpIssue.NAME
        !USERNAME.matches(normalizeUsername(username)) -> SignUpIssue.USERNAME
        password.length !in 8..128 -> SignUpIssue.PASSWORD
        password != confirm -> SignUpIssue.MISMATCH
        else -> null
    }
}
