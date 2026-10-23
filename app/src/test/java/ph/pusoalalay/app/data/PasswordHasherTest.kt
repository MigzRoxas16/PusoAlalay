package ph.pusoalalay.app.data

import org.junit.Assert.*
import org.junit.Test

class PasswordHasherTest {
    @Test fun verifiesOnlyTheRightPassword() {
        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash("correct horse".toCharArray(), salt, 1_000)
        assertTrue(PasswordHasher.verify("correct horse".toCharArray(), salt, hash, 1_000))
        assertFalse(PasswordHasher.verify("correct hors".toCharArray(), salt, hash, 1_000))
    }
    @Test fun saltsDiffer() {
        val a = PasswordHasher.newSalt(); val b = PasswordHasher.newSalt()
        assertFalse(a.contentEquals(b))
        assertFalse(PasswordHasher.hash("pw12345678".toCharArray(), a, 1_000).contentEquals(PasswordHasher.hash("pw12345678".toCharArray(), b, 1_000)))
    }
    @Test fun base64RoundTrip() {
        val bytes = PasswordHasher.newSalt()
        assertArrayEquals(bytes, PasswordHasher.decode(PasswordHasher.encode(bytes)))
    }
}
