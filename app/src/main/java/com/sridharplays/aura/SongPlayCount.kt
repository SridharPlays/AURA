package com.sridharplays.aura

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "song_play_counts")
data class SongPlayCount(
    @PrimaryKey
    val songId: Long,

    @ColumnInfo(name = "play_count")
    val playCount: Int
)