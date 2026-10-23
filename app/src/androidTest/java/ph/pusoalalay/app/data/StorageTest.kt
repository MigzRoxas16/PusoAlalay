package ph.pusoalalay.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ph.pusoalalay.app.domain.*
import java.security.KeyStore
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StorageTest {
    private lateinit var db: HealthDatabase
    private lateinit var cipher: RecordCipher
    private lateinit var alias: String

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java).build()
        alias = "pusoalalay.test." + UUID.randomUUID()
        cipher = RecordCipher(alias)
    }
    @After fun cleanup() {
        db.close()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
    }
    @Test fun persistsEditsAndDeletesEncryptedRecords() = runBlocking {
        val record = HealthRecord(kind = RecordKind.BLOOD_PRESSURE, measuredAt = System.currentTimeMillis(),
            value = 120.0, secondary = 80.0, note = "Private test note")
        db.records().insert(cipher.encode(record))
        val stored = db.records().all().single()
        assertFalse(stored.encryptedPayload.contains("Private test note"))
        assertEquals(record, cipher.decode(stored))
        val updated = record.copy(value = 118.0)
        assertEquals(1, db.records().update(cipher.encode(updated)))
        assertEquals(updated, cipher.decode(db.records().all().single()))
        db.records().delete(record.id)
        assertTrue(db.records().all().isEmpty())
    }
    @Test fun samePlaintextHasDifferentCiphertext() {
        val record = HealthRecord(kind = RecordKind.SYMPTOM, measuredAt = System.currentTimeMillis(), note = "Test note")
        assertNotEquals(cipher.encode(record).encryptedPayload, cipher.encode(record).encryptedPayload)
    }
    @Test fun rejectsTamperedMetadata() {
        val record = HealthRecord(kind = RecordKind.HEART_RATE, measuredAt = System.currentTimeMillis(), value = 72.0)
        val entity = cipher.encode(record)
        assertTrue(runCatching { cipher.decode(entity.copy(measuredAt = entity.measuredAt + 1)) }.isFailure)
    }
    @Test fun missingKeyFailsWithoutReplacingOrDeletingRecords() = runBlocking {
        val record = HealthRecord(kind = RecordKind.SYMPTOM, measuredAt = System.currentTimeMillis(), note = "Keep me")
        db.records().insert(cipher.encode(record))
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        assertTrue(runCatching { cipher.decode(db.records().all().single()) }.isFailure)
        assertEquals(1, db.records().all().size)
        assertFalse(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(alias))
    }
}
