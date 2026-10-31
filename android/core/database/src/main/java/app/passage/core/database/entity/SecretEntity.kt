package app.passage.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "secrets_index",
    indices = [
        Index(value = ["relativePath"], unique = true),
        Index(value = ["name"]),
        Index(value = ["parentFolder"])
    ]
)
data class SecretEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val relativePath: String,
    val parentFolder: String,
    val lastModifiedMs: Long
)
