package fr.cyberagenda.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "events", indices = [Index("source_id"), Index("starts_at")])
data class EventEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    val title: String,
    val description: String,
    @ColumnInfo(name = "starts_at") val startsAt: Long,
    @ColumnInfo(name = "ends_at") val endsAt: Long,
    val format: String,
    val kind: String,
    val url: String,
    val organizer: String?,
    val location: String?,
    val latitude: Double?,
    val longitude: Double?,
    @ColumnInfo(name = "beginner_friendly") val beginnerFriendly: Boolean,
    val free: Boolean?,
    @ColumnInfo(defaultValue = "NULL") val price: String? = null,
)

/** Favoris conservés indépendamment du cache : ils survivent au rafraîchissement d'une source. */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

@Entity(tableName = "source_sync")
data class SourceSyncEntity(
    @PrimaryKey @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "last_success") val lastSuccess: Long?,
    @ColumnInfo(name = "last_error") val lastError: String?,
)
