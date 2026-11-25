package fr.cyberagenda.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// Toutes les requêtes sont paramétrées par Room (pas de concaténation SQL → pas d'injection).
@Dao
interface EventDao {
    @Query(
        """SELECT * FROM events
           WHERE source_id IN (:sourceIds) AND ends_at >= :now
           ORDER BY starts_at ASC"""
    )
    fun observeUpcoming(sourceIds: Set<String>, now: Long): Flow<List<EventEntity>>

    /** Événements terminés récemment (entre [from] et [now]), les plus récents d'abord. */
    @Query(
        """SELECT * FROM events
           WHERE source_id IN (:sourceIds) AND ends_at < :now AND ends_at >= :from
           ORDER BY starts_at DESC"""
    )
    fun observePast(sourceIds: Set<String>, from: Long, now: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id")
    fun observeById(id: String): Flow<EventEntity?>

    /** Toutes les occurrences à venir d'un même événement récurrent (même source, titre et lieu). */
    @Query(
        """SELECT * FROM events WHERE source_id = :sourceId AND title = :title
           AND location IS :location AND ends_at >= :now ORDER BY starts_at ASC"""
    )
    fun observeSeries(sourceId: String, title: String, location: String?, now: Long): Flow<List<EventEntity>>

    /** Événements à venir mis en favori (pour planifier les rappels). */
    @Query(
        """SELECT e.* FROM events e INNER JOIN favorites f ON f.event_id = e.id
           WHERE e.starts_at >= :now ORDER BY e.starts_at ASC"""
    )
    fun observeUpcomingFavorites(now: Long): Flow<List<EventEntity>>

    @Query("SELECT event_id FROM favorites")
    fun observeFavoriteIds(): Flow<List<String>>

    @Query("SELECT source_id, COUNT(*) AS count FROM events WHERE ends_at >= :now GROUP BY source_id")
    fun observeCounts(now: Long): Flow<List<SourceCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<EventEntity>)

    @Query("DELETE FROM events WHERE source_id = :sourceId")
    suspend fun deleteBySource(sourceId: String)

    @Query("DELETE FROM events WHERE source_id = :sourceId AND ends_at >= :now")
    suspend fun deleteUpcomingBySource(sourceId: String, now: Long)

    /** Durée de conservation : on supprime ce qui est terminé depuis trop longtemps. */
    @Query("DELETE FROM events WHERE ends_at < :cutoff")
    suspend fun purgeEndedBefore(cutoff: Long)

    /**
     * Met à jour une source de façon atomique : les événements à venir sont remplacés par ceux
     * reçus (un événement annulé disparaît) ; les événements déjà terminés sont conservés
     * (beaucoup de sources ne renvoient que le futur) jusqu'à la purge.
     */
    @Transaction
    suspend fun replaceSource(sourceId: String, events: List<EventEntity>, now: Long) {
        deleteUpcomingBySource(sourceId, now)
        insertAll(events)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE event_id = :eventId")
    suspend fun removeFavorite(eventId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE event_id = :eventId)")
    fun observeIsFavorite(eventId: String): Flow<Boolean>
}

data class SourceCount(
    @androidx.room.ColumnInfo(name = "source_id") val sourceId: String,
    val count: Int,
)

@Dao
interface SourceSyncDao {
    @Query("SELECT * FROM source_sync")
    fun observeAll(): Flow<List<SourceSyncEntity>>

    @Query("SELECT * FROM source_sync WHERE source_id = :sourceId")
    suspend fun get(sourceId: String): SourceSyncEntity?

    @Upsert
    suspend fun upsert(entity: SourceSyncEntity)
}
