package ph.pusoalalay.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import ph.pusoalalay.app.domain.*
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM per record. Keys never leave Android Keystore. No plaintext health values/notes in SQLite. */
class RecordCipher(private val alias: String = "pusoalalay.records.v1") {
    @Synchronized
    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create) { "Encryption key unavailable; existing records were preserved." }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    private fun aad(id: String, measuredAt: Long, kind: String) = "$id|$measuredAt|$kind|v1".toByteArray(Charsets.UTF_8)

    private fun seal(plain: String, aad: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
    }

    private fun open(payload: String, aad: ByteArray): String {
        val bytes = Base64.decode(payload, Base64.NO_WRAP)
        require(bytes.size >= 28) { "Invalid encrypted record" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(aad)
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }

    fun encode(record: HealthRecord, ownerId: String = ""): RecordEntity {
        val json = JSONObject().put("value", record.value ?: JSONObject.NULL)
            .put("secondary", record.secondary ?: JSONObject.NULL).put("note", record.note)
            .put("source", record.source.name).put("createdAt", record.createdAt)
            .put("symptoms", JSONArray(record.symptoms.sortedBy { it.ordinal }.map { it.name }))
            .put("severity", record.severity ?: JSONObject.NULL)
        val payload = seal(json.toString(), aad(record.id, record.measuredAt, record.kind.name))
        return RecordEntity(record.id, record.measuredAt, record.kind.name, payload, ownerId)
    }

    fun decode(entity: RecordEntity): HealthRecord {
        val json = JSONObject(open(entity.encryptedPayload, aad(entity.id, entity.measuredAt, entity.kind)))
        val names = json.optJSONArray("symptoms")
        val symptoms = if (names == null) emptySet() else (0 until names.length()).mapNotNull { i ->
            Symptom.entries.firstOrNull { it.name == names.optString(i) }
        }.toSet()
        return HealthRecord(entity.id, RecordKind.valueOf(entity.kind), entity.measuredAt,
            if (json.isNull("value")) null else json.getDouble("value"),
            if (json.isNull("secondary")) null else json.getDouble("secondary"),
            json.getString("note"), MeasurementSource.valueOf(json.getString("source")), json.getLong("createdAt"),
            symptoms, if (!json.has("severity") || json.isNull("severity")) null else json.getInt("severity"))
    }

    private fun appointmentAad(id: String, ownerId: String, startsAt: Long) =
        "appointment|$id|$ownerId|$startsAt|v1".toByteArray(Charsets.UTF_8)

    fun encodeAppointment(appointment: Appointment, ownerId: String): AppointmentEntity {
        val json = JSONObject().put("title", appointment.title).put("note", appointment.note)
        return AppointmentEntity(appointment.id, ownerId, appointment.startsAt, appointment.remindAt,
            seal(json.toString(), appointmentAad(appointment.id, ownerId, appointment.startsAt)))
    }

    fun decodeAppointment(entity: AppointmentEntity): Appointment {
        val json = JSONObject(open(entity.encryptedPayload, appointmentAad(entity.id, entity.ownerId, entity.startsAt)))
        return Appointment(entity.id, entity.startsAt, entity.remindAt, json.getString("title"), json.optString("note"))
    }
}
