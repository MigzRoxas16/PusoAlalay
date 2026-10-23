package ph.pusoalalay.app.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** ownerId is the local account that owns the record. '' marks records saved before accounts existed. */
@Entity(tableName = "health_records", indices = [Index("measuredAt"), Index("ownerId")])
data class RecordEntity(
    @PrimaryKey val id: String,
    val measuredAt: Long,
    val kind: String,
    val encryptedPayload: String,
    val ownerId: String = ""
)

/** On-device account. Only a salted PBKDF2 hash of the password is stored. */
@Entity(tableName = "accounts", indices = [Index(value = ["username"], unique = true)])
data class AccountEntity(
    @PrimaryKey val id: String,
    val username: String,
    val displayName: String,
    val passwordHash: String,
    val salt: String,
    val createdAt: Long
)

/** Title and note are encrypted; times stay plain so reminders can be restored after a reboot. */
@Entity(tableName = "appointments", indices = [Index("ownerId"), Index("remindAt")])
data class AppointmentEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val startsAt: Long,
    val remindAt: Long,
    val encryptedPayload: String
)

@Dao
interface RecordDao {
    @Query("SELECT * FROM health_records WHERE ownerId = :ownerId ORDER BY measuredAt DESC, id ASC")
    fun observe(ownerId: String): Flow<List<RecordEntity>>
    @Query("SELECT * FROM health_records ORDER BY measuredAt DESC, id ASC")
    suspend fun all(): List<RecordEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: RecordEntity)
    @Update
    suspend fun update(record: RecordEntity): Int
    @Query("DELETE FROM health_records WHERE id = :id")
    suspend fun delete(id: String)
    @Query("DELETE FROM health_records WHERE id = :id AND ownerId = :ownerId")
    suspend fun deleteOwned(id: String, ownerId: String): Int
    @Query("DELETE FROM health_records WHERE ownerId = :ownerId")
    suspend fun deleteAllOwned(ownerId: String)
    @Query("UPDATE health_records SET ownerId = :ownerId WHERE ownerId = ''")
    suspend fun claimUnowned(ownerId: String): Int
}

@Dao
interface AccountDao {
    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int
    @Query("SELECT * FROM accounts WHERE username = :username LIMIT 1")
    suspend fun byUsername(username: String): AccountEntity?
    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): AccountEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity)
    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments WHERE ownerId = :ownerId ORDER BY startsAt ASC")
    fun observe(ownerId: String): Flow<List<AppointmentEntity>>
    @Query("SELECT * FROM appointments WHERE ownerId = :ownerId")
    suspend fun allOwned(ownerId: String): List<AppointmentEntity>
    @Query("SELECT * FROM appointments WHERE remindAt > :now")
    suspend fun pendingReminders(now: Long): List<AppointmentEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(appointment: AppointmentEntity)
    @Query("DELETE FROM appointments WHERE id = :id AND ownerId = :ownerId")
    suspend fun deleteOwned(id: String, ownerId: String): Int
    @Query("DELETE FROM appointments WHERE ownerId = :ownerId")
    suspend fun deleteAllOwned(ownerId: String)
}

/** Version 1 (PusoAlalay 1.0) had only health_records. Existing records are kept and claimed by the first account. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `health_records` ADD COLUMN `ownerId` TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_health_records_ownerId` ON `health_records` (`ownerId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `accounts` (`id` TEXT NOT NULL, `username` TEXT NOT NULL, `displayName` TEXT NOT NULL, `passwordHash` TEXT NOT NULL, `salt` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_accounts_username` ON `accounts` (`username`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `appointments` (`id` TEXT NOT NULL, `ownerId` TEXT NOT NULL, `startsAt` INTEGER NOT NULL, `remindAt` INTEGER NOT NULL, `encryptedPayload` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_appointments_ownerId` ON `appointments` (`ownerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_appointments_remindAt` ON `appointments` (`remindAt`)")
    }
}

@Database(entities = [RecordEntity::class, AccountEntity::class, AppointmentEntity::class], version = 2, exportSchema = true)
abstract class HealthDatabase : RoomDatabase() {
    abstract fun records(): RecordDao
    abstract fun accounts(): AccountDao
    abstract fun appointments(): AppointmentDao
    companion object {
        fun create(context: Context) = Room.databaseBuilder(context, HealthDatabase::class.java, "pusoalalay.db")
            .addMigrations(MIGRATION_1_2).build()
    }
}
