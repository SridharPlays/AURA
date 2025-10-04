package com.sridharplays.aura

import android.content.ContentUris
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso

class QueueAdapter(
    private val songs: List<MusicPlayerFragment.Song>,
    private val currentSongId: Long,
    private val onSongClicked: (Int) -> Unit // Callback to handle clicks
) : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>() {

    inner class QueueViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val albumArt: ImageView = itemView.findViewById(R.id.album_art_imageview)
        private val title: TextView = itemView.findViewById(R.id.song_title_textview)
        private val artist: TextView = itemView.findViewById(R.id.artist_name_textview)
        private val duration: TextView = itemView.findViewById(R.id.song_duration_textview)

        init {
            itemView.setOnClickListener {
                onSongClicked(adapterPosition)
            }
        }

        fun bind(song: MusicPlayerFragment.Song) {
            title.text = song.title
            artist.text = song.artist
            duration.text = formatTime(song.duration)

            val albumArtUri = ContentUris.withAppendedId(
                "content://media/external/audio/albumart".toUri(),
                song.albumId
            )
            Picasso.get().load(albumArtUri)
                .placeholder(R.drawable.aura_logo)
                .error(R.drawable.aura_logo)
                .into(albumArt)

            // Highlight the currently playing song
            if (song.id == currentSongId) {
                title.setTextColor(ContextCompat.getColor(itemView.context, R.color.aura_accent_muted))
                artist.setTextColor(ContextCompat.getColor(itemView.context, R.color.aura_accent_muted))
            } else {
                title.setTextColor(ContextCompat.getColor(itemView.context, R.color.aura_cream))
                artist.setTextColor(ContextCompat.getColor(itemView.context, R.color.aura_accent_muted)) // Or your default color
            }
        }

        private fun formatTime(ms: Int): String {
            val totalSeconds = ms / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%d:%02d", minutes, seconds)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song_list_linear, parent, false)
        return QueueViewHolder(view)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        holder.bind(songs[position])
    }

    override fun getItemCount(): Int = songs.size
}