package com.sridharplays.aura

import android.content.ContentUris
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso

class AllSongsAdapter(
    private val songs: List<MusicPlayerFragment.Song>,
    private val isGridView: Boolean, // Pass the view state here
    private val onSongClicked: (MusicPlayerFragment.Song) -> Unit
) : RecyclerView.Adapter<AllSongsAdapter.SongViewHolder>() {

    companion object {
        private const val VIEW_TYPE_GRID = 0
        private const val VIEW_TYPE_LIST = 1
    }

    class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val albumArt: ImageView = itemView.findViewById(R.id.album_art_imageview)
        val title: TextView = itemView.findViewById(R.id.song_title_textview)
        val artist: TextView = itemView.findViewById(R.id.artist_name_textview)
        // Duration is only in the list view, so it can be null
        val duration: TextView? = itemView.findViewById(R.id.song_duration_textview)
    }

    override fun getItemViewType(position: Int): Int {
        return if (isGridView) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val layoutId = if (viewType == VIEW_TYPE_GRID) {
            R.layout.item_song_list // Your existing grid layout
        } else {
            R.layout.item_song_list_linear // The new list layout
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.title.text = song.title

        // Only show artist and duration in the list view
        if (!isGridView) {
            holder.artist.visibility = View.VISIBLE
            holder.artist.text = song.artist
            holder.duration?.text = formatDuration(song.duration)
        } else {
            // For grid view, hide the artist as before
            holder.artist.visibility = View.GONE
        }

        val albumArtUri = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"), song.albumId
        )
        Picasso.get().load(albumArtUri)
            .placeholder(R.drawable.aura_logo)
            .error(R.drawable.aura_logo)
            .into(holder.albumArt)

        holder.itemView.setOnClickListener {
            onSongClicked(song)
        }
    }

    override fun getItemCount() = songs.size

    // Helper function to format milliseconds to M:SS
    private fun formatDuration(ms: Int): String {
        val minutes = (ms / 1000) / 60
        val seconds = (ms / 1000) % 60
        return String.format("%d:%02d", minutes, seconds)
    }
}