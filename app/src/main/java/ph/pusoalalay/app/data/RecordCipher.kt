package ph.pusoalalay.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
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

    fun encode(record: HealthRecord): RecordEntity {
        val json = JSONObject().put("value", record.value ?: JSONObject.NULL)
            .put("secondary", record.secondary ?: JSONObject.NULL).put("note", record.note)
            .put("source", record.source.name).put("createdAt", record.createdAt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
        cipher.updateAAD(aad(record.id, record.measuredAt, record.kind.name))
        val ciphertext = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val payload = Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
        return RecordEntity(record.id, record.measuredAt, record.kind.name, payload)
    }

    fun decode(entity: RecordEntity): HealthRecord {
        val bytes = Base64.decode(entity.encryptedPayload, Base64.NO_WRAP)
        require(bytes.size >= 28) { "Invalid encrypted record" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(aad(entity.id, entity.measuredAt, entity.kind))
        val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        return HealthRecord(entity.id, RecordKind.valueOf(entity.kind), entity.measuredAt,
            if (json.isNull("value")) null else json.getDouble("value"),
            if (json.isNull("secondary")) null else json.getDouble("secondary"),
            json.getString("note"), MeasurementSource.valueOf(json.getString("source")), json.getLong("createdAt"))
    }
}
