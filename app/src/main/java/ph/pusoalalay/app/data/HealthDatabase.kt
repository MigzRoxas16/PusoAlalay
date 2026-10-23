package ph.pusoalalay.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "health_records", indices = [Index("measuredAt")])
data class RecordEntity(
    @PrimaryKey val id: String,
    val measuredAt: Long,
    val kind: String,
    val encryptedPayload: String
)

@Dao
interface RecordDao {
    @Query("SELECT * FROM health_records ORDER BY measuredAt DESC, id ASC")
    fun observe(): Flow<List<RecordEntity>>
    @Query("SELECT * FROM health_records ORDER BY measuredAt DESC, id ASC")
    suspend fun all(): List<RecordEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: RecordEntity)
    @Update
    suspend fun update(record: RecordEntity): Int
    @Query("DELETE FROM health_records WHERE id = :id")
    suspend fun delete(id: String)
    @Query("DELETE FROM health_records")
    suspend fun deleteAll()
}

@Database(entities = [RecordEntity::class], version = 1, exportSchema = true)
abstract class HealthDatabase : RoomDatabase() {
    abstract fun records(): RecordDao
    companion object {
        fun create(context: Context) = Room.databaseBuilder(context, HealthDatabase::class.java, "pusoalalay.db").build()
    }
}
