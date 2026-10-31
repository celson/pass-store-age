package app.passage.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.passage.core.database.entity.SecretEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SecretDao {

    @Query("SELECT * FROM secrets_index ORDER BY relativePath ASC")
    fun getAllSecrets(): Flow<List<SecretEntity>>

    @Query("SELECT * FROM secrets_index WHERE name LIKE '%' || :query || '%' OR relativePath LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<SecretEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(secrets: List<SecretEntity>)

    @Query("DELETE FROM secrets_index WHERE relativePath = :relativePath")
    suspend fun deleteByPath(relativePath: String)

    @Query("DELETE FROM secrets_index")
    suspend fun clearAll()
}
