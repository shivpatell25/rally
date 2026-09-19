package com.shiv.rally.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels")
    suspend fun getAllChannels(): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE id = :id LIMIT 1")
    suspend fun getChannelById(id: String): ChannelEntity?

    @Query(
        """SELECT * FROM channels
            WHERE name LIKE '%' || :query || '%'
               OR category LIKE '%' || :query || '%'
               OR number LIKE '%' || :query || '%'
            LIMIT :limit"""
    )
    suspend fun searchChannels(query: String, limit: Int): List<ChannelEntity>

    @Query("SELECT COUNT(*) FROM channels")
    suspend fun getChannelCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(channels: List<ChannelEntity>) {
        clearAll()
        insertChannels(channels)
    }
}
