package com.sridharplays.aura

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import android.content.ContentUris

data class SongGroup(val groupName: String, val songs: List<Song>)

class PlaylistFragment : Fragment() {

    private var musicService: MusicPlaybackService? = null
    private var isBound = false
    private lateinit var allSongs: List<Song> // Keep a reference to the full list

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MusicPlaybackService.MusicBinder
            musicService = binder.getService()
            isBound = true
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(requireContext(), MusicPlaybackService::class.java).also { intent ->
            requireActivity().bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            requireActivity().unbindService(connection)
            isBound = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_playlist, container, false)
        val outerRecyclerView: RecyclerView = view.findViewById(R.id.outer_recyclerview)
        outerRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        allSongs = getAllAudioFromDevice(requireContext())

        val songsByArtist = allSongs.groupBy { it.artist.split(',').first().trim() }
            .map { (artistName, songsInGroup) -> SongGroup(artistName, songsInGroup) }
            .sortedBy { it.groupName.lowercase() }

        outerRecyclerView.adapter = PlaylistGroupAdapter(songsByArtist) { selectedSong ->
            // 1. Find the index of the clicked song in the complete, original list
            val globalIndex = allSongs.indexOf(selectedSong)

            if (globalIndex != -1 && isBound) {
                // 2. Tell the service to load the entire playlist and start at that specific index
                musicService?.loadPlaylist(allSongs, globalIndex)

                // 3. Navigate to the player fragment
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, MusicPlayerFragment.newInstance())
                    .addToBackStack(null)
                    .commit()
            }
        }
        return view
    }

    private fun getAllAudioFromDevice(context: Context): List<Song> {
        val songList = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
                } else {
                    "${MediaStore.Audio.Media.DATA} LIKE ?"
                }
        val selectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf("AURA_Music/%")
        } else {
            arrayOf("%/AURA_Music/%")
        }

        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, selectionArgs,
            MediaStore.Audio.Media.ARTIST + " ASC"
        )

        cursor?.use { c ->
            val idColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIdColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (c.moveToNext()) {
                val id = c.getLong(idColumn)
                val title = c.getString(titleColumn)
                val artist = c.getString(artistColumn) ?: "Unknown Artist"
                val albumId = c.getLong(albumIdColumn)
                val duration = c.getInt(durationColumn)
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                songList.add(Song(id, title, artist, albumId, duration, contentUri))
            }
        }
        return songList
    }
}

class PlaylistGroupAdapter(
    private val songGroups: List<SongGroup>,
    private val onSongClicked: (Song) -> Unit
) : RecyclerView.Adapter<PlaylistGroupAdapter.GroupViewHolder>() {

    class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.group_title_textview)
        val innerRecyclerView: RecyclerView = itemView.findViewById(R.id.inner_recyclerview)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist_group, parent, false)
        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = songGroups[position]
        holder.title.text = group.groupName

        holder.innerRecyclerView.layoutManager = LinearLayoutManager(holder.itemView.context, LinearLayoutManager.HORIZONTAL, false)
        holder.innerRecyclerView.adapter = SongAdapter(group.songs) { song ->
            onSongClicked(song)
        }
    }

    override fun getItemCount() = songGroups.size
}


class SongAdapter(
    private val songs: List<Song>,
    private val onSongClicked: (Song) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val albumArt: ImageView = itemView.findViewById(R.id.album_art_imageview)
        val title: TextView = itemView.findViewById(R.id.song_title_textview)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.title.text = song.title

        val albumArtUri = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"), song.albumId
        )
        Picasso.get().load(albumArtUri).placeholder(R.drawable.aura_logo).into(holder.albumArt)

        holder.itemView.setOnClickListener {
            onSongClicked(song)
        }
    }

    override fun getItemCount() = songs.size
}