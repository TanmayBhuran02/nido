package com.nido.music.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nido.music.core.database.entity.QueueItemEntity
import com.nido.music.core.database.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueDao {

    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN queue_items q ON s.id = q.songId
        ORDER BY q.position ASC
    """)
    fun getQueueSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM queue_items ORDER BY position ASC")
    suspend fun getQueueItems(): List<QueueItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(items: List<QueueItemEntity>)

    @Query("DELETE FROM queue_items")
    suspend fun clearQueue()

    /** Replace entire queue atomically. */
    @Transaction
    suspend fun replaceQueue(items: List<QueueItemEntity>) {
        clearQueue()
        insertQueueItems(items)
    }

    /** Insert a song at a specific position, shifting others down. */
    @Transaction
    suspend fun insertAtPosition(position: Int, songId: Long) {
        val existing = getQueueItems().toMutableList()
        existing.add(position, QueueItemEntity(position, songId))
        // Reindex
        val reindexed = existing.mapIndexed { index, item ->
            item.copy(position = index)
        }
        replaceQueue(reindexed)
    }

    /** Add song as next to play (position = currentIndex + 1). */
    @Transaction
    suspend fun addNext(currentPosition: Int, songId: Long) {
        insertAtPosition(currentPosition + 1, songId)
    }

    /** Append song to end of queue. */
    @Transaction
    suspend fun addToEnd(songId: Long) {
        val items = getQueueItems()
        val nextPos = (items.maxOfOrNull { it.position } ?: -1) + 1
        insertQueueItems(listOf(QueueItemEntity(nextPos, songId)))
    }

    @Query("DELETE FROM queue_items WHERE position = :position")
    suspend fun removeAtPosition(position: Int)

    @Query("SELECT COUNT(*) FROM queue_items")
    fun getQueueSize(): Flow<Int>
}
