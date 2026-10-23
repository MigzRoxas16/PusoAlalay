package ph.pusoalalay.app.domain

import org.junit.Assert.*
import org.junit.Test

class AccountRulesTest {
    @Test fun acceptsValidSignUp() =
        assertNull(AccountRules.validateSignUp("Maria", " Maria.Cruz ", "secret123", "secret123"))
    @Test fun normalizesUsername() = assertEquals("maria.cruz", AccountRules.normalizeUsername(" Maria.Cruz "))
    @Test fun rejectsBadInput() {
        assertEquals(SignUpIssue.NAME, AccountRules.validateSignUp(" ", "maria", "secret123", "secret123"))
        assertEquals(SignUpIssue.USERNAME, AccountRules.validateSignUp("Maria", "ma", "secret123", "secret123"))
        assertEquals(SignUpIssue.USERNAME, AccountRules.validateSignUp("Maria", "maria cruz", "secret123", "secret123"))
        assertEquals(SignUpIssue.PASSWORD, AccountRules.validateSignUp("Maria", "maria", "short", "short"))
        assertEquals(SignUpIssue.MISMATCH, AccountRules.validateSignUp("Maria", "maria", "secret123", "secret124"))
    }
}
