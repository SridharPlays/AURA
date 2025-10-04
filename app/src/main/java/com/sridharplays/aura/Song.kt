package com.sridharplays.aura

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val albumId: Long,
    val duration: Int,
    val contentUri: Uri
) : Parcelable