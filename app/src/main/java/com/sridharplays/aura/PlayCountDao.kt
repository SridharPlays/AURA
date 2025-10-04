package com.sridharplays.aura

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlayCountDao {

    // Inserts a new song with a play count, ignoring if it already exists.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(songPlayCount: SongPlayCount)

    // Increments the play count for a given song ID.
    @Query("UPDATE song_play_counts SET play_count = play_count + 1 WHERE songId = :songId")
    suspend fun incrementPlayCount(songId: Long)

    // Gets a song's play count data to check if it exists.
    @Query("SELECT * FROM song_play_counts WHERE songId = :songId LIMIT 1")
    suspend fun getPlayCount(songId: Long): SongPlayCount?

    @Query("SELECT * FROM song_play_counts ORDER BY play_count DESC LIMIT 5")
    suspend fun getTopSongs(): List<SongPlayCount>

}